package com.pockyl.diegesis.impl.client.scene;

import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlacingPhysics;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.impl.action.StationAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Solid things for a scene's free placing ({@code placing.physics}): items fall, tip over and shove each other. Each item is
 * a rigid box with a weight (its size times how dense it is) and a turning inertia, moved by gravity and pushed out of the
 * other boxes and the sides of its surface's room (floor, the walled edges, the clearance as a ceiling) by contacts with
 * Coulomb friction and no bounce, worked out in short steps.
 *
 * <p>An item the hand drags or turns ({@link #hold}) is a body of its own weight pulled towards where the hand wants it
 * ({@code hand_force}): what it runs into pushes back. The stack in the hand ({@link #carry}) is solid only with
 * {@code hand_pushes}; otherwise it touches nothing. Things put on top of another stand as put (perched) until something
 * touches them or their support.
 *
 * <p>Only the player at the station works it out. A change it did not make itself (an item put down, taken away, turned)
 * wakes the items; when all have come to rest, the poses that changed go to the server, so everyone else sees them glide
 * there. While it runs the poses are written into the client's state only. The scene is reached through a {@link Host}, so
 * the core also runs without a client (the game tests).
 */
public final class PlacedPhysics {
    private static final float STEP = 1.0F / 120.0F;
    private static final int MAX_STEPS = 6;
    /** Seconds an item held by the hand would take to get where the hand wants it, pulled hard enough. */
    private static final float DRIVE_TIME = 0.04F;
    /** Deeper than this (pixels) under a surface, a corner has come from under it past an open edge, not just settled on it. */
    private static final float UNDER_SLAB = 0.25F;
    /** Short steps a step is worked out in: short steps keep a stack of things standing still and nothing passing through. */
    private static final int SUBSTEPS = 4;
    /** Pixels a corner may stand off a box's side and still touch it. */
    private static final float CONTACT_SLACK = 0.02F;
    /** Deeper than this (pixels) into a body at rest, a push wakes it. */
    private static final float WAKE_DEPTH = 0.02F;
    /** How much of {@code friction} (which holds a thing still) is left once it slides: sliding things grip less. */
    private static final float KINETIC = 0.8F;
    /** Coming together slower than this (pixels a second), things never bounce: whatever rests stays at rest. */
    private static final float BOUNCE_LEAST = 20.0F;
    /** How fast a thing loses its speed and its turning in the air, a second. */
    private static final float AIR = 0.1F;
    private static final float AIR_TURN = 1.5F;
    /** How hard a rocking flat item is pulled back to how it was turned (a second squared), and how its rocking dies down. */
    private static final float ROCK_PULL = 1600.0F;
    private static final float ROCK_DAMPING = 40.0F;
    /** Pixels the hand moves a held thing at most in one step, and the most steps a frame takes for that. */
    private static final float SWEEP_STEP = 0.25F;
    private static final int MAX_SWEEP_STEPS = 16;
    /** A box moving less than this (pixels a step) for {@link #SLEEP_TIME} seconds comes to rest. */
    private static final float SLEEP_MOTION = 0.004F;
    private static final float SLEEP_TIME = 0.2F;
    /** Nothing runs longer than this after a change; whatever still trembles is let rest. */
    private static final float GIVE_UP = 5.0F;
    /** Fastest an item let go of by the hand slides on (pixels a second) and spins on (radians a second). */
    private static final float TOSS_MOST = 24.0F;
    private static final float SPIN_MOST = (float) Math.toRadians(540.0);
    /**
     * Shortest side of a box for the collisions and its weight, pixels: thin enough for an icon drawn a quarter pixel thick, thick
     * enough that it never passes through another in a short step.
     */
    private static final float THINNEST = 0.2F;
    /**
     * Pixels added to the least thickness of a flat thing: two flat things put face to face never have their faces in one
     * plane, so nothing flickers where they meet, and a lying one never shares its plane with what it lies on.
     */
    private static final float FLAT_SKIN = 0.02F;
    /** Degrees off upright within which a flat thing stands as a standing one (rocking back), not as a leaning card. */
    private static final float STANDS = 10.0F;
    /** Degrees within which a lying flat thing come to rest is set back to the turn it was given. */
    private static final float LIE_BACK = 5.0F;

    /**
     * Half the least thickness of a flat thing (an icon), pixels: the one thickness the physics, the hand's preview and the free
     * spots all give it, whatever it is drawn with.
     */
    public static float flatHalf(PlacingPhysics physics) {
        return Math.max(physics.flatDepth(), THINNEST) * 0.5F + FLAT_SKIN * 0.5F;
    }

    /** Changed by less than this, a pose is not sent again. */
    private static final float MOVED = 0.005F;
    /**
     * Seconds a box just put down slides out of others whole at most: put down where there is no room, after that it pushes
     * them as any thing does, and what is crowded out over an open edge falls out.
     */
    private static final float FRESH_MOST = 0.3F;
    /** Deeper than this (pixels) into another box, a box just put down is still being moved out of it. */
    private static final float OVERLAP = 0.05F;
    /**
     * Pixels a step the top of a box just put down drifts at first: nothing stands perfectly balanced, so a thing stood on its
     * edge falls over by itself, and one standing firmly does not notice.
     */
    private static final float WOBBLE = 0.04F;
    /** Fallen this far (pixels) below the surface it was on, a thing has left it: onto a surface below, or out of the station. */
    private static final float FALLEN = 1.5F;
    /** How long a pose sent (or put down) is still expected back from the server. */
    private static final long ECHO_MILLIS = 4000L;
    /** How long a thing that fell out stays out of sight while the server takes it. */
    private static final long TAKE_MILLIS = 1500L;
    /** The contacts of the short step being worked out: reused, {@link #used} of them in use. */
    private final List<Contact> contacts = new ArrayList<>();
    private int used;
    /** Closer than this (pixels) two boxes only touch: the stack in the hand may stand right against an item. */
    private static final float CLEAR_GRAZE = 0.02F;
    /** Times the stack in the hand is moved out of the items it stands in, looking for a clear spot. */
    private static final int CLEAR_PASSES = 4;
    /** Pixels a step a thing pushed by the hand may move faster than the hand. */
    private static final float PUSHED_SLACK = 0.02F;
    /** Pixels the item under one put on top of it may move before that one stops standing as put and goes by the physics. */
    private static final float PERCH_SHIFT = 0.05F;
    /** Degrees the item under a pile may tip before what stands on it leaves it ({@code pile_rides}). */
    private static final float PILE_TIP = 8.0F;
    /** The index the stack in the hand goes by in the pushes: it is no slot's. */
    private static final int HAND = -2;
    /** At most this many impacts are told in one frame; a thing tells one at most every {@link #IMPACT_SECONDS}. */
    private static final int MAX_IMPACTS = 8;
    private static final float IMPACT_SECONDS = 0.1F;
    private int impactsThisFrame;
    /** Seconds the physics has run, for the impacts' pause. */
    private float clock;
    /** Whether a slot's thing was told to have fallen off, until the slot is empty. */
    private final boolean[] fellTold;

    /** The turn the stack in the hand has this frame. */
    private final Quaternionf handTurn = new Quaternionf();

    private final Host host;
    private final PlacingOptions options;
    private final PlacingPhysics physics;
    private final int slots;
    private final Body[] bodies;
    /** Per slot, the pose the physics last saw, wrote or sent. */
    private final ItemPose[] known;
    /**
     * Per slot, the pose the server has: taken from it, or sent to it. What the server says that equals this is no news; where
     * this client shows the item elsewhere (it settled, rode a pile, was pushed), that is newer and goes out once it rests.
     */
    private final ItemPose[] committed;
    /** Per slot, poses this client sent or put down, which the server will send back. */
    private final List<List<Echo>> echoes = new ArrayList<>();
    private final boolean[] dirty;
    /** Per slot, sent when it rests even where it started: the hand moved it without telling the server. */
    private final boolean[] forced;
    private int held = -1;
    /** The held item is turned by the hand (its turn is the hand's); otherwise dragged, keeping a turn of its own. */
    private boolean heldTurns;
    /**
     * Where the hand last put the held item: the only pose of it taken over while it is held. The server's word on it meanwhile
     * (sent with any other change of the station) is where it stood before the hand took it, and would pull it back.
     */
    private ItemPose handPose;
    /** Where the held item stood when the hand took it: the server may still send it after the hand let go, for a while. */
    private ItemPose heldStart;
    /** Per slot, a pose from before the hand moved it, ignored until the time given: the server's late word. */
    private final List<List<Echo>> stale = new ArrayList<>();
    private float accumulator;
    private float running;
    /** The stack in the hand, or null; it moves from {@link #handFrom} to where the hand has it now over this frame's steps. */
    private Body hand;
    private final Vector3f handFrom = new Vector3f();
    private final Vector3f handTo = new Vector3f();
    /** The hand pushed into something in this frame's steps. */
    private boolean handTouched;
    /** How far the stack in the hand is still inside what it pushes after the frame's steps: something it cannot move on. */
    private final Vector3f handStuck = new Vector3f();
    private final Vector3f handStuckUp = new Vector3f();
    private final Vector3f handStuckDown = new Vector3f();
    /** The item held by the hand (turned, dragged): where it was at the last step and where the hand has it now. */
    private final Vector3f heldFrom = new Vector3f();
    private final Vector3f heldTo = new Vector3f();
    private final Quaternionf heldTurnFrom = new Quaternionf();
    private final Quaternionf heldTurnTo = new Quaternionf();
    /** How far the hand moved what it holds in this step: the fastest a thing it pushes may go. */
    private float handStepLength;
    /** Per slot, the speed (x, z, pixels a second) an item put down by this client's moving hand slides on with. */
    private final Vector3f[] tossed;

    private record Echo(ItemPose pose, long until) {
    }

    /**
     * What is under a stretch of a surface: the height of the highest item there (the surface's own height with none) and that
     * item's slot index, -1 for the surface.
     */
    public record Ground(float top, int index) {
    }

    /**
     * The size of an item for the physics: half its hit box along the box's own sides and how that box is turned in the item
     * (model units), the factor from model units to pixels per pixel of size, and whether it is a flat icon.
     */
    public record Solid(Vector3fc hitHalf, Quaternionfc hitTurn, float unit, boolean flat) {
        /** Half the hit box in pixels, along its own sides, for an item of {@code size} pixels. */
        public Vector3f half(float size) {
            return new Vector3f(hitHalf).mul(size * unit);
        }
    }

    /** What the physics needs of the scene its items stand in, by slot index of the placing's group. */
    public interface Host {
        /** The item in the slot; empty for none. */
        ItemStack stack(int index);

        /** Its size for the physics; null while the slot is empty. */
        Solid solid(int index);

        /** Where it stands now as the scene shows it; null while the slot is empty. */
        ItemPose pose(int index);

        /** Shows it at {@code pose} (in the client's state only). */
        void setPose(int index, ItemPose pose);

        SlotRef slot(int index);

        /** The surface an item standing at {@code pose} belongs to, also one standing on another item; null for none. */
        PlaceSurface surfaceOf(ItemPose pose);

        /** Whether surface {@code index} is moved now (on a door swung open). */
        boolean surfaceMoved(int index);

        /** Whether this player works at the station now: only then are things moved and sent. */
        boolean focused();

        void perform(StationAction action);

        /** Out of sight for {@code millis}: it fell out and the server is taking it. */
        void hideWhileTaken(int index, long millis);

        /**
         * The item put down or let go of has touched what it lands on, coming down at {@code speed} pixels a second; {@code mass}
         * its weight (cubic pixels times density), {@code flat} whether it is a flat icon. With {@code landing_sound} only.
         */
        default void landed(int index, float speed, float mass, boolean flat) {
        }

        /**
         * The item put down or let go of has touched what it lands on ({@code under}, -1 for a surface), once per putting down,
         * also without the landing sound.
         */
        default void settled(int index, int under, float speed, float mass, boolean flat) {
        }

        /**
         * Two things knocked together ({@code other} -1: into the room's side) at {@code speed} pixels a second, at least
         * {@link #impactMinSpeed()}; {@code point} in scene pixels.
         */
        default void impact(int index, int other, float speed, float mass, org.joml.Vector3fc point) {
        }

        /** How fast things must knock together for {@link #impact}; infinite: nobody wants to know. */
        default float impactMinSpeed() {
            return Float.POSITIVE_INFINITY;
        }

        /** An item fell off its surfaces: the server takes it next. */
        default void fellOff(int index) {
        }

        /** The time now, milliseconds: how long the server's answers are still expected. */
        default long millis() {
            return System.currentTimeMillis();
        }
    }

    /** Coming down slower than this (pixels a second), a thing let go of by a drag lands without a sound. */
    private static final float LAND_QUIET = 3.0F;
    /** Seconds after it was let go within which a thing still sounds as it lands. */
    private static final float LAND_WAIT = 1.0F;

    public PlacedPhysics(Host host, PlacingOptions options, PlacingPhysics physics, int slots) {
        this.host = host;
        this.options = options;
        this.physics = physics;
        this.slots = slots;
        this.bodies = new Body[slots];
        this.fellTold = new boolean[slots];
        this.known = new ItemPose[slots];
        this.committed = new ItemPose[slots];
        this.dirty = new boolean[slots];
        this.forced = new boolean[slots];
        this.tossed = new Vector3f[slots];
        for (int i = 0; i < slots; i++) {
            echoes.add(new ArrayList<>());
            stale.add(new ArrayList<>());
        }
    }

    /**
     * The item of slot {@code index} is dragged by the hand: it goes where the hand has it, swept through the steps, and what is
     * in its way is really pushed, tipped and knocked aside; nothing pushes it. It keeps its own turn, and pressed into a wall
     * at a slant it swings round flat against it, as a box pushed into a corner does.
     */
    public void hold(int index) {
        hold(index, false);
    }

    /**
     * The item of slot {@code index} is held by the hand: dragged, or with {@code turning} turned, where the hand's turn alone
     * decides how it stands.
     */
    public void hold(int index, boolean turning) {
        if (held == index) {
            // Held already (a turn calls this with each move): where it was a step ago stays, so it keeps its way and speed.
            return;
        }
        held = index;
        heldTurns = turning;
        handPose = index >= 0 && index < known.length ? known[index] : null;
        heldStart = handPose;
        Body body = index >= 0 && index < bodies.length ? bodies[index] : null;
        if (body != null) {
            body.gripped = true;
            body.perched = false;
            body.fresh = false;
            body.wake();
            heldFrom.set(body.center);
            heldTo.set(body.center);
            heldTurnFrom.set(body.rotation);
            heldTurnTo.set(body.rotation);
        }
    }

    /** Whether the hand holds the item of slot {@code index} now. */
    public boolean holds(int index) {
        return held == index && index >= 0;
    }

    /** Whether the item of slot {@code index} stands on another as part of its pile, going along with it. */
    public boolean rides(int index) {
        return index >= 0 && index < bodies.length && bodies[index] != null && bodies[index].perched;
    }

    /** How deep (pixels) the two items that overlap most are inside each other now; 0 when nothing is inside anything. */
    public float deepestOverlap() {
        float deepest = 0.0F;
        for (int a = 0; a < bodies.length; a++) {
            for (int b = a + 1; b < bodies.length; b++) {
                if (bodies[a] != null && bodies[b] != null && bodies[a].room == bodies[b].room) {
                    deepest = Math.max(deepest, penetration(bodies[a], bodies[b]));
                }
            }
        }
        return deepest;
    }

    /** How deep two boxes are inside each other along the way they overlap least; 0 apart. */
    private static float penetration(Body a, Body b) {
        Quaternionf frameA = a.frame();
        Quaternionf frameB = b.frame();
        Vector3f[] axesA = {frameA.transform(new Vector3f(1.0F, 0.0F, 0.0F)), frameA.transform(new Vector3f(0.0F, 1.0F, 0.0F)),
                frameA.transform(new Vector3f(0.0F, 0.0F, 1.0F))};
        Vector3f[] axesB = {frameB.transform(new Vector3f(1.0F, 0.0F, 0.0F)), frameB.transform(new Vector3f(0.0F, 1.0F, 0.0F)),
                frameB.transform(new Vector3f(0.0F, 0.0F, 1.0F))};
        Vector3f between = new Vector3f(b.center).sub(a.center);
        float least = Float.MAX_VALUE;
        Vector3f axis = new Vector3f();
        for (int test = 0; test < 15; test++) {
            if (test < 3) {
                axis.set(axesA[test]);
            } else if (test < 6) {
                axis.set(axesB[test - 3]);
            } else {
                axesA[(test - 6) / 3].cross(axesB[(test - 6) % 3], axis);
                if (axis.lengthSquared() < 1.0e-6F) {
                    continue;
                }
                axis.normalize();
            }
            float reachA = a.half.x * Math.abs(axesA[0].dot(axis)) + a.half.y * Math.abs(axesA[1].dot(axis)) + a.half.z * Math.abs(axesA[2].dot(axis));
            float reachB = b.half.x * Math.abs(axesB[0].dot(axis)) + b.half.y * Math.abs(axesB[1].dot(axis)) + b.half.z * Math.abs(axesB[2].dot(axis));
            float overlap = reachA + reachB - Math.abs(between.dot(axis));
            if (overlap <= 0.0F) {
                return 0.0F;
            }
            least = Math.min(least, overlap);
        }
        return least;
    }

    /** Whether the item of slot {@code index} is still moving by the physics. */
    public boolean moving(int index) {
        return index >= 0 && index < bodies.length && bodies[index] != null && bodies[index].awake;
    }

    /** Whether a pile goes along with what it stands on ({@code pile_rides}). */
    public boolean pileRides() {
        return physics.pileRides();
    }

    /** The hand puts the held item of slot {@code index} at {@code pose} (written into the state just now). */
    public void steer(int index, ItemPose pose) {
        if (index == held && pose != null) {
            handPose = pose;
        }
    }

    public void release(int index) {
        if (held == index) {
            held = -1;
            handPose = null;
            if (heldStart != null && index >= 0 && index < stale.size()) {
                stale.get(index).add(new Echo(heldStart, host.millis() + ECHO_MILLIS));
            }
            heldStart = null;
            Body body = index >= 0 && index < bodies.length ? bodies[index] : null;
            if (body != null) {
                body.gripped = false;
                body.letGo();
                body.arm(false);
            }
            // Moved by the hand where the server has not seen it: sent when it comes to rest, wherever that is.
            if (index >= 0 && index < forced.length) {
                forced[index] = true;
                dirty[index] = true;
            }
            wakeAll();
        }
    }

    /** The stack this client just put down into slot {@code index} came from a moving hand: it slides on a little. */
    public void toss(int index, float speedX, float speedZ) {
        if (index >= 0 && index < tossed.length) {
            Vector3f speed = new Vector3f(speedX, 0.0F, speedZ).mul(physics.toss());
            if (speed.length() > TOSS_MOST) {
                speed.normalize(TOSS_MOST);
            }
            tossed[index] = speed;
        }
    }

    /**
     * The stack in the hand stands at {@code pose} on the surface {@code room} (as written, before any move of it) this frame;
     * a null {@code pose} when the hand holds nothing over a surface.
     */
    public void carry(ItemStack stack, Solid shape, ItemPose pose, PlaceSurface room) {
        if (pose == null || room == null || stack.isEmpty()) {
            hand = null;
            return;
        }
        Vector3f center = new Vector3f(pose.x(), pose.y() + pose.lift(), pose.z());
        if (hand == null || hand.stack != stack || hand.room != room || Math.abs(hand.scale - pose.scale()) > 1.0e-4F) {
            hand = new Body(-1, stack, shape, pose, room);
            hand.awake = true;
            handTurn.set(hand.rotation);
            hand.fresh = false;
            handFrom.set(center);
        }
        // Where it was at the last step stays handFrom until steps have carried it to this.
        handTo.set(center);
        handTurn.set(pose.rotation()).normalize();
    }

    /**
     * The highest item on {@code room} whose box reaches over the stretch {@code minX..maxX} by {@code minZ..maxZ}, as it stands
     * now: the stack in the hand rises onto it. The item the hand holds and what rides on it are not counted.
     */
    public Ground groundUnder(PlaceSurface room, float minX, float maxX, float minZ, float maxZ) {
        float top = room.height();
        int index = -1;
        for (int i = 0; i < bodies.length; i++) {
            Body body = bodies[i];
            if (body == null || body.room != room || i == held || held >= 0 && under(held, i)) {
                continue;
            }
            float lowX = Float.MAX_VALUE;
            float highX = -Float.MAX_VALUE;
            float lowZ = Float.MAX_VALUE;
            float highZ = -Float.MAX_VALUE;
            float high = -Float.MAX_VALUE;
            for (Vector3f point : body.points) {
                lowX = Math.min(lowX, point.x);
                highX = Math.max(highX, point.x);
                lowZ = Math.min(lowZ, point.z);
                highZ = Math.max(highZ, point.z);
                high = Math.max(high, point.y);
            }
            if (highX > minX && lowX < maxX && highZ > minZ && lowZ < maxZ && high > top) {
                top = high;
                index = i;
            }
        }
        return new Ground(top, index);
    }

    /**
     * The nearest spot to {@code at} (x, z; y unused) on {@code room} where a box of half size {@code halfX} by {@code halfZ},
     * reaching from {@code bottom} to {@code top}, stands clear of the items there: the stack in the hand keeps beside what it
     * cannot rise onto, so it lands where it is seen and moves nothing. Kept within the room's edges; where nothing near is
     * free, the spot that overlaps least of those tried.
     */
    public Vector3f clearSpot(PlaceSurface room, Vector3fc at, float halfX, float halfZ, float bottom, float top) {
        List<float[]> boxes = new ArrayList<>();
        for (int i = 0; i < bodies.length; i++) {
            Body body = bodies[i];
            if (body == null || body.room != room || i == held) {
                continue;
            }
            float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
            for (Vector3f point : body.points) {
                box[0] = Math.min(box[0], point.x);
                box[1] = Math.max(box[1], point.x);
                box[2] = Math.min(box[2], point.z);
                box[3] = Math.max(box[3], point.z);
                box[4] = Math.min(box[4], point.y);
                box[5] = Math.max(box[5], point.y);
            }
            if (box[5] > bottom + CLEAR_GRAZE && box[4] < top - CLEAR_GRAZE) {
                boxes.add(box);
            }
        }
        Vector3f spot = new Vector3f(at.x(), 0.0F, at.z());
        keepInRoom(room, spot, halfX, halfZ);
        if (boxes.isEmpty()) {
            return spot;
        }
        Vector3f best = new Vector3f(spot);
        float bestOverlap = overlap(boxes, spot, halfX, halfZ);
        for (int pass = 0; pass < CLEAR_PASSES && bestOverlap > 0.0F; pass++) {
            for (float[] box : boxes) {
                float intoX = Math.min(spot.x + halfX - box[0], box[1] - (spot.x - halfX));
                float intoZ = Math.min(spot.z + halfZ - box[2], box[3] - (spot.z - halfZ));
                if (intoX <= 0.0F || intoZ <= 0.0F) {
                    continue;
                }
                // Out along the shorter way: sideways to the side the spot is nearer to; in depth on a shelf seen from the front
                // (aim_height) to the front (-z) where it fits, where the viewer sees it: the cursor over an item points at the
                // surface behind it, and hidden there the stack would be lost from sight. Seen from above, to the nearer side.
                if (intoX <= intoZ) {
                    spot.x = spot.x < (box[0] + box[1]) / 2.0F ? box[0] - halfX : box[1] + halfX;
                } else if (room.aimHeight() > 0.0F) {
                    spot.z = box[2] - halfZ >= room.from().y() + halfZ - CLEAR_GRAZE ? box[2] - halfZ : box[3] + halfZ;
                } else {
                    spot.z = spot.z < (box[2] + box[3]) / 2.0F ? box[2] - halfZ : box[3] + halfZ;
                }
            }
            keepInRoom(room, spot, halfX, halfZ);
            float left = overlap(boxes, spot, halfX, halfZ);
            if (left < bestOverlap) {
                bestOverlap = left;
                best.set(spot);
            }
        }
        if (bestOverlap <= 0.0F) {
            flush(room, boxes, best, halfX, halfZ);
        }
        return best;
    }

    /**
     * Closes a sliver of a gap ({@code flush} pixels at most) between a spot and the nearest side of a box or wall along each
     * way, so a thing put down a hair away from its neighbour lands touching it; never into anything.
     */
    private void flush(PlaceSurface room, List<float[]> boxes, Vector3f spot, float halfX, float halfZ) {
        float most = physics.flush();
        if (most <= 0.0F) {
            return;
        }
        for (int axis = 0; axis < 2; axis++) {
            boolean alongX = axis == 0;
            float at = alongX ? spot.x : spot.z;
            float half = alongX ? halfX : halfZ;
            float across = alongX ? spot.z : spot.x;
            float acrossHalf = alongX ? halfZ : halfX;
            // The walled sides of the room, then the boxes beside it across this way.
            float low = alongX ? room.open().contains(PlaceSurface.Side.RIGHT) ? -Float.MAX_VALUE : room.from().x()
                    : room.open().contains(PlaceSurface.Side.FRONT) ? -Float.MAX_VALUE : room.from().y();
            float high = alongX ? room.open().contains(PlaceSurface.Side.LEFT) ? Float.MAX_VALUE : room.to().x()
                    : room.open().contains(PlaceSurface.Side.BACK) ? Float.MAX_VALUE : room.to().y();
            for (float[] box : boxes) {
                float boxLow = alongX ? box[0] : box[2];
                float boxHigh = alongX ? box[1] : box[3];
                float acrossLow = alongX ? box[2] : box[0];
                float acrossHigh = alongX ? box[3] : box[1];
                if (acrossHigh <= across - acrossHalf + CLEAR_GRAZE || acrossLow >= across + acrossHalf - CLEAR_GRAZE) {
                    continue;
                }
                if (boxHigh <= at - half + CLEAR_GRAZE) {
                    low = Math.max(low, boxHigh);
                } else if (boxLow >= at + half - CLEAR_GRAZE) {
                    high = Math.min(high, boxLow);
                }
            }
            float gapLow = at - half - low;
            float gapHigh = high - (at + half);
            float move = 0.0F;
            if (gapLow > 0.0F && gapLow <= most && (gapLow <= gapHigh || gapHigh <= 0.0F)) {
                move = -gapLow;
            } else if (gapHigh > 0.0F && gapHigh <= most) {
                move = gapHigh;
            }
            if (move == 0.0F) {
                continue;
            }
            if (alongX) {
                spot.x += move;
            } else {
                spot.z += move;
            }
            if (overlap(boxes, spot, halfX, halfZ) > 0.0F) {
                if (alongX) {
                    spot.x -= move;
                } else {
                    spot.z -= move;
                }
            }
        }
    }

    private static void keepInRoom(PlaceSurface room, Vector3f spot, float halfX, float halfZ) {
        float lowX = room.from().x() + halfX;
        float highX = room.to().x() - halfX;
        float lowZ = room.from().y() + halfZ;
        float highZ = room.to().y() - halfZ;
        spot.x = lowX > highX ? (room.from().x() + room.to().x()) / 2.0F : Mth.clamp(spot.x, lowX, highX);
        spot.z = lowZ > highZ ? (room.from().y() + room.to().y()) / 2.0F : Mth.clamp(spot.z, lowZ, highZ);
    }

    /** The area (square pixels, seen from above) a box at {@code spot} shares with {@code boxes}. */
    private static float overlap(List<float[]> boxes, Vector3f spot, float halfX, float halfZ) {
        float area = 0.0F;
        for (float[] box : boxes) {
            float x = Math.min(spot.x + halfX, box[1]) - Math.max(spot.x - halfX, box[0]);
            float z = Math.min(spot.z + halfZ, box[3]) - Math.max(spot.z - halfZ, box[2]);
            if (x > CLEAR_GRAZE && z > CLEAR_GRAZE) {
                area += x * z;
            }
        }
        return area;
    }

    /**
     * How far (x, z, pixels) the stack in the hand would have to move to be out of what it presses into and cannot move on
     * (an item against a wall): it is drawn that far off, beside it, not inside it.
     */
    public Vector3f handStuck() {
        return handStuck;
    }

    /** Expects {@code pose} back from the server for the slot: it is what this client asked for. */
    public void expect(int index, ItemPose pose) {
        if (index >= 0 && index < echoes.size() && pose != null) {
            echoes.get(index).add(new Echo(pose, host.millis() + ECHO_MILLIS));
        }
    }

    /**
     * Follows the state's items and, for the player at the station, moves them. Called each frame after the placed items took
     * the state over; re-syncs those it moved.
     */
    public void update(float dt) {
        impactsThisFrame = 0;
        clock += dt;
        boolean local = host.focused();
        boolean changed = sync(local);
        if (!local) {
            // Left while things still moved: where they are now is where they stay.
            hand = null;
            accumulator = 0.0F;
            for (Body body : bodies) {
                if (body != null && body.awake) {
                    body.sleep();
                }
            }
            sendRested();
            return;
        }
        if (changed) {
            running = 0.0F;
        }
        if (!anyAwake() && held < 0 && hand == null) {
            accumulator = 0.0F;
            sendRested();
            return;
        }
        running += dt;
        accumulator = Math.min(accumulator + dt, STEP * MAX_STEPS);
        int steps = (int) (accumulator / STEP);
        // The hand moves things this frame by up to so far: in steps short enough that nothing passes through a thin thing.
        int sweep = 0;
        if (steps > 0 && hand != null) {
            float way = hand != null ? handFrom.distance(handTo) : 0.0F;
            sweep = Math.min(MAX_SWEEP_STEPS, (int) Math.ceil(way / SWEEP_STEP)) - steps;
        }
        handTouched = false;
        if (steps > 0) {
            handStuckUp.zero();
            handStuckDown.zero();
        }
        int total = steps + Math.max(0, sweep);
        for (int done = 1; done <= total; done++) {
            if (done <= steps) {
                accumulator -= STEP;
            }
            handStepLength = 0.0F;
            if (hand != null) {
                // The hand sweeps through the frame's steps rather than jumping: a fast one still meets what is in its way.
                Vector3f to = new Vector3f(handFrom).lerp(handTo, (float) done / total);
                handStepLength = to.distance(hand.center);
                hand.aim(to, handTurn);
            }
            Body grabbed = held >= 0 ? bodies[held] : null;
            if (grabbed != null) {
                // An item held by the hand is pulled towards where the hand wants it, turned as the hand turns it (dragged, it
                // keeps a turn of its own); how fast it gets there is up to its weight and what is in its way.
                float k = (float) done / total;
                Vector3f at = new Vector3f(heldFrom).lerp(heldTo, k);
                Quaternionf turned = heldTurns ? new Quaternionf(heldTurnFrom).slerp(heldTurnTo, k).normalize()
                        : new Quaternionf(grabbed.rotation);
                grabbed.aim(at, turned);
            }
            step();
            for (Body body : bodies) {
                if (body != null && body.handPushed) {
                    // Pushed by the hand, it goes no faster than the hand: squeezed against a wall it is not flung away.
                    body.handPushed = false;
                    body.limitSpeed(handStepLength + PUSHED_SLACK);
                }
            }
        }
        if (steps > 0 && held >= 0 && bodies[held] != null) {
            // Held, it is drawn where the walls and what it pushes let it be (dragged, as the walls turned it); the hand goes on
            // from there.
            ItemPose pose = bodies[held].pose(-1);
            known[held] = pose;
            host.setPose(held, pose);
        }
        if (steps > 0) {
            handStuck.set(handStuckUp).add(handStuckDown);
            handStuck.y = 0.0F;
            handFrom.set(handTo);
            heldFrom.set(heldTo);
            heldTurnFrom.set(heldTurnTo);
        }
        if (handTouched || held >= 0) {
            // Something is being pushed or held by the hand: it goes on for as long as the hand does.
            running = 0.0F;
        }
        sendHeld(dt);
        if (running > GIVE_UP) {
            for (Body body : bodies) {
                if (body != null) {
                    body.sleep();
                }
            }
        }
        for (int i = 0; i < bodies.length; i++) {
            Body body = bodies[i];
            if (body != null && i != held && body.center.y < body.room.height() - FALLEN) {
                fellOff(i, body);
                continue;
            }
            if (body != null && body.moved && i != held) {
                body.moved = false;
                ItemPose pose = body.pose(supportOf(body));
                known[i] = pose;
                dirty[i] = true;
                host.setPose(i, pose);
            }
        }
        // Each thing goes to the server as soon as it rests, not when all have: one that keeps trembling holds no other back.
        sendRested();
    }

    /** Seconds between the poses of a thing the hand holds sent while it is held: everyone else sees it move in steps. */
    private static final float INTERIM = 0.25F;
    private float heldFor;

    /**
     * While the hand holds a thing longer than {@link #INTERIM}, where it is now (and what rides on it) goes to the server every
     * {@link #INTERIM}, so other players see a long drag or turn as it goes, not as one jump at the end. The answers are
     * expected: they never pull it back.
     */
    private void sendHeld(float dt) {
        if (held < 0 || bodies[held] == null) {
            heldFor = 0.0F;
            return;
        }
        float before = heldFor;
        heldFor += dt;
        if ((int) (heldFor / INTERIM) == (int) (before / INTERIM)) {
            return;
        }
        for (int i = 0; i < bodies.length; i++) {
            if (bodies[i] != null && known[i] != null && (i == held || under(held, i) && dirty[i])) {
                ItemPose pose = known[i];
                expect(i, pose);
                host.perform(new StationAction.Pose(host.slot(i), pose));
            }
        }
    }

    /** Takes over what changed in the state; returns whether anything did that this physics did not do itself. */
    private boolean sync(boolean local) {
        boolean changed = false;
        long now = host.millis();
        for (int i = 0; i < slots; i++) {
            ItemPose pose = host.pose(i);
            List<Echo> expected = echoes.get(i);
            expected.removeIf(echo -> echo.until() < now);
            List<Echo> old = stale.get(i);
            old.removeIf(echo -> echo.until() < now);
            if (pose == null) {
                if (bodies[i] != null) {
                    bodies[i] = null;
                    changed = true;
                }
                fellTold[i] = false;
                known[i] = null;
                committed[i] = null;
                dirty[i] = false;
                expected.clear();
                continue;
            }
            Body body = bodies[i];
            boolean same = known[i] != null && close(known[i], pose);
            ItemStack stack = host.stack(i);
            if (same && body != null && body.stack == stack) {
                continue;
            }
            if (i == held && body != null) {
                if (handPose != null && !close(handPose, pose) && known[i] != null) {
                    // Not where the hand put it: the server's older word. The hand's grip wins.
                    host.setPose(i, known[i]);
                    continue;
                }
                // Where the hand has it now: the steps take it there, pushing what is in the way.
                heldTo.set(pose.x(), pose.y() + pose.lift(), pose.z());
                heldTurnTo.set(pose.rotation()).normalize();
                if (heldTurns) {
                    body.restAt(heldTurnTo);
                }
                // It stays in the room of its surface while held: the hand slides it along that surface.
                known[i] = pose;
                body.wake();
                continue;
            }
            if (!same && known[i] != null && body != null && i != held && (expected.stream().anyMatch(echo -> close(echo.pose(), pose))
                    || old.stream().anyMatch(echo -> close(echo.pose(), pose)))) {
                // The server's answer to something sent earlier, now behind what the items did since: keep showing those.
                host.setPose(i, known[i]);
                continue;
            }
            if (!same && known[i] != null && body != null && i != held && committed[i] != null && close(committed[i], pose)) {
                // The server says again what it already has (with any other change of the station, or all of it after an action
                // it turned down): the item has moved on here since, settling or riding a pile. What is shown stays, and goes
                // to the server once it rests. Taken over, things jumped back to where they were put whenever an item was taken.
                host.setPose(i, known[i]);
                dirty[i] = true;
                continue;
            }
            PlaceSurface room = roomOf(pose);
            Solid solid = host.solid(i);
            if (room == null || solid == null) {
                bodies[i] = null;
                known[i] = pose;
                continue;
            }
            boolean arrived = body == null;
            // Come straight down by the height of what was taken from under it (the server's word, or this client's own take):
            // it is set where it now stands, not knocked over on the way.
            boolean lowered = body != null && known[i] != null && Math.abs(known[i].x() - pose.x()) < 0.002F
                    && Math.abs(known[i].z() - pose.z()) < 0.002F && pose.y() < known[i].y() - 0.01F
                    && Math.abs(new Quaternionf(known[i].rotation()).dot(new Quaternionf(pose.rotation()))) > 0.99995F;
            bodies[i] = new Body(i, stack, solid, pose, room);
            if (lowered) {
                bodies[i].spin.zero();
            }
            known[i] = pose;
            // From the state: the server's word, or what this client asked of it (put down, turned), which the server takes.
            committed[i] = pose;
            if (arrived) {
                // Put down by this client a moment ago: the server will say the same pose back.
                expect(i, pose);
                if (tossed[i] != null) {
                    // From the hand: it sounds as it touches down, however gently.
                    bodies[i].push(tossed[i]);
                    bodies[i].arm(true);
                    tossed[i] = null;
                }
            }
            changed = true;
        }
        for (Body body : bodies) {
            if (body != null && body.perched && (body.restingOn < 0 || body.restingOn >= bodies.length || bodies[body.restingOn] == null)) {
                // What it stood on is gone: it comes down.
                body.perched = false;
                body.wake();
                changed = true;
            }
        }
        if (changed && local) {
            wakeAll();
        }
        return changed;
    }

    /**
     * A box fell off its surface: it goes on falling in the room of a surface below it, or, with none there, it leaves the
     * station into the player's inventory, and what has no room there falls on the ground in front of the station.
     */
    private void fellOff(int index, Body body) {
        PlaceSurface below = null;
        for (PlaceSurface surface : options.surfaces()) {
            if (surface != body.room && surface.height() < body.room.height() && !host.surfaceMoved(options.surfaces().indexOf(surface))
                    && body.center.x >= surface.from().x() && body.center.x <= surface.to().x() && body.center.z >= surface.from().y()
                    && body.center.z <= surface.to().y() && (below == null || surface.height() > below.height())) {
                below = surface;
            }
        }
        if (below != null) {
            body.room = below;
            return;
        }
        ItemPose start = body.start;
        bodies[index] = null;
        dirty[index] = false;
        forced[index] = false;
        if (start != null) {
            // Where the server has it, out of sight until the take is answered. Shown meanwhile, it glided back up onto the shelf
            // before it went.
            host.setPose(index, start);
            known[index] = start;
        }
        host.hideWhileTaken(index, TAKE_MILLIS);
        if (!fellTold[index]) {
            // Told once: until the server takes it, it may come back where it stood and fall again.
            fellTold[index] = true;
            host.fellOff(index);
        }
        host.perform(new StationAction.Click(new StationAction.Target.Station(host.slot(index)), 0, StationAction.ClickKind.SPILL));
        wakeAll();
    }

    /**
     * The surface a pose stands in the room of; also one a little past an open edge of its surface (pulled over it by the hand,
     * or tipping off), whose middle has left the surface.
     */
    private PlaceSurface roomOf(ItemPose pose) {
        PlaceSurface surface = host.surfaceOf(pose);
        if (surface != null) {
            return surface;
        }
        for (PlaceSurface candidate : options.surfaces()) {
            if (candidate.open().isEmpty() || pose.y() < candidate.height() - 0.01F || pose.y() > candidate.height() + candidate.clearance()) {
                continue;
            }
            float reach = pose.lift() * 2.0F + 2.0F;
            if (pose.x() >= candidate.from().x() - reach && pose.x() <= candidate.to().x() + reach
                    && pose.z() >= candidate.from().y() - reach && pose.z() <= candidate.to().y() + reach) {
                return candidate;
            }
        }
        return null;
    }

    /** Wakes every item but those put on top of another: they stay as put until something touches them or their support. */
    private void wakeAll() {
        for (Body body : bodies) {
            if (body != null && !body.perched) {
                body.wake();
            }
        }
    }

    private boolean anyAwake() {
        for (int i = 0; i < bodies.length; i++) {
            if (bodies[i] != null && bodies[i].awake && i != held) {
                return true;
            }
        }
        return false;
    }

    /** Whether everything the item of slot {@code index} stands on, down its pile, is at rest. */
    private boolean supportsAtRest(int index) {
        int at = bodies[index].restingOn;
        for (int depth = 0; depth < bodies.length && at >= 0 && at < bodies.length && bodies[at] != null; depth++) {
            if (bodies[at].awake || at == held) {
                return false;
            }
            at = bodies[at].restingOn;
        }
        return true;
    }

    private void sendRested() {
        for (int i = 0; i < dirty.length; i++) {
            if (!dirty[i]) {
                continue;
            }
            dirty[i] = false;
            Body body = bodies[i];
            if (body == null || i == held) {
                continue;
            }
            if (held >= 0 && under(held, i)) {
                // Riding what the hand still holds: sent with it once it is let go of and comes to rest.
                dirty[i] = true;
                continue;
            }
            if (body.awake || !supportsAtRest(i)) {
                // Still moving, or on something that still moves: sent once it rests, each thing as soon as it does.
                dirty[i] = true;
                continue;
            }
            ItemPose pose = body.restingPose(supportOf(body));
            boolean force = forced[i];
            forced[i] = false;
            if (!force && committed[i] != null && close(committed[i], pose, MOVED) && committed[i].on() == pose.on()) {
                // Settled where the server has it: shown just so (a rocking icon rests at its own turn, not a hair off it).
                known[i] = committed[i];
                host.setPose(i, committed[i]);
                continue;
            }
            known[i] = pose;
            committed[i] = pose;
            expect(i, pose);
            host.setPose(i, pose);
            host.perform(new StationAction.Pose(host.slot(i), pose));
            body.start = pose;
        }
    }

    // -------------------------------------------------------------------------------------------------------------------
    // The step
    // -------------------------------------------------------------------------------------------------------------------

    /**
     * One step, in {@link #SUBSTEPS} short ones: each moves the bodies by their speed and gravity, pushes them out of each other
     * and the room's sides (the push weighed by mass and by how hard each turns about the point pushed), takes their speed from
     * how far they really went, then stops them along each contact (nothing bounces off) and lets friction slow their sliding.
     */
    private void step() {
        for (Body body : bodies) {
            if (body == null || !body.perched) {
                continue;
            }
            if (physics.pileRides()) {
                // It rides what it stands on (see ride); only with that gone does it come down.
                if (body.restingOn < 0 || body.restingOn >= bodies.length || bodies[body.restingOn] == null) {
                    body.perched = false;
                    body.wake();
                }
                continue;
            }
            Body support = body.restingOn >= 0 && body.restingOn < bodies.length ? bodies[body.restingOn] : null;
            if (support != null && !body.supportSeen) {
                body.supportSeen = true;
                body.supportAt.set(support.center);
            }
            if (support == null || body.restingOn == held || support.center.distance(body.supportAt) > PERCH_SHIFT) {
                // What it stands on is moved: from now on it rides or falls as a solid thing does.
                body.perched = false;
                body.wake();
            }
        }
        for (int i = 0; i < bodies.length; i++) {
            Body body = bodies[i];
            if (body != null && body.awake) {
                body.onFloor = false;
                body.restingOn = -1;
            }
        }
        float h = STEP / SUBSTEPS;
        for (int sub = 1; sub <= SUBSTEPS; sub++) {
            float along = (float) sub / SUBSTEPS;
            boolean last = sub == SUBSTEPS;
            if (hand != null) {
                hand.driveTo(along, h);
            }
            if (held >= 0 && bodies[held] != null) {
                bodies[held].pull(h, heldTurns);
            }
            for (int i = 0; i < bodies.length; i++) {
                Body body = bodies[i];
                if (body != null && body.awake) {
                    body.integrate(h);
                }
            }
            used = 0;
            for (int i = 0; i < bodies.length; i++) {
                Body body = bodies[i];
                if (body != null && body.awake) {
                    roomContacts(body, i);
                }
            }
            for (int a = 0; a < bodies.length; a++) {
                for (int b = a + 1; b < bodies.length; b++) {
                    pairContacts(a, b);
                }
                if (hand != null) {
                    handContacts(a);
                }
            }
            for (int c = 0; c < used; c++) {
                solvePosition(contacts.get(c));
            }
            for (int c = 0; c < used; c++) {
                holdStill(contacts.get(c));
            }
            for (int i = 0; i < bodies.length; i++) {
                Body body = bodies[i];
                if (body != null && body.awake) {
                    body.takeSpeed(h);
                }
            }
            for (int c = 0; c < used; c++) {
                solveSpeed(contacts.get(c), h);
            }
            for (int i = 0; i < bodies.length; i++) {
                Body body = bodies[i];
                if (body != null && body.awake) {
                    body.limit();
                }
            }
            if (last && hand != null) {
                stuckHand();
            }
        }
        for (int i = 0; i < bodies.length; i++) {
            Body body = bodies[i];
            if (body != null && body.awake) {
                body.settle(STEP);
            }
        }
        if (physics.pileRides()) {
            ride();
        }
    }

    /**
     * A pile is one thing: each thing perched on another takes the change of where that one is and of its turn about the
     * vertical (about that one's middle), from the bottom of each pile up, so it stands on it as it stood. It leaves the pile
     * and goes by the physics when what it stands on tips over, or when riding would take it through a wall or the ceiling.
     */
    private void ride() {
        int[] depth = new int[bodies.length];
        int deepest = 0;
        for (int i = 0; i < bodies.length; i++) {
            Body body = bodies[i];
            if (body != null) {
                body.rode = false;
                depth[i] = pileDepth(i);
                deepest = Math.max(deepest, depth[i]);
            }
        }
        for (int level = 1; level <= deepest; level++) {
            for (int i = 0; i < bodies.length; i++) {
                Body body = bodies[i];
                if (body == null || !body.perched || depth[i] != level) {
                    continue;
                }
                Body support = body.restingOn >= 0 && body.restingOn < bodies.length ? bodies[body.restingOn] : null;
                if (support == null) {
                    continue;
                }
                if (!body.supportSeen) {
                    body.supportSeen = true;
                    body.supportAt.set(support.center);
                    body.supportTurn.set(support.rotation);
                    continue;
                }
                Quaternionf change = new Quaternionf(support.rotation).mul(new Quaternionf(body.supportTurn).conjugate()).normalize();
                Vector3f up = change.transform(new Vector3f(0.0F, 1.0F, 0.0F));
                if (up.y < Math.cos(Math.toRadians(PILE_TIP))) {
                    // Tipped over: what stood on it falls as it may.
                    body.perched = false;
                    body.wake();
                    continue;
                }
                Quaternionf twist = new Quaternionf(0.0F, change.y(), 0.0F, change.w());
                if (twist.lengthSquared() < 1.0e-8F) {
                    twist.identity();
                }
                twist.normalize();
                boolean turned = Math.abs(twist.y()) > 1.0e-6F;
                if (!turned && support.center.distanceSquared(body.supportAt) < 1.0e-10F) {
                    continue;
                }
                Vector3f offset = twist.transform(new Vector3f(body.center).sub(body.supportAt));
                body.supportAt.set(support.center);
                body.supportTurn.set(support.rotation);
                if (turned) {
                    body.rotation.set(new Quaternionf(twist).mul(body.rotation)).normalize();
                    body.rest.set(new Quaternionf(twist).mul(body.rest)).normalize();
                }
                body.place(offset.add(support.center));
                body.moved = true;
                body.rode = true;
                // Carried, it is a thing like any other for what it meets, not one just put down sliding out of it.
                body.fresh = false;
                if (outOfRoom(body)) {
                    // Carried into a wall or the ceiling: it is knocked off the pile there.
                    body.perched = false;
                    body.wake();
                }
            }
        }
    }

    /** How many things down the pile the item of slot {@code index} stands: 0 on its surface (or not perched). */
    private int pileDepth(int index) {
        int depth = 0;
        int at = index;
        while (depth < bodies.length && at >= 0 && at < bodies.length && bodies[at] != null && bodies[at].perched) {
            at = bodies[at].restingOn;
            depth++;
        }
        return depth;
    }

    /** Whether the item of slot {@code below} is under the perched item of slot {@code index}, anywhere down its pile. */
    private boolean under(int below, int index) {
        int at = index;
        for (int depth = 0; depth < bodies.length && at >= 0 && at < bodies.length && bodies[at] != null && bodies[at].perched; depth++) {
            at = bodies[at].restingOn;
            if (at == below) {
                return true;
            }
        }
        return false;
    }

    /** Whether a corner of the body is past a walled side of its room or over its ceiling. */
    private static boolean outOfRoom(Body body) {
        PlaceSurface room = body.room;
        float lowX = Math.min(room.from().x(), room.to().x());
        float highX = Math.max(room.from().x(), room.to().x());
        float lowZ = Math.min(room.from().y(), room.to().y());
        float highZ = Math.max(room.from().y(), room.to().y());
        float ceiling = room.height() + room.clearance();
        for (Vector3f point : body.points) {
            if (point.x < lowX - CONTACT_SLACK && !room.open().contains(PlaceSurface.Side.RIGHT)
                    || point.x > highX + CONTACT_SLACK && !room.open().contains(PlaceSurface.Side.LEFT)
                    || point.z < lowZ - CONTACT_SLACK && !room.open().contains(PlaceSurface.Side.FRONT)
                    || point.z > highZ + CONTACT_SLACK && !room.open().contains(PlaceSurface.Side.BACK)
                    || point.y > ceiling + CONTACT_SLACK) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moved by the hand alone: the stack in it, with {@code hand_pushes}. An item the hand drags or turns is not: it is a thing
     * with its weight that the hand pulls, and what it runs into pushes back.
     */
    private boolean fixed(int index) {
        return index == HAND;
    }

    private Body bodyAt(int index) {
        return index == HAND ? hand : index >= 0 && index < bodies.length ? bodies[index] : null;
    }

    private Contact nextContact() {
        if (used == contacts.size()) {
            contacts.add(new Contact());
        }
        return contacts.get(used++);
    }

    /** The sides of a body's room its corners are past: the floor, the walls of its closed sides and the ceiling. */
    private void roomContacts(Body body, int index) {
        PlaceSurface room = body.room;
        boolean mayFall = body.leaving || body.gripped || physics.fallOff() == PlacingPhysics.FallOff.ANY;
        float lowX = Math.min(room.from().x(), room.to().x());
        float highX = Math.max(room.from().x(), room.to().x());
        float lowZ = Math.min(room.from().y(), room.to().y());
        float highZ = Math.max(room.from().y(), room.to().y());
        float floor = room.height();
        float ceiling = room.height() + room.clearance();
        for (Vector3f point : body.points) {
            boolean outX = mayFall && (point.x < lowX && room.open().contains(PlaceSurface.Side.RIGHT)
                    || point.x > highX && room.open().contains(PlaceSurface.Side.LEFT));
            boolean outZ = mayFall && (point.z < lowZ && room.open().contains(PlaceSurface.Side.FRONT)
                    || point.z > highZ && room.open().contains(PlaceSurface.Side.BACK));
            boolean over = !outX && !outZ;
            // The surface is a slab: a corner that hung under it past an open edge and comes back in below its top goes back out
            // through the edge's face when that is the shorter way, not up through the slab (that flung things away).
            float edge = !mayFall ? Float.MAX_VALUE : nearestOpenEdge(room, point, lowX, highX, lowZ, highZ);
            if (over && floor - point.y > UNDER_SLAB && edge < floor - point.y && body.beyondOpenEdge()) {
                outThroughEdge(body, index, point, room, lowX, highX, lowZ, highZ, edge);
            } else if (over && point.y < floor) {
                wall(body, index, point, 0.0F, 1.0F, 0.0F, point.x, floor, point.z);
                body.onFloor = true;
            } else if (over && point.y > ceiling && body.center.y < ceiling) {
                // A box come down from a surface above is let in through the ceiling.
                wall(body, index, point, 0.0F, -1.0F, 0.0F, point.x, ceiling, point.z);
                // The shelf above holds a thing down but never by friction: one wedged leaning between it and what it
                // stands on slides and rocks level, instead of the two grips twisting it to and fro.
                contacts.get(used - 1).slippery = true;
            }
            if (!outX && point.x < lowX) {
                wall(body, index, point, 1.0F, 0.0F, 0.0F, lowX, point.y, point.z);
            } else if (!outX && point.x > highX) {
                wall(body, index, point, -1.0F, 0.0F, 0.0F, highX, point.y, point.z);
            }
            if (!outZ && point.z < lowZ) {
                wall(body, index, point, 0.0F, 0.0F, 1.0F, point.x, point.y, lowZ);
            } else if (!outZ && point.z > highZ) {
                wall(body, index, point, 0.0F, 0.0F, -1.0F, point.x, point.y, highZ);
            }
        }
        if (mayFall) {
            for (PlaceSurface.Side side : room.open()) {
                edgeContacts(body, index, side, lowX, highX, lowZ, highZ, floor);
            }
        }
    }

    /** Pixels in from the ends of where an open edge runs under a body that its holding points are taken. */
    private static final float EDGE_IN = 0.01F;

    /**
     * The open edge of a surface holds up what is still over it: where the edge runs under the body's bottom (its two ends
     * there), the slab's edge pushes the body up as the floor does. A box pushed part way over sits flat, overhanging, and only
     * past its balance point does it tip over the edge, instead of resting on its inner corners leaning out.
     */
    private void edgeContacts(Body body, int index, PlaceSurface.Side side, float lowX, float highX, float lowZ, float highZ, float floor) {
        boolean alongX = side == PlaceSurface.Side.FRONT || side == PlaceSurface.Side.BACK;
        float line = switch (side) {
            case FRONT -> lowZ;
            case BACK -> highZ;
            case RIGHT -> lowX;
            case LEFT -> highX;
        };
        // Where the body's outline seen from above crosses the edge's line: along its edges, the ends of its silhouette.
        float from = Float.MAX_VALUE;
        float to = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            for (int bit = 1; bit < 8; bit <<= 1) {
                if ((corner & bit) != 0) {
                    continue;
                }
                Vector3f a = body.points[corner];
                Vector3f b = body.points[corner | bit];
                float sa = (alongX ? a.z : a.x) - line;
                float sb = (alongX ? b.z : b.x) - line;
                if (sa * sb > 0.0F || sa == sb) {
                    continue;
                }
                float k = sa / (sa - sb);
                float at = alongX ? a.x + (b.x - a.x) * k : a.z + (b.z - a.z) * k;
                from = Math.min(from, at);
                to = Math.max(to, at);
            }
        }
        from = Math.max(from, alongX ? lowX : lowZ) + EDGE_IN;
        to = Math.min(to, alongX ? highX : highZ) - EDGE_IN;
        if (from > to) {
            return;
        }
        Quaternionf back = body.frame().conjugate();
        for (float at : new float[] {from, to}) {
            float x = alongX ? at : line;
            float z = alongX ? line : at;
            // Straight up from under the slab into the body: its bottom over this point of the edge.
            Vector3f origin = back.transform(new Vector3f(x, floor - EDGE_REACH, z).sub(body.center));
            Vector3f up = back.transform(new Vector3f(0.0F, 1.0F, 0.0F));
            float[] span = Placing.through(body.half, origin, up);
            if (span == null) {
                continue;
            }
            float bottom = floor - EDGE_REACH + span[0];
            if (bottom < floor && bottom > floor - EDGE_REACH + 1.0e-3F) {
                wall(body, index, new Vector3f(x, bottom, z), 0.0F, 1.0F, 0.0F, x, floor, z);
                body.onFloor = true;
            }
        }
    }

    /** Pixels under the floor the edge looks for a body's bottom over it: deeper, the body has gone over the edge. */
    private static final float EDGE_REACH = 2.0F;

    /** How far {@code point} is inside the nearest open edge of {@code room}, pixels; no open edge: the most there is. */
    private static float nearestOpenEdge(PlaceSurface room, Vector3f point, float lowX, float highX, float lowZ, float highZ) {
        float nearest = Float.MAX_VALUE;
        if (room.open().contains(PlaceSurface.Side.FRONT)) {
            nearest = Math.min(nearest, point.z - lowZ);
        }
        if (room.open().contains(PlaceSurface.Side.BACK)) {
            nearest = Math.min(nearest, highZ - point.z);
        }
        if (room.open().contains(PlaceSurface.Side.RIGHT)) {
            nearest = Math.min(nearest, point.x - lowX);
        }
        if (room.open().contains(PlaceSurface.Side.LEFT)) {
            nearest = Math.min(nearest, highX - point.x);
        }
        return nearest;
    }

    /** Pushes a corner under the surface out through its nearest open edge, {@code edge} pixels away. */
    private void outThroughEdge(Body body, int index, Vector3f point, PlaceSurface room, float lowX, float highX, float lowZ, float highZ,
            float edge) {
        if (room.open().contains(PlaceSurface.Side.FRONT) && point.z - lowZ == edge) {
            wall(body, index, point, 0.0F, 0.0F, -1.0F, point.x, point.y, lowZ);
        } else if (room.open().contains(PlaceSurface.Side.BACK) && highZ - point.z == edge) {
            wall(body, index, point, 0.0F, 0.0F, 1.0F, point.x, point.y, highZ);
        } else if (room.open().contains(PlaceSurface.Side.RIGHT) && point.x - lowX == edge) {
            wall(body, index, point, -1.0F, 0.0F, 0.0F, lowX, point.y, point.z);
        } else {
            wall(body, index, point, 1.0F, 0.0F, 0.0F, highX, point.y, point.z);
        }
    }

    private void wall(Body body, int index, Vector3f point, float nx, float ny, float nz, float x, float y, float z) {
        Contact contact = nextContact();
        contact.set(body, index, null, -1, nx, ny, nz);
        body.anchor(point, contact.anchorA);
        contact.anchorB.set(x, y, z);
        contact.normalSpeedBefore = body.speedAt(point, new Vector3f()).dot(contact.normal);
    }

    private void pairContacts(int a, int b) {
        Body first = bodies[a];
        Body second = bodies[b];
        // Two things at rest do not touch anew: with hundreds on the shelves, most pairs end at the first test.
        if (first == null || second == null || !first.awake && !second.awake && !first.rode && !second.rode || first.room != second.room
                || !first.overlaps(second)) {
            return;
        }
        if (physics.pileRides() && (under(a, b) || under(b, a))) {
            // A pile does not push itself apart: what stands on a thing rides it.
            return;
        }
        boxContacts(first, a, second, b);
    }

    private void handContacts(int index) {
        Body body = bodies[index];
        if (body == null || index == held || body.room != hand.room || !body.overlaps(hand)) {
            return;
        }
        boxContacts(body, index, hand, HAND);
    }

    /**
     * Where two boxes touch: the axis they overlap least along (the faces of each and the crossings of their edges), the
     * corners of each inside the other along it, or, edge across edge, one point between them.
     */
    private void boxContacts(Body a, int ai, Body b, int bi) {
        Quaternionf frameA = a.frame();
        Quaternionf frameB = b.frame();
        Vector3f[] axesA = {frameA.transform(new Vector3f(1.0F, 0.0F, 0.0F)), frameA.transform(new Vector3f(0.0F, 1.0F, 0.0F)),
                frameA.transform(new Vector3f(0.0F, 0.0F, 1.0F))};
        Vector3f[] axesB = {frameB.transform(new Vector3f(1.0F, 0.0F, 0.0F)), frameB.transform(new Vector3f(0.0F, 1.0F, 0.0F)),
                frameB.transform(new Vector3f(0.0F, 0.0F, 1.0F))};
        Vector3f between = new Vector3f(b.center).sub(a.center);
        Vector3f best = new Vector3f();
        float bestScore = Float.MAX_VALUE;
        float bestOverlap = 0.0F;
        Vector3f axis = new Vector3f();
        for (int test = 0; test < 15; test++) {
            boolean edges = test >= 6;
            if (test < 3) {
                axis.set(axesA[test]);
            } else if (test < 6) {
                axis.set(axesB[test - 3]);
            } else {
                axesA[(test - 6) / 3].cross(axesB[(test - 6) % 3], axis);
                if (axis.lengthSquared() < 1.0e-6F) {
                    continue;
                }
                axis.normalize();
            }
            float reachA = a.half.x * Math.abs(axesA[0].dot(axis)) + a.half.y * Math.abs(axesA[1].dot(axis)) + a.half.z * Math.abs(axesA[2].dot(axis));
            float reachB = b.half.x * Math.abs(axesB[0].dot(axis)) + b.half.y * Math.abs(axesB[1].dot(axis)) + b.half.z * Math.abs(axesB[2].dot(axis));
            float overlap = reachA + reachB - Math.abs(between.dot(axis));
            if (overlap <= 0.0F) {
                return;
            }
            // Faces are preferred: a box resting on another touches it face to face, not by an edge a hair less deep.
            float score = edges ? overlap * 1.05F + 0.01F : overlap;
            if (score < bestScore) {
                bestScore = score;
                bestOverlap = overlap;
                best.set(axis);
            }
        }
        // From b to a: a is pushed out along it.
        Vector3f normal = new Vector3f(best);
        if (normal.dot(between) > 0.0F) {
            normal.negate();
        }
        float supportB = b.center.dot(normal) + b.half.x * Math.abs(axesB[0].dot(normal)) + b.half.y * Math.abs(axesB[1].dot(normal))
                + b.half.z * Math.abs(axesB[2].dot(normal));
        float supportA = a.center.dot(normal) - a.half.x * Math.abs(axesA[0].dot(normal)) - a.half.y * Math.abs(axesA[1].dot(normal))
                - a.half.z * Math.abs(axesA[2].dot(normal));
        int before = used;
        Vector3f other = new Vector3f();
        for (Vector3f point : a.points) {
            float depth = supportB - point.dot(normal);
            if (depth > 0.0F && b.holds(point, CONTACT_SLACK)) {
                Contact contact = nextContact();
                contact.set(a, ai, b, bi, normal.x, normal.y, normal.z);
                a.anchor(point, contact.anchorA);
                b.anchor(other.set(normal).mul(depth).add(point), contact.anchorB);
                contact.normalSpeedBefore = relativeSpeed(contact, point, other);
            }
        }
        for (Vector3f point : b.points) {
            float depth = point.dot(normal) - supportA;
            if (depth > 0.0F && a.holds(point, CONTACT_SLACK)) {
                Contact contact = nextContact();
                contact.set(a, ai, b, bi, normal.x, normal.y, normal.z);
                a.anchor(other.set(normal).mul(-depth).add(point), contact.anchorA);
                b.anchor(point, contact.anchorB);
                contact.normalSpeedBefore = relativeSpeed(contact, other, point);
            }
        }
        if (used == before) {
            // Edge across edge: one point, halfway between the two boxes' deepest points.
            Vector3f deepA = a.support(new Vector3f(normal).negate());
            Vector3f deepB = b.support(normal);
            Vector3f middle = new Vector3f(deepA).add(deepB).mul(0.5F);
            Contact contact = nextContact();
            contact.set(a, ai, b, bi, normal.x, normal.y, normal.z);
            Vector3f onA = new Vector3f(normal).mul(-bestOverlap * 0.5F).add(middle);
            Vector3f onB = new Vector3f(normal).mul(bestOverlap * 0.5F).add(middle);
            a.anchor(onA, contact.anchorA);
            b.anchor(onB, contact.anchorB);
            contact.normalSpeedBefore = relativeSpeed(contact, onA, onB);
        }
    }

    private static float relativeSpeed(Contact contact, Vector3f onA, Vector3f onB) {
        Vector3f speed = contact.a.speedAt(onA, new Vector3f());
        if (contact.b != null) {
            speed.sub(contact.b.speedAt(onB, new Vector3f()));
        }
        return speed.dot(contact.normal);
    }

    /**
     * A body that was put down or let go of touches what it lands on ({@code under}, of index {@code underIndex}; null for the
     * floor): it sounds, once. Put down from the hand level onto another thing, it stands there as put, part of that pile.
     */
    private void landing(Body body, int index, float speed, Body under, int underIndex) {
        if (!body.armed || index < 0) {
            return;
        }
        body.armed = false;
        if (body.fromHand || speed >= LAND_QUIET) {
            if (physics.landingSound()) {
                host.landed(index, Math.max(0.0F, speed), 1.0F / body.inverseMass, body.flat);
            }
            host.settled(index, under != null && underIndex >= 0 ? underIndex : -1, Math.max(0.0F, speed), 1.0F / body.inverseMass, body.flat);
        }
        if (body.fromHand && physics.pileRides() && under != null && underIndex >= 0 && underIndex != held) {
            if (body.faceUp() && under.faceUp() && clearOfAllBut(body, under)) {
                // Level with what it lands on: it stays as it touched down, without a stack of loose boxes trembling.
                body.sleep();
                body.perched = true;
                body.restingOn = underIndex;
                body.onFloor = false;
                body.supportSeen = false;
            }
        }
    }

    /**
     * Tells of a knock: two things (or one and the room's side) came together fast. Not a landing, which is told as such, nor the
     * hand's touch; once per thing in {@link #IMPACT_SECONDS}, and a few a frame at most.
     */
    private void knock(Contact contact, Vector3f onA, Vector3f onB) {
        Body a = contact.a;
        Body b = contact.b;
        if (impactsThisFrame >= MAX_IMPACTS || contact.ai < 0 || b != null && contact.bi < 0
                || contact.normal.y > 0.5F && a.armed || contact.normal.y < -0.5F && b != null && b.armed) {
            return;
        }
        if (clock - a.lastImpact < IMPACT_SECONDS || b != null && clock - b.lastImpact < IMPACT_SECONDS) {
            return;
        }
        a.lastImpact = clock;
        if (b != null) {
            b.lastImpact = clock;
        }
        impactsThisFrame++;
        Vector3f point = new Vector3f(onA).add(onB).mul(0.5F);
        host.impact(contact.ai, b == null ? -1 : contact.bi, -contact.normalSpeedBefore, 1.0F / a.inverseMass, point);
    }

    /** Whether {@code body} is inside no other body but {@code but}: only then may it stop where it is. */
    private boolean clearOfAllBut(Body body, Body but) {
        for (Body other : bodies) {
            if (other != null && other != body && other != but && other.room == body.room && body.overlaps(other)
                    && penetration(body, other) > CONTACT_SLACK) {
                return false;
            }
        }
        return true;
    }

    /** Degrees within which a thing put down on another lands level with it and joins its pile at once. */
    private static final float LAND_LEVEL = 3.0F;

    /** Of the normal's up share, how much makes a contact one of standing on something. */
    private static final float STANDING = 0.7F;

    /**
     * Whether, in this contact, side {@code a} (else {@code b}) is what the item the hand holds stands on: a thing dragged across
     * the top of another slides on it and pushes nothing below.
     */
    private boolean underHeld(Contact contact, boolean a) {
        if (held < 0) {
            return false;
        }
        // The normal points from b to a: a stands on b where it points up.
        return a ? contact.bi == held && contact.normal.y < -STANDING : contact.ai == held && contact.normal.y > STANDING;
    }

    /** A body that takes a push: awake, not moved by the hand alone, and not held still by the other being just put down. */
    private boolean gives(Body body, int index, Body other) {
        return body != null && !fixed(index) && body.awake && !(other != null && other.fresh && !body.fresh);
    }

    /**
     * Pushes the two bodies of a contact apart by how deep it is now, each by its share: the lighter and the one that turns more
     * easily about the point give more.
     */
    private void solvePosition(Contact contact) {
        Body a = contact.a;
        Body b = contact.b;
        // A standing icon is held up by the floor, not tipped by it: only things meeting it rock it.
        a.straight = b == null && a.upright;
        if (b != null) {
            b.straight = false;
        }
        Vector3f onA = a.world(contact.anchorA, new Vector3f());
        Vector3f onB = b == null ? new Vector3f(contact.anchorB) : b.world(contact.anchorB, new Vector3f());
        float depth = new Vector3f(onB).sub(onA).dot(contact.normal);
        contact.lambda = 0.0F;
        if (depth <= 0.0F || b != null && physics.pileRides() && contact.ai >= 0 && contact.bi >= 0
                && (under(contact.ai, contact.bi) || under(contact.bi, contact.ai))) {
            // A thing joined the pile of the other in this short step (it landed on it): from now on it rides, not pushes.
            return;
        }
        if (depth > WAKE_DEPTH) {
            // A body at rest stays put for a graze; pushed in deeper, it wakes and takes its share.
            // Pushed into, a thing at rest wakes, also one riding a pile: the pile is hit, that piece leaves it and gives way.
            // Kept still instead, two piles carried into each other went through each other.
            if (!a.awake && !fixed(contact.ai)) {
                a.wake();
            }
            if (b != null && !b.awake && !fixed(contact.bi)) {
                b.wake();
            }
            if (contact.ai == HAND || contact.bi == HAND) {
                handTouched = true;
            }
        }
        if (-contact.normalSpeedBefore >= host.impactMinSpeed()) {
            knock(contact, onA, onB);
        }
        if (contact.normal.y > 0.5F) {
            landing(a, contact.ai, -contact.normalSpeedBefore, b, contact.bi);
        } else if (contact.normal.y < -0.5F && b != null) {
            landing(b, contact.bi, -contact.normalSpeedBefore, a, contact.ai);
        }
        if (b != null && depth > OVERLAP) {
            a.overlapping = true;
            b.overlapping = true;
        }
        boolean givesA = gives(a, contact.ai, b) && !underHeld(contact, true);
        boolean givesB = gives(b, contact.bi, a) && !underHeld(contact, false);
        if (!givesA && !givesB) {
            return;
        }
        if (fixed(contact.bi) && givesA) {
            a.handPushed = true;
        } else if (fixed(contact.ai) && givesB) {
            b.handPushed = true;
        }
        Vector3f normal = contact.normal;
        float weightA = givesA ? a.weightAt(onA, normal) : 0.0F;
        float weightB = givesB ? b.weightAt(onB, normal) : 0.0F;
        float lambda = depth / (weightA + weightB);
        // Put down (or turned, or set out) inside another, a thing slides out of it without taking speed from that: a row of
        // things put down a hair inside each other spreads by that hair, it is not flung apart.
        // Deeper than a step's move brings two things together, the overlap was there before (set there, carried there, turned
        // there): it is undone the same way, without the speed a collision would give.
        boolean quiet = b != null && (a.fresh || b.fresh || depth > OVERLAP);
        if (givesA) {
            a.nudge(onA, new Vector3f(normal).mul(lambda), quiet);
        }
        if (givesB) {
            b.nudge(onB, new Vector3f(normal).mul(-lambda), quiet);
        }
        contact.lambda = lambda;
        if (normal.y > 0.5F && contact.bi >= 0) {
            a.restingOn = contact.bi;
        } else if (normal.y < -0.5F && b != null && contact.ai >= 0) {
            b.restingOn = contact.ai;
        }
    }

    /**
     * Static friction, once every contact of the short step has pushed its bodies apart: how far the two points slid across each
     * other in it is taken back if friction holds it, so a thing at rest stays exactly where and as it stands.
     */
    private void holdStill(Contact contact) {
        if (contact.lambda <= 0.0F || contact.slippery) {
            return;
        }
        Body a = contact.a;
        Body b = contact.b;
        a.straight = b == null && a.upright;
        if (b != null) {
            b.straight = false;
        }
        boolean givesA = gives(a, contact.ai, b) && !underHeld(contact, true);
        boolean givesB = gives(b, contact.bi, a) && !underHeld(contact, false);
        if (!givesA && !givesB) {
            return;
        }
        Vector3f normal = contact.normal;
        float lambda = contact.lambda;
        Vector3f onA = new Vector3f();
        Vector3f onB = new Vector3f(contact.anchorB);
        a.world(contact.anchorA, onA);
        Vector3f slid = new Vector3f(onA).sub(a.previousWorld(contact.anchorA, new Vector3f()));
        if (b != null) {
            b.world(contact.anchorB, onB);
            slid.sub(new Vector3f(onB).sub(b.previousWorld(contact.anchorB, new Vector3f())));
        }
        slid.fma(-slid.dot(normal), normal);
        float length = slid.length();
        if (length < 1.0e-7F) {
            return;
        }
        Vector3f across = new Vector3f(slid).div(length);
        float tangentA = givesA ? a.weightAt(onA, across) : 0.0F;
        float tangentB = givesB ? b.weightAt(onB, across) : 0.0F;
        float hold = length / (tangentA + tangentB);
        if (hold < physics.friction() * lambda) {
            if (givesA) {
                a.nudge(onA, new Vector3f(across).mul(-hold));
            }
            if (givesB) {
                b.nudge(onB, new Vector3f(across).mul(hold));
            }
        }
    }

    /**
     * After the speeds were taken from the moves: along each contact the two stop moving into or away from each other (no
     * bounce, unless the scene's {@code bounce} gives some back), and their sliding across it is slowed by friction, at most by
     * what the push between them allows (kinetic friction).
     */
    private void solveSpeed(Contact contact, float h) {
        if (contact.lambda <= 0.0F) {
            return;
        }
        Body a = contact.a;
        Body b = contact.b;
        a.straight = b == null && a.upright;
        if (b != null) {
            b.straight = false;
        }
        boolean givesA = gives(a, contact.ai, b) && !underHeld(contact, true);
        boolean givesB = gives(b, contact.bi, a) && !underHeld(contact, false);
        if (!givesA && !givesB) {
            return;
        }
        Vector3f normal = contact.normal;
        Vector3f onA = a.world(contact.anchorA, new Vector3f());
        Vector3f onB = b == null ? new Vector3f(contact.anchorB) : b.world(contact.anchorB, new Vector3f());
        Vector3f speed = a.speedAt(onA, new Vector3f());
        if (b != null) {
            speed.sub(b.speedAt(onB, new Vector3f()));
        }
        float along = speed.dot(normal);
        Vector3f across = new Vector3f(speed).fma(-along, normal);
        float sliding = across.length();
        if (sliding > 1.0e-6F && !contact.slippery) {
            across.div(sliding);
            float weight = (givesA ? a.weightAt(onA, across) : 0.0F) + (givesB ? b.weightAt(onB, across) : 0.0F);
            float most = physics.friction() * KINETIC * contact.lambda / h;
            float impulse = Math.min(sliding / weight, most);
            if (givesA) {
                a.strike(onA, new Vector3f(across).mul(-impulse));
            }
            if (givesB) {
                b.strike(onB, new Vector3f(across).mul(impulse));
            }
        }
        speed = a.speedAt(onA, new Vector3f());
        if (b != null) {
            speed.sub(b.speedAt(onB, new Vector3f()));
        }
        along = speed.dot(normal);
        float wanted = contact.normalSpeedBefore < -BOUNCE_LEAST ? -physics.bounce() * contact.normalSpeedBefore : 0.0F;
        float change = wanted - along;
        if (Math.abs(change) > 1.0e-6F) {
            float weight = (givesA ? a.weightAt(onA, normal) : 0.0F) + (givesB ? b.weightAt(onB, normal) : 0.0F);
            float impulse = change / weight;
            if (givesA) {
                a.strike(onA, new Vector3f(normal).mul(impulse));
            }
            if (givesB) {
                b.strike(onB, new Vector3f(normal).mul(-impulse));
            }
        }
    }

    /** How far the stack in the hand is still inside what it pushes after the step: what it presses on does not give way. */
    private void stuckHand() {
        for (int c = 0; c < used; c++) {
            Contact contact = contacts.get(c);
            if (contact.ai != HAND && contact.bi != HAND) {
                continue;
            }
            Vector3f onA = contact.a.world(contact.anchorA, new Vector3f());
            Vector3f onB = contact.b.world(contact.anchorB, new Vector3f());
            float depth = new Vector3f(onB).sub(onA).dot(contact.normal);
            if (depth <= 0.0F) {
                continue;
            }
            Vector3f out = new Vector3f(contact.normal).mul(contact.ai == HAND ? depth : -depth);
            handStuckUp.max(out);
            handStuckDown.min(out);
        }
    }

    /** The item a box rests on, or -1 for the surface. */
    private int supportOf(Body body) {
        return body.onFloor ? -1 : body.restingOn;
    }

    private static boolean close(ItemPose a, ItemPose b) {
        return close(a, b, 0.002F) && a.on() == b.on();
    }

    private static boolean close(ItemPose a, ItemPose b, float pixels) {
        return Math.abs(a.x() - b.x()) < pixels && Math.abs(a.y() + a.lift() - b.y() - b.lift()) < pixels && Math.abs(a.z() - b.z()) < pixels
                && Math.abs(a.scale() - b.scale()) < 1.0e-4F && Math.abs(new Quaternionf(a.rotation()).dot(new Quaternionf(b.rotation()))) > 0.99995F;
    }

    /**
     * How dense a thing is, as water is 1: a block by what it is made of (its sound tells: metal, stone, wood, wool), anything
     * else as food is. Its weight is this times its size: a stone block shoves an apple aside, an apple hardly moves the block.
     */
    static float density(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) {
            return 1.0F;
        }
        SoundType sound = item.getBlock().defaultBlockState().getSoundType();
        if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.COPPER
                || sound == SoundType.CHAIN || sound == SoundType.LANTERN || sound == SoundType.LODESTONE) {
            return 4.0F;
        }
        if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.DEEPSLATE_BRICKS || sound == SoundType.POLISHED_DEEPSLATE
                || sound == SoundType.TUFF || sound == SoundType.BASALT || sound == SoundType.NETHER_BRICKS || sound == SoundType.GLASS
                || sound == SoundType.AMETHYST || sound == SoundType.CALCITE || sound == SoundType.DRIPSTONE_BLOCK
                || sound == SoundType.NETHERRACK || sound == SoundType.GILDED_BLACKSTONE) {
            return 2.5F;
        }
        if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD || sound == SoundType.CHERRY_WOOD || sound == SoundType.NETHER_WOOD
                || sound == SoundType.SCAFFOLDING || sound == SoundType.BAMBOO) {
            return 0.7F;
        }
        if (sound == SoundType.WOOL || sound == SoundType.MOSS || sound == SoundType.MOSS_CARPET || sound == SoundType.SNOW
                || sound == SoundType.POWDER_SNOW || sound == SoundType.AZALEA_LEAVES || sound == SoundType.GRASS) {
            return 0.3F;
        }
        return 1.5F;
    }

    // -------------------------------------------------------------------------------------------------------------------
    // A contact
    // -------------------------------------------------------------------------------------------------------------------

    /** Two points that touch, one on each body (or on the room's side, {@code b} null), and the way {@code a} is pushed out. */
    private static final class Contact {
        Body a;
        int ai;
        Body b;
        int bi;
        /** The point on {@code a} in its own turn about its middle; on {@code b} the same, or the room's point. */
        final Vector3f anchorA = new Vector3f();
        final Vector3f anchorB = new Vector3f();
        final Vector3f normal = new Vector3f();
        /** How fast the points came towards each other along the normal before this short step (negative: coming closer). */
        float normalSpeedBefore;
        /** How hard they were pushed apart in this short step: what friction may hold against. */
        float lambda;

        /** No friction along it (the ceiling). */
        boolean slippery;

        void set(Body a, int ai, Body b, int bi, float nx, float ny, float nz) {
            slippery = false;
            this.a = a;
            this.ai = ai;
            this.b = b;
            this.bi = bi;
            normal.set(nx, ny, nz);
            lambda = 0.0F;
        }
    }

    // -------------------------------------------------------------------------------------------------------------------
    // A box
    // -------------------------------------------------------------------------------------------------------------------

    /**
     * A solid box: where its middle is and how it is turned, how fast it moves and turns, its weight (its size times how dense
     * it is) and how hard it is to turn about each of its sides (as a solid box of that weight is).
     */
    private final class Body {
        final ItemStack stack;
        PlaceSurface room;
        final Vector3f half;
        /** How the hit box is turned in the item: its sides are those of {@link #frame()}. */
        final Quaternionf hitTurn;
        final float inverseMass;
        /** One over how hard it is to turn about each side of the hit box; zero for an item that never turns. */
        final Vector3f inverseInertia = new Vector3f();
        /**
         * A flat icon standing as it was turned: it rocks when bumped and comes back to {@link #rest}. Lying, it is a plain rigid
         * plate that does not rock.
         */
        boolean upright;
        /** A flat icon: it stands up or lies down by its rest turn. */
        final boolean flat;
        /** One over how hard it is to turn about each side as a solid box, whether or not it rocks. */
        private final Vector3f boxInertia = new Vector3f();
        /** The turn a rocking item comes back to: as the player turned it. */
        final Quaternionf rest = new Quaternionf();
        /** Let go of by the hand over an open edge: it may fall off there ({@code fall_off: held}). */
        boolean leaving;
        /** In the hand's grip: an open edge is no wall for it, the hand may pull it over. */
        boolean gripped;
        /**
         * Put on top of another item: it stands as put, out of the physics, until something touches it or the item under it
         * moves (from {@link #supportAt}, where that one stood) or goes. A stack of things stays as stacked.
         */
        boolean perched;
        final Vector3f supportAt = new Vector3f();
        /** How the item under it was turned when last seen, for a pile that rides it. */
        final Quaternionf supportTurn = new Quaternionf();
        /** Carried along on a pile in the last step: it meets what it comes to though it is at rest. */
        boolean rode;
        /** For the contact being worked out: a standing icon on the floor, which only holds it up and never tips it. */
        boolean straight;
        /** Put down or let go of: it sounds when it touches what it lands on; put down from the hand, however gently. */
        boolean armed;
        boolean fromHand;
        /** When it last told of a knock, seconds of the physics' clock. */
        float lastImpact = -1.0F;
        private float armedFor;
        /** Pushed by the hand in this step. */
        boolean handPushed;
        boolean supportSeen;
        /** The corners where they are now, kept with every move. */
        final Vector3f[] points = new Vector3f[8];
        /** The corners in the item, about its middle. */
        final Vector3f[] corners = new Vector3f[8];
        final Vector3f center = new Vector3f();
        final Quaternionf rotation = new Quaternionf();
        final Vector3f previousCenter = new Vector3f();
        final Quaternionf previousRotation = new Quaternionf();
        /** Pixels a second, and radians a second about an axis in the room. */
        final Vector3f velocity = new Vector3f();
        final Vector3f spin = new Vector3f();
        /** Moved by the hand: where it was at the start of the step and where the hand has it at its end. */
        final Vector3f driveFrom = new Vector3f();
        final Vector3f driveTo = new Vector3f();
        final Quaternionf driveTurnFrom = new Quaternionf();
        final Quaternionf driveTurnTo = new Quaternionf();
        final float scale;
        /** The size the player gave it, kept through its moves (see {@link ItemPose#wanted}). */
        final float wanted;
        ItemPose start;
        boolean awake;
        boolean moved;
        /** Just put down (or turned) and maybe inside another box: moved out of it whole until it is out. */
        boolean fresh = true;
        boolean overlapping;
        boolean onFloor;
        int restingOn = -1;
        private float still;
        /** How long it has been just put down. */
        private float freshFor;

        Body(int index, ItemStack stack, Solid shape, ItemPose pose, PlaceSurface room) {
            this.stack = stack;
            this.room = room;
            this.start = pose;
            this.scale = pose.scale();
            this.wanted = pose.wanted();
            // The hit box as the model has it: for an icon drawn on the slant, the narrow box round its pixels, turned.
            this.half = shape.half(pose.scale() * options.itemSize());
            this.hitTurn = new Quaternionf(shape.hitTurn());
            float longest = Math.max(half.x, Math.max(half.y, half.z));
            this.flat = shape.flat();
            // A flat thing is as thick as everywhere else it is reckoned with (flatHalf); anything else as drawn, but never
            // thinner than a thin thing can be and not pass through another in a short step.
            half.max(new Vector3f(flat ? flatHalf(physics) : THINNEST * 0.5F));
            float mass = Math.max(0.05F, density(stack) * 8.0F * half.x * half.y * half.z);
            this.inverseMass = 1.0F / mass;
            // A solid box: m (b^2 + c^2) / 12 about each side, with b and c the other two sides' lengths.
            float x2 = 4.0F * half.x * half.x;
            float y2 = 4.0F * half.y * half.y;
            float z2 = 4.0F * half.z * half.z;
            boxInertia.set(12.0F / (mass * (y2 + z2)), 12.0F / (mass * (x2 + z2)), 12.0F / (mass * (x2 + y2)));
            center.set(pose.x(), pose.y() + pose.lift(), pose.z());
            rotation.set(pose.rotation()).normalize();
            restAt(rotation);
            for (int corner = 0; corner < 8; corner++) {
                corners[corner] = hitTurn.transform(new Vector3f((corner & 1) != 0 ? half.x : -half.x, (corner & 2) != 0 ? half.y : -half.y,
                        (corner & 4) != 0 ? half.z : -half.z));
                points[corner] = new Vector3f();
            }
            previousCenter.set(center);
            previousRotation.set(rotation);
            driveFrom.set(center);
            driveTo.set(center);
            driveTurnFrom.set(rotation);
            driveTurnTo.set(rotation);
            refresh();
            restingOn = pose.on();
            onFloor = pose.on() < 0;
            perched = index >= 0 && pose.on() >= 0;
            // The stack in the hand (index -1) does not wobble: it is held.
            Vector3f thin = upright || index < 0 ? null : thinSide();
            if (thin != null) {
                // A thin thing stood on its edge: it starts to lean a hair across its thin side, one way or the other by its slot,
                // and falls over as a card would.
                thin.y = 0.0F;
                thin.normalize(index % 2 == 0 ? 1.0F : -1.0F);
                spin.set(0.0F, 1.0F, 0.0F).cross(thin).mul(WOBBLE / STEP / Math.max(0.01F, half.length()));
            }
            if (longest <= 0.0F) {
                awake = false;
            }
        }

        /**
         * The turn it comes back to and stands at rest with. A flat icon given a standing turn rocks about it (with
         * {@code upright_flat}); given a lying one it is a plate like any other.
         */
        void restAt(Quaternionfc turn) {
            rest.set(turn).normalize();
            // Stood up straight it stays standing as a card would not; at a lean it is a card: it falls flat, or leans on
            // what is beside it, as it may.
            upright = physics.uprightFlat() && flat && Math.abs(thinSide(rest).y) < Math.sin(Math.toRadians(STANDS));
            if (upright && physics.rock() <= 0.0F) {
                inverseInertia.zero();
            } else {
                inverseInertia.set(boxInertia);
            }
        }

        /** Whether, turned {@code turn}, its thin side faces up or down: a flat thing lying. */
        boolean lies(Quaternionfc turn) {
            return Math.abs(thinSide(turn).y) > Math.sqrt(0.5);
        }

        /** The way its thin side faces in the room, turned {@code turn}. */
        private Vector3f thinSide(Quaternionfc turn) {
            float thinnest = Math.min(half.x, Math.min(half.y, half.z));
            Vector3f thin = half.x == thinnest ? new Vector3f(1.0F, 0.0F, 0.0F) : half.y == thinnest ? new Vector3f(0.0F, 1.0F, 0.0F)
                    : new Vector3f(0.0F, 0.0F, 1.0F);
            return new Quaternionf(turn).mul(hitTurn).transform(thin);
        }

        /** The hit box's own sides in the room: the item's turn and the box's turn in it. */
        Quaternionf frame() {
            return new Quaternionf(rotation).mul(hitTurn);
        }

        /** Whether it lies on one of its faces, level within {@link #LAND_LEVEL}: what stands on it stands level. */
        boolean faceUp() {
            Quaternionf frame = frame();
            float level = (float) Math.cos(Math.toRadians(LAND_LEVEL));
            return Math.abs(frame.transform(new Vector3f(1.0F, 0.0F, 0.0F)).y) > level
                    || Math.abs(frame.transform(new Vector3f(0.0F, 1.0F, 0.0F)).y) > level
                    || Math.abs(frame.transform(new Vector3f(0.0F, 0.0F, 1.0F)).y) > level;
        }

        /** It sounds when it next touches down (within {@link #LAND_WAIT}). */
        void arm(boolean hand) {
            armed = true;
            fromHand = hand;
            armedFor = 0.0F;
        }

        /** Sets it moving by the physics; perched on a pile, it leaves the pile. */
        void wake() {
            awake = true;
            perched = false;
            still = 0.0F;
        }

        /** For a thin box (a card, an icon) standing on an edge, the way its thin side faces; null otherwise. */
        private Vector3f thinSide() {
            float thinnest = Math.min(half.x, Math.min(half.y, half.z));
            float longest = Math.max(half.x, Math.max(half.y, half.z));
            if (thinnest > longest * 0.25F) {
                return null;
            }
            Vector3f thin = half.x == thinnest ? new Vector3f(1.0F, 0.0F, 0.0F) : half.y == thinnest ? new Vector3f(0.0F, 1.0F, 0.0F)
                    : new Vector3f(0.0F, 0.0F, 1.0F);
            frame().transform(thin);
            return Math.abs(thin.y) < 0.5F ? thin : null;
        }

        /** The corners again, from where its middle is and how it is turned. */
        void refresh() {
            for (int corner = 0; corner < 8; corner++) {
                rotation.transform(points[corner].set(corners[corner])).add(center);
            }
        }

        /** A point of the room as a point of this body, in its own turn about its middle. */
        void anchor(Vector3f point, Vector3f into) {
            new Quaternionf(rotation).conjugate().transform(into.set(point).sub(center));
        }

        /** A point of this body (as {@link #anchor} gave it) where it is now. */
        Vector3f world(Vector3f anchor, Vector3f into) {
            return rotation.transform(into.set(anchor)).add(center);
        }

        /** The same point where it was at the start of the short step. */
        Vector3f previousWorld(Vector3f anchor, Vector3f into) {
            return previousRotation.transform(into.set(anchor)).add(previousCenter);
        }

        /** How fast the point of the body at {@code point} (in the room) moves. */
        Vector3f speedAt(Vector3f point, Vector3f into) {
            Vector3f arm = new Vector3f(point).sub(center);
            return spin.cross(arm, into).add(velocity);
        }

        /** Whether {@code point} is inside the box, give or take {@code slack} pixels. */
        boolean holds(Vector3f point, float slack) {
            Vector3f local = frame().conjugate().transform(new Vector3f(point).sub(center));
            return Math.abs(local.x) <= half.x + slack && Math.abs(local.y) <= half.y + slack && Math.abs(local.z) <= half.z + slack;
        }

        /** Its corner farthest along {@code way}. */
        Vector3f support(Vector3f way) {
            Vector3f best = points[0];
            for (Vector3f point : points) {
                if (point.dot(way) > best.dot(way)) {
                    best = point;
                }
            }
            return new Vector3f(best);
        }

        /**
         * Whether pushes leave its turn alone: just put down, it slides out of another whole and is not knocked over by it; a
         * flat item in the hand's grip stands as the hand holds it, it does not rock there (pressed into something, a rocked
         * icon's slanting side lifted it up that thing).
         */
        private boolean keepsTurn() {
            return fresh || gripped && upright || straight;
        }

        /** The turn {@code torque} gives it: through how hard it is to turn about each of its sides. */
        private Vector3f turned(Vector3f torque) {
            Quaternionf frame = frame();
            Vector3f local = new Quaternionf(frame).conjugate().transform(new Vector3f(torque));
            local.mul(inverseInertia);
            return frame.transform(local);
        }

        /** How much the point {@code at} gives when pushed along {@code way}: by the body's weight, and by its turning about it. */
        float weightAt(Vector3f at, Vector3f way) {
            if (keepsTurn()) {
                return inverseMass;
            }
            Vector3f arm = new Vector3f(at).sub(center).cross(way);
            return inverseMass + arm.dot(turned(new Vector3f(arm)));
        }

        /** Moves the body by the push {@code push} (pixels times weight) at the point {@code at}: it shifts and turns about it. */
        void nudge(Vector3f at, Vector3f push) {
            nudge(at, push, false);
        }

        /** As {@link #nudge(Vector3f, Vector3f)}; {@code quiet}: moved where it was too, so the move gives it no speed. */
        void nudge(Vector3f at, Vector3f push, boolean quiet) {
            if (quiet) {
                previousCenter.fma(inverseMass, push);
            }
            center.fma(inverseMass, push);
            if (!keepsTurn()) {
                Vector3f turn = turned(new Vector3f(at).sub(center).cross(push));
                turnBy(turn, 1.0F);
                if (quiet) {
                    turnBy(previousRotation, turn);
                }
            }
            refresh();
        }

        /** Turns {@code what} by the small turn {@code angles} (radians about the room's axes). */
        private static void turnBy(Quaternionf what, Vector3f angles) {
            Quaternionf delta = new Quaternionf(angles.x * 0.5F, angles.y * 0.5F, angles.z * 0.5F, 0.0F).mul(what);
            what.set(what.x() + delta.x(), what.y() + delta.y(), what.z() + delta.z(), what.w() + delta.w()).normalize();
        }

        /** Changes its speed by the blow {@code blow} (speed times weight) at the point {@code at}. */
        void strike(Vector3f at, Vector3f blow) {
            velocity.fma(inverseMass, blow);
            if (!keepsTurn()) {
                spin.add(turned(new Vector3f(at).sub(center).cross(blow)));
            }
        }

        /** Turns it by {@code angles} (radians about the room's axes) times {@code times}, as a small turn. */
        private void turnBy(Vector3f angles, float times) {
            Quaternionf delta = new Quaternionf(angles.x * times * 0.5F, angles.y * times * 0.5F, angles.z * times * 0.5F, 0.0F).mul(rotation);
            rotation.set(rotation.x() + delta.x(), rotation.y() + delta.y(), rotation.z() + delta.z(), rotation.w() + delta.w()).normalize();
        }

        /** Moved by the hand: this far ({@code along}, 0 to 1) from where it was at the start of the step to where the hand has it. */
        void driveTo(float along, float h) {
            previousCenter.set(center);
            previousRotation.set(rotation);
            new Vector3f(driveFrom).lerp(driveTo, along, center);
            new Quaternionf(driveTurnFrom).slerp(driveTurnTo, along, rotation).normalize();
            takeSpeed(h);
            refresh();
        }

        /** Where the hand will have it at the end of this step, turned {@code turned}: it is moved there in the short steps. */
        void aim(Vector3f at, Quaternionfc turned) {
            driveFrom.set(center);
            driveTurnFrom.set(rotation);
            driveTo.set(at);
            driveTurnTo.set(turned).normalize();
        }

        /** Stands the box with its middle at {@code at} turned {@code turned}, without speed. */
        void place(Vector3f at, Quaternionf turned) {
            rotation.set(turned);
            place(at);
        }

        /** Sets it moving by {@code speed} (pixels a second). */
        void push(Vector3f speed) {
            velocity.add(speed);
            wake();
        }

        /**
         * Let go of by the hand: it keeps {@code toss} of the speed it moved and turned with (capped), and stops dead with none.
         * A flat item now comes back to this turn. Past an open edge, it may fall off.
         */
        void letGo() {
            float keep = physics.toss();
            velocity.x *= keep;
            velocity.z *= keep;
            float level = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (level > TOSS_MOST) {
                velocity.x *= TOSS_MOST / level;
                velocity.z *= TOSS_MOST / level;
            }
            spin.mul(keep);
            if (spin.length() > SPIN_MOST) {
                spin.normalize(SPIN_MOST);
            }
            restAt(rotation);
            leaving = beyondOpenEdge();
            fresh = false;
            wake();
        }

        /**
         * The hand pulls it towards where it wants it ({@link #aim}): it would get there in {@link #DRIVE_TIME}, but the hand
         * pulls only so hard ({@code hand_force}), so a heavy thing comes slower, and what it runs into and cannot shove stops
         * it. Turned by hand ({@code turning}), it is twisted towards the hand's turn as hard as that force at its edge allows.
         */
        void pull(float h, boolean turning) {
            float mass = 1.0F / inverseMass;
            float most = physics.handForce() * h / mass;
            Vector3f change = new Vector3f(driveTo).sub(center).div(DRIVE_TIME).sub(velocity);
            // The hand holds it down on its surface: pressed into something it is not wedged up that thing; it never lifts it.
            change.y = Math.min(0.0F, change.y);
            if (change.length() > most) {
                change.normalize(most);
            }
            velocity.add(change);
            if (!turning) {
                if (upright) {
                    // A flat item in the grip stands as it was turned.
                    rotation.set(rest);
                    spin.zero();
                    refresh();
                }
                return;
            }
            Quaternionf delta = new Quaternionf(driveTurnTo).mul(new Quaternionf(rotation).conjugate());
            if (delta.w() < 0.0F) {
                delta.set(-delta.x(), -delta.y(), -delta.z(), -delta.w());
            }
            float angle = 2.0F * (float) Math.acos(Math.min(1.0F, delta.w()));
            Vector3f axis = new Vector3f(delta.x(), delta.y(), delta.z());
            if (axis.length() < 1.0e-7F) {
                return;
            }
            if (inverseInertia.lengthSquared() <= 0.0F) {
                // It never turns by a push: the hand turns it as it goes.
                rotation.set(new Quaternionf(rotation).slerp(driveTurnTo, Math.min(1.0F, h / DRIVE_TIME))).normalize();
                restAt(rotation);
                refresh();
                return;
            }
            Vector3f turnChange = axis.normalize(angle / DRIVE_TIME).sub(spin);
            Vector3f twist = inertiaTimes(turnChange);
            float twistMost = physics.handForce() * h * Math.max(0.5F, half.length());
            if (twist.length() > twistMost) {
                turnChange.mul(twistMost / twist.length());
            }
            spin.add(turnChange);
        }

        /** How hard it is to turn it by {@code turning} (radians a second): the turning times its inertia. */
        private Vector3f inertiaTimes(Vector3f turning) {
            Quaternionf frame = frame();
            Vector3f local = new Quaternionf(frame).conjugate().transform(new Vector3f(turning));
            local.set(inverseInertia.x > 0.0F ? local.x / inverseInertia.x : 0.0F, inverseInertia.y > 0.0F ? local.y / inverseInertia.y : 0.0F,
                    inverseInertia.z > 0.0F ? local.z / inverseInertia.z : 0.0F);
            return frame.transform(local);
        }

        /** Whether its middle is past an open edge of its surface. */
        boolean beyondOpenEdge() {
            return center.x < room.from().x() && room.open().contains(PlaceSurface.Side.RIGHT)
                    || center.x > room.to().x() && room.open().contains(PlaceSurface.Side.LEFT)
                    || center.z < room.from().y() && room.open().contains(PlaceSurface.Side.FRONT)
                    || center.z > room.to().y() && room.open().contains(PlaceSurface.Side.BACK);
        }

        /** Stands the box with its middle at {@code at}, turned as it is, without speed. */
        void place(Vector3f at) {
            center.set(at);
            previousCenter.set(center);
            previousRotation.set(rotation);
            driveFrom.set(center);
            driveTo.set(center);
            driveTurnFrom.set(rotation);
            driveTurnTo.set(rotation);
            velocity.zero();
            spin.zero();
            refresh();
        }

        /** Slows it to at most {@code most} pixels a step, at its middle and at its corners as it turns. */
        void limitSpeed(float most) {
            float fastest = most / STEP;
            if (velocity.length() > fastest) {
                velocity.normalize(fastest);
            }
            float turning = spin.length() * half.length();
            if (turning > fastest) {
                spin.mul(fastest / turning);
            }
        }

        /** Moves it whole by {@code by}, without speed. */
        void shift(Vector3f by) {
            center.add(by);
            previousCenter.add(by);
            driveFrom.add(by);
            driveTo.add(by);
            refresh();
        }

        void sleep() {
            awake = false;
            velocity.zero();
            spin.zero();
            previousCenter.set(center);
            previousRotation.set(rotation);
        }

        /**
         * Moves it on by its speed and gravity for a short step. A rocking flat item is pulled back towards how it was turned,
         * as a roly-poly toy is.
         */
        void integrate(float h) {
            previousCenter.set(center);
            previousRotation.set(rotation);
            velocity.y -= physics.gravity() * h;
            velocity.mul(1.0F / (1.0F + AIR * h));
            spin.mul(1.0F / (1.0F + AIR_TURN * h));
            if (upright && physics.rock() > 0.0F) {
                Vector3f away = awayFromRest();
                spin.add(away.mul(-ROCK_PULL * h)).fma(-ROCK_DAMPING * h, spin);
            }
            limit();
            center.fma(h, velocity);
            turnBy(spin, h);
            refresh();
        }

        /** Its turn away from {@link #rest}, as an axis as long as the angle (radians). */
        private Vector3f awayFromRest() {
            Quaternionf away = new Quaternionf(rotation).mul(new Quaternionf(rest).conjugate());
            if (away.w() < 0.0F) {
                away.set(-away.x(), -away.y(), -away.z(), -away.w());
            }
            float angle = 2.0F * (float) Math.acos(Math.min(1.0F, away.w()));
            Vector3f axis = new Vector3f(away.x(), away.y(), away.z());
            float length = axis.length();
            return length < 1.0e-7F ? axis.zero() : axis.mul(angle / length);
        }

        /** Its speed from how far it really went in the short step: the pushes out of other things changed it. */
        void takeSpeed(float h) {
            velocity.set(center).sub(previousCenter).div(h);
            Quaternionf delta = new Quaternionf(rotation).mul(new Quaternionf(previousRotation).conjugate());
            if (delta.w() < 0.0F) {
                delta.set(-delta.x(), -delta.y(), -delta.z(), -delta.w());
            }
            spin.set(delta.x(), delta.y(), delta.z()).mul(2.0F / h);
        }

        /** No faster than {@code max_speed}; a rocking flat item turns away from its rest only so far. */
        void limit() {
            float most = physics.maxSpeed();
            if (velocity.length() > most) {
                velocity.normalize(most);
            }
            float turning = spin.length() * Math.max(0.5F, half.length());
            if (turning > most) {
                spin.mul(most / turning);
            }
            if (upright && physics.rock() > 0.0F) {
                Vector3f away = awayFromRest();
                float angle = away.length();
                float widest = (float) Math.toRadians(physics.rock());
                if (angle > widest) {
                    rotation.set(new Quaternionf(rest).slerp(rotation, widest / angle)).normalize();
                    Vector3f axis = away.div(angle);
                    float outwards = spin.dot(axis);
                    if (outwards > 0.0F) {
                        spin.fma(-outwards, axis);
                    }
                    refresh();
                }
            }
        }

        /** After a step: whether the box moved, and whether it has come to rest. */
        void settle(float dt) {
            moved = true;
            freshFor += dt;
            armedFor += dt;
            if (armedFor > LAND_WAIT) {
                armed = false;
            }
            if (!overlapping || freshFor > FRESH_MOST) {
                fresh = false;
            }
            // Still inside another, it is not at rest yet, however slowly it is moved out.
            boolean inside = overlapping;
            overlapping = false;
            float fastest = Math.max(velocity.length(), spin.length() * half.length());
            still = !gripped && !inside && fastest * STEP < SLEEP_MOTION ? still + dt : 0.0F;
            if (still >= SLEEP_TIME) {
                sleep();
                if (physics.pileRides() && !onFloor && restingOn >= 0) {
                    // Come to rest on another thing (put on it, or fallen onto it): from now on it is part of that pile.
                    perched = true;
                    supportSeen = false;
                }
            }
        }

        boolean overlaps(Body other) {
            float reach = half.length() + other.half.length();
            return center.distanceSquared(other.center) < reach * reach;
        }

        /**
         * Where the box comes to rest, as a pose: a rocking flat item stands as it was turned; a lying one come to rest nearly as
         * it was turned lies exactly so, dead flat.
         */
        ItemPose restingPose(int on) {
            boolean lyingBack = flat && lies(rest) && Math.abs(new Quaternionf(rotation).dot(rest)) > Math.cos(Math.toRadians(LIE_BACK) / 2.0);
            if (!upright && !lyingBack) {
                return pose(on);
            }
            Quaternionf drawn = new Quaternionf(rotation);
            rotation.set(rest);
            refresh();
            ItemPose pose = pose(on);
            rotation.set(drawn);
            refresh();
            return pose;
        }

        /** Where the box stands now, as a pose: on its lowest point. */
        ItemPose pose(int on) {
            float lowest = Float.MAX_VALUE;
            for (Vector3f point : points) {
                lowest = Math.min(lowest, point.y);
            }
            lowest = Math.max(lowest, room.height());
            return new ItemPose(center.x, lowest, center.z, Math.max(0.0F, center.y - lowest), new Quaternionf(rotation), scale, on, wanted);
        }
    }
}
