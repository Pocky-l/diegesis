package com.pockyl.diegesis.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlacingPhysics;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.impl.client.scene.PlacedPhysics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.pockyl.diegesis.gametest.PlacingGameTests.CUBE;
import static com.pockyl.diegesis.gametest.PlacingGameTests.ICON;
import static com.pockyl.diegesis.gametest.PlacingGameTests.check;

/**
 * Heaps: many things thrown in one after another, flat ones and blocks, as players fill a drawer or a shelf. Owner,
 * 2026-11-21: "thrown into a heap, they jump, turn and move about". A heap comes to rest, stays at rest, and putting one more
 * thing in moves only what it touches. Also: things fall as fast as real ones do.
 */
@GameTestHolder(Diegesis.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacingHeapTests {
    private static final float FRAME = 1.0F / 60.0F;
    private static final Quaternionf LYING = new Quaternionf().rotateX((float) Math.PI / 2.0F);

    private PlacingHeapTests() {
    }

    /** A kitchen drawer: 14 by 11, walled all round, room 5 high, things 4 pixels big, flat things lie. */
    static PlacingOptions drawer() {
        return PlacingGameTests.drawer();
    }

    /** The drawer with room 14 high: a heap with room to grow. */
    static PlacingOptions crate() {
        PlacingOptions drawer = drawer();
        PlaceSurface tray = drawer.surfaces().get(0);
        PlaceSurface high = new PlaceSurface(tray.from(), tray.to(), tray.height(), 14.0F, tray.aimHeight(), tray.wall(), tray.open(),
                tray.headroom(), tray.flatRest());
        return with(drawer, high, drawer.physics().orElseThrow());
    }

    static PlacingOptions with(PlacingOptions built, PlaceSurface surface, PlacingPhysics physics) {
        return new PlacingOptions(built.group(), List.of(surface), built.itemSize(), built.turnStep(), built.scaleRange(), built.scaleStep(),
                built.turnSpeed(), built.stacking(), built.blockModels(), built.itemLight(), built.lightRadius(), built.lightColor(),
                built.shadows(), built.snap(), built.previewColor(), built.counts(), java.util.Optional.of(physics), built.framePose(),
                built.hoverLift(), built.handling());
    }

    /** The default physics with another gravity. */
    static PlacingPhysics withGravity(float gravity) {
        PlacingPhysics d = PlacingPhysics.DEFAULT;
        return new PlacingPhysics(gravity, d.friction(), d.uprightFlat(), d.rock(), d.flatDepth(), d.hover(), d.carryLag(), d.reach(),
                d.climb(), d.swing(), d.swingAngle(), d.swingCalm(), d.hopTime(), d.toss(), d.handPushes(), d.handForce(), d.turnPower(),
                d.fallOff(), d.maxSpeed(), d.bounce(), d.pileRides(), d.flatPoses(), d.flush(), d.landingSound());
    }

    /** Half the box of a thing for the physics, pixels along its own sides, as the physics makes it. */
    static Vector3f half(PlacingOptions options, PlacedPhysics.Solid solid, float scale) {
        Vector3f half = solid.half(options.itemSize() * scale);
        return half.max(new Vector3f(solid.flat() ? PlacedPhysics.flatHalf(options.physics().orElseThrow()) : 0.1F));
    }

    /** Half the room a box of half size {@code half} turned {@code turn} takes along the room's axes. */
    static Vector3f extents(Vector3f half, Quaternionf turn) {
        Matrix3f m = new Matrix3f().set(turn);
        return new Vector3f(Math.abs(m.m00) * half.x + Math.abs(m.m10) * half.y + Math.abs(m.m20) * half.z,
                Math.abs(m.m01) * half.x + Math.abs(m.m11) * half.y + Math.abs(m.m21) * half.z,
                Math.abs(m.m02) * half.x + Math.abs(m.m12) * half.y + Math.abs(m.m22) * half.z);
    }

    /**
     * Lets a thing go from the hand over {@code x, z}, as the hand does: a hover over the highest thing under it where it fits
     * under the room's top, else beside what is there on the floor.
     */
    static void drop(PlacingGameTests.Scene scene, int slot, ItemStack stack, PlacedPhysics.Solid solid, float x, float z, Quaternionf turn,
            float scale) {
        drop(scene, slot, stack, solid, x, z, turn, scale, true);
    }

    /** As above; with {@code beside} false, a thing that does not fit on top is not let go of (false). */
    static boolean drop(PlacingGameTests.Scene scene, int slot, ItemStack stack, PlacedPhysics.Solid solid, float x, float z, Quaternionf turn,
            float scale, boolean beside) {
        PlacingOptions options = scene.options;
        PlaceSurface room = options.surfaces().get(0);
        float hover = options.physics().orElseThrow().hover();
        Vector3f ext = extents(half(options, solid, scale), turn);
        x = Math.max(room.from().x() + ext.x, Math.min(room.to().x() - ext.x, x));
        z = Math.max(room.from().y() + ext.z, Math.min(room.to().y() - ext.z, z));
        PlacedPhysics.Ground ground = scene.physics.groundUnder(room, x - ext.x, x + ext.x, z - ext.z, z + ext.z);
        float ceiling = room.height() + room.clearance();
        float y;
        float lift;
        int on;
        if (ground.top() + ext.y * 2.0F + hover <= ceiling) {
            y = ground.top();
            lift = ext.y + hover;
            on = ground.index();
        } else if (!beside) {
            return false;
        } else {
            Vector3f spot = scene.physics.clearSpot(room, new Vector3f(x, 0.0F, z), ext.x, ext.z, room.height(), room.height() + ext.y * 2.0F);
            x = spot.x;
            z = spot.z;
            y = room.height();
            lift = ext.y;
            on = -1;
        }
        scene.physics.toss(slot, 0.0F, 0.0F);
        scene.put(slot, stack, solid, new ItemPose(x, y, z, lift, turn, scale, on));
        return true;
    }

    private static final Item[] FLAT = {Items.BREAD, Items.PAPER, Items.CARROT, Items.COD, Items.APPLE, Items.COOKED_BEEF};
    private static final Item[] BLOCKS = {Items.STONE, Items.OAK_PLANKS, Items.IRON_BLOCK, Items.WHITE_WOOL, Items.MELON};

    /** What {@link #throwIn} drops: lying flat things, flat things at a lean, blocks, small blocks. */
    enum Mix {
        FLAT, MIXED
    }

    /** Turns the things thrown in were given, by slot. */
    static final class Heap {
        final PlacingGameTests.Scene scene;
        final Quaternionf[] turns;
        final boolean[] flat;

        Heap(PlacingGameTests.Scene scene, int count) {
            this.scene = scene;
            this.turns = new Quaternionf[count];
            this.flat = new boolean[count];
        }
    }

    /** Throws {@code count} things in at random spots, one every {@code every} frames, running the frames between. */
    static Heap throwIn(PlacingOptions options, int count, Mix mix, int every, long seed) {
        return throwIn(options, count, mix, every, seed, false);
    }

    /**
     * As a player fills a place: at a random spot where the thing fits on top of what is there (another spot if not); with
     * {@code crowd}, forced in beside the rest where it does not fit.
     */
    static Heap throwIn(PlacingOptions options, int count, Mix mix, int every, long seed, boolean crowd) {
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, count + 2);
        Heap heap = new Heap(scene, count + 2);
        Random random = new Random(seed);
        PlaceSurface room = options.surfaces().get(0);
        for (int i = 0; i < count; i++) {
            float yaw = random.nextFloat() * (float) Math.PI * 2.0F;
            int kind = mix == Mix.FLAT ? 0 : i % 6;
            Quaternionf turn;
            boolean flat = kind <= 2;
            float scale;
            if (flat) {
                // Lying flat things; a third of the mix's flat ones let go of at a lean.
                float lean = kind == 2 ? 0.7F : 0.0F;
                turn = new Quaternionf().rotateY(yaw).rotateX((float) Math.PI / 2.0F - lean);
                scale = 0.75F + random.nextFloat() * 0.5F;
            } else {
                turn = new Quaternionf().rotateY(yaw * 0.25F);
                scale = kind == 5 ? 0.5F : 0.8F + random.nextFloat() * 0.2F;
            }
            ItemStack stack = new ItemStack(flat ? FLAT[i % FLAT.length] : BLOCKS[i % BLOCKS.length]);
            PlacedPhysics.Solid solid = flat ? ICON : CUBE;
            for (int tries = 0; tries < 12; tries++) {
                float x = room.from().x() + random.nextFloat() * (room.to().x() - room.from().x());
                float z = room.from().y() + random.nextFloat() * (room.to().y() - room.from().y());
                if (drop(scene, i, stack, solid, x, z, turn, scale, crowd)) {
                    break;
                }
            }
            heap.flat[i] = flat;
            heap.turns[i] = turn;
            for (int frame = 0; frame < every; frame++) {
                scene.physics.update(FRAME);
            }
        }
        return heap;
    }

    /** Runs until nothing moves, at most {@code most} seconds; returns the seconds it took (more than {@code most}: never). */
    static float settle(PlacingGameTests.Scene scene, float most) {
        float time = 0.0F;
        while (time <= most) {
            scene.physics.update(FRAME);
            time += FRAME;
            if (scene.physics.awake() == 0) {
                return time;
            }
        }
        return time;
    }

    /** The largest turn (degrees) of any item between two lists of poses. */
    static float turned(List<ItemPose> before, List<ItemPose> after) {
        float most = 0.0F;
        for (int i = 0; i < before.size(); i++) {
            ItemPose a = before.get(i);
            ItemPose b = after.get(i);
            if (a == null || b == null) {
                continue;
            }
            float dot = Math.min(1.0F, Math.abs(new Quaternionf(a.rotation()).dot(new Quaternionf(b.rotation()))));
            most = Math.max(most, (float) Math.toDegrees(2.0 * Math.acos(dot)));
        }
        return most;
    }

    static void log(String test, String text) {
        com.pockyl.diegesis.DiegesisMod.LOGGER.info("[heap] {}: {}", test, text);
    }

    /** A heap left alone after it rested: nothing it does by itself. */
    static String stillness(PlacingGameTests.Scene scene, float seconds) {
        List<ItemPose> rested = scene.poses();
        int sentBefore = scene.sent.size();
        int awakeFrames = 0;
        for (float time = 0.0F; time < seconds; time += FRAME) {
            scene.physics.update(FRAME);
            if (scene.physics.awake() > 0) {
                awakeFrames++;
            }
        }
        return String.format("moved %.4f turned %.3f sent %d awakeFrames %d", PlacingStressTests.moved(scene, rested),
                turned(rested, scene.poses()), scene.sent.size() - sentBefore, awakeFrames);
    }

    static float apart(ItemPose a, ItemPose b) {
        return (float) Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() + a.lift() - b.y() - b.lift()) * (a.y() + a.lift() - b.y() - b.lift())
                + (a.z() - b.z()) * (a.z() - b.z()));
    }

    static float movedOf(PlacingGameTests.Scene scene, List<ItemPose> before) {
        return PlacingStressTests.moved(scene, before);
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aHeapOfFlatThingsComesToRest(GameTestHelper helper) {
        Heap heap = throwIn(drawer(), 40, Mix.FLAT, 6, 11L);
        PlacingGameTests.Scene scene = heap.scene;
        float rest = settle(scene, 8.0F);
        float overlap = scene.physics.deepestOverlap();
        float outside = scene.physics.outside();
        List<ItemPose> rested = scene.poses();
        String still = stillness(scene, 10.0F);
        float moved = movedOf(scene, rested);
        log("aHeapOfFlatThingsComesToRest", String.format("rest %.2f s overlap %.3f outside %.3f, then %s", rest, overlap, outside, still));
        check(helper, rest < 3.0F, "the heap rests within 3 s of the last throw: " + rest);
        check(helper, overlap < 0.06F && outside < 0.05F, "nothing inside anything or out of the drawer: " + overlap + " " + outside);
        check(helper, moved < 0.01F, "then it stays: " + still);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aMixedHeapComesToRest(GameTestHelper helper) {
        Heap heap = throwIn(crate(), 60, Mix.MIXED, 6, 23L);
        PlacingGameTests.Scene scene = heap.scene;
        float rest = settle(scene, 8.0F);
        float overlap = scene.physics.deepestOverlap();
        float outside = scene.physics.outside();
        List<ItemPose> rested = scene.poses();
        String still = stillness(scene, 10.0F);
        float moved = movedOf(scene, rested);
        log("aMixedHeapComesToRest", String.format("rest %.2f s overlap %.3f outside %.3f, then %s", rest, overlap, outside, still));
        check(helper, rest < 4.0F, "the heap rests within 4 s of the last throw: " + rest);
        check(helper, overlap < 0.06F && outside < 0.05F, "nothing inside anything or out of the crate: " + overlap + " " + outside);
        check(helper, moved < 0.01F, "then it stays: " + still);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aCrowdedDrawerComesToRest(GameTestHelper helper) {
        // The drawer as it is (room 5): blocks and flat things until it is full, the upper layer against the drawer's top.
        Heap heap = throwIn(drawer(), 28, Mix.MIXED, 6, 5L, true);
        PlacingGameTests.Scene scene = heap.scene;
        float rest = settle(scene, 8.0F);
        float overlap = scene.physics.deepestOverlap();
        List<ItemPose> rested = scene.poses();
        String still = stillness(scene, 10.0F);
        float moved = movedOf(scene, rested);
        log("aCrowdedDrawerComesToRest", String.format("rest %.2f s overlap %.3f, then %s", rest, overlap, still));
        check(helper, rest < 4.0F, "the drawer rests within 4 s of the last throw: " + rest);
        check(helper, moved < 0.01F, "then it stays: " + still);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aHeapOnAnOpenShelfComesToRest(GameTestHelper helper) {
        Heap heap = throwIn(PlacingStressTests.cabinet(), 45, Mix.MIXED, 6, 7L);
        PlacingGameTests.Scene scene = heap.scene;
        float rest = settle(scene, 8.0F);
        List<ItemPose> rested = scene.poses();
        String still = stillness(scene, 10.0F);
        float moved = movedOf(scene, rested);
        log("aHeapOnAnOpenShelfComesToRest", String.format("rest %.2f s, then %s", rest, still));
        check(helper, rest < 4.0F, "the shelf rests within 4 s of the last throw: " + rest);
        check(helper, moved < 0.01F, "then it stays: " + still);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void noThingSpinsWhileAHeapSettles(GameTestHelper helper) {
        Heap heap = throwIn(drawer(), 40, Mix.FLAT, 6, 11L);
        PlacingGameTests.Scene scene = heap.scene;
        settle(scene, 8.0F);
        float most = 0.0F;
        int worst = -1;
        for (int i = 0; i < 40; i++) {
            ItemPose pose = scene.poses.get(i);
            if (pose == null) {
                continue;
            }
            // How far it turned about the vertical from how it was let go of.
            Quaternionf change = new Quaternionf(pose.rotation()).mul(new Quaternionf(heap.turns[i]).conjugate());
            float twist = (float) Math.abs(Math.toDegrees(2.0 * Math.atan2(change.y, change.w)));
            twist = twist > 180.0F ? 360.0F - twist : twist;
            if (twist > most) {
                most = twist;
                worst = i;
            }
        }
        log("noThingSpinsWhileAHeapSettles", String.format("most twist %.1f deg (slot %d)", most, worst));
        check(helper, most < 20.0F, "no flat thing spun round while the heap settled: " + most + " degrees, slot " + worst);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void puttingOneMoreWakesOnlyWhatItTouches(GameTestHelper helper) {
        // Two heaps of flat things in one wide drawer, 6 pixels apart; one more bread laid on the left one.
        PlacingOptions built = PlacingOptions.builder("items").surface(1.0F, -8.0F, 31.0F, 3.0F, 8.0F, 5.0F, 0.0F).itemSize(4.0F)
                .physics(PlacingPhysics.DEFAULT).build();
        PlaceSurface tray = built.surfaces().get(0);
        PlacingOptions options = with(built, new PlaceSurface(tray.from(), tray.to(), tray.height(), tray.clearance(), tray.aimHeight(),
                tray.wall(), tray.open(), tray.headroom(), PlaceSurface.FlatRest.LIE), PlacingPhysics.DEFAULT);
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 31);
        Random random = new Random(13L);
        for (int i = 0; i < 30; i++) {
            boolean left = i % 2 == 0;
            float x = (left ? 3.5F : 21.5F) + random.nextFloat() * 6.0F;
            float z = -5.5F + random.nextFloat() * 6.0F;
            float scale = 0.75F + random.nextFloat() * 0.5F;
            drop(scene, i, new ItemStack(FLAT[i % FLAT.length]), ICON, x, z, new Quaternionf().rotateY(random.nextFloat() * 6.28F).mul(LYING),
                    scale, false);
            scene.run(0.1F);
        }
        settle(scene, 8.0F);
        List<ItemPose> rested = scene.poses();
        drop(scene, 30, new ItemStack(Items.BREAD), ICON, 6.5F, -2.5F, new Quaternionf().rotateY(0.4F).mul(LYING), 1.0F);
        int rightAwake = 0;
        int leftAwake = 0;
        for (float time = 0.0F; time < 2.0F; time += FRAME) {
            scene.physics.update(FRAME);
            for (int i = 0; i < 30; i++) {
                if (scene.physics.moving(i)) {
                    if (i % 2 == 0) {
                        leftAwake++;
                    } else {
                        rightAwake++;
                    }
                }
            }
        }
        settle(scene, 4.0F);
        int leftMoved = 0;
        int rightMoved = 0;
        for (int i = 0; i < 30; i++) {
            if (rested.get(i) == null || scene.poses.get(i) == null) {
                continue;
            }
            // Moved so one sees it: a tenth of a pixel, or turned 3 degrees.
            if (apart(rested.get(i), scene.poses.get(i)) > 0.1F || turned(List.of(rested.get(i)), List.of(scene.poses.get(i))) > 3.0F) {
                if (i % 2 == 0) {
                    leftMoved++;
                } else {
                    rightMoved++;
                }
            }
        }
        log("puttingOneMoreWakesOnlyWhatItTouches", String.format("left heap: %d moved, %d awake frames; right heap: %d moved, %d awake frames",
                leftMoved, leftAwake, rightMoved, rightAwake));
        check(helper, rightAwake == 0 && rightMoved == 0, "the other heap never stirred: " + rightAwake + " awake frames, " + rightMoved + " moved");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void wakingAHeapTenTimesMovesNothing(GameTestHelper helper) {
        PlacingOptions crate = crate();
        Heap heap = throwIn(with(crate, crate.surfaces().get(0), wakingAll(crate.physics().orElseThrow())), 30, Mix.FLAT, 6, 3L);
        PlacingGameTests.Scene scene = heap.scene;
        settle(scene, 8.0F);
        List<ItemPose> rested = scene.poses();
        for (int round = 0; round < 10; round++) {
            // Something put down high over the heap and taken away before it lands: every thing wakes each time.
            scene.put(30, new ItemStack(Items.STONE), CUBE, new ItemPose(8.0F, 20.0F, -2.5F, 0.5F, new Quaternionf(), 0.3F, -1));
            scene.physics.update(FRAME);
            scene.put(30, ItemStack.EMPTY, null, null);
            scene.run(1.5F);
        }
        float moved = movedOf(scene, rested);
        float turn = turned(rested.subList(0, 30), scene.poses().subList(0, 30));
        log("wakingAHeapTenTimesMovesNothing", String.format("moved %.4f turned %.3f", moved, turn));
        // Each wake settles every thing again; leaning flat things may settle by a hair more (measured 0.04 px, 1.5 degrees in
        // all ten), never visibly. With the default wake (near) nothing here wakes at all.
        check(helper, moved < 0.05F && turn < 2.0F, "ten wakes moved nothing one sees: " + moved + " px, " + turn + " degrees");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aHeavyBlockOnAFlatThingStandsStill(GameTestHelper helper) {
        PlacingOptions options = drawer();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 2);
        drop(scene, 0, new ItemStack(Items.BREAD), ICON, 8.0F, -2.5F, LYING, 1.0F);
        settle(scene, 2.0F);
        float iconY = scene.poses.get(0).y() + scene.poses.get(0).lift();
        drop(scene, 1, new ItemStack(Items.IRON_BLOCK), CUBE, 8.0F, -2.5F, new Quaternionf(), 1.0F);
        float rest = settle(scene, 4.0F);
        List<ItemPose> rested = scene.poses();
        String still = stillness(scene, 5.0F);
        float sink = iconY - (scene.poses.get(0).y() + scene.poses.get(0).lift());
        log("aHeavyBlockOnAFlatThingStandsStill", String.format("rest %.2f s sink %.4f, then %s", rest, sink, still));
        check(helper, rest < 1.0F, "at rest within a second: " + rest);
        check(helper, Math.abs(sink) < 0.02F && movedOf(scene, rested) < 0.01F, "the bread is not pressed down or shoved: " + sink + " " + still);
        helper.succeed();
    }

    static float creep() {
        PlacingOptions options = PlacingStressTests.cabinet();
        float half = CUBE.half(2.0F).y;
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 2);
        scene.physics.toss(0, 0.0F, 0.0F);
        scene.put(0, new ItemStack(Items.STONE), CUBE, new ItemPose(5.0F, 1.0F, 8.0F, half + 1.5F, new Quaternionf().rotateZ(0.08F), 1.0F, -1));
        scene.run(6.0F);
        List<ItemPose> rested = scene.poses();
        for (int round = 0; round < 4; round++) {
            scene.put(1, new ItemStack(Items.APPLE), CUBE, new ItemPose(12.0F, 1.0F, 12.0F, half, new Quaternionf(), 1.0F, -1));
            scene.run(1.0F);
            scene.put(1, ItemStack.EMPTY, null, null);
            scene.run(1.0F);
        }
        return movedOf(scene, rested.subList(0, 1));
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aBlockWokenAgainAndAgainNeverCreeps(GameTestHelper helper) {
        // Let go of a little leaning, at rest, then woken by things put down and taken away elsewhere, four times.
        PlacingOptions options = PlacingStressTests.cabinet();
        float half = CUBE.half(2.0F).y;
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 2);
        scene.physics.toss(0, 0.0F, 0.0F);
        scene.put(0, new ItemStack(Items.STONE), CUBE, new ItemPose(5.0F, 1.0F, 8.0F, half + 1.5F, new Quaternionf().rotateZ(0.08F), 1.0F, -1));
        scene.run(6.0F);
        List<ItemPose> rested = scene.poses();
        PlacingOptions all = with(options, options.surfaces().get(0), wakingAll(options.physics().orElseThrow()));
        PlacingGameTests.Scene woken = new PlacingGameTests.Scene(all, 2);
        woken.put(0, scene.stacks.get(0), CUBE, rested.get(0));
        woken.run(2.0F);
        List<ItemPose> start = woken.poses();
        for (int round = 0; round < 4; round++) {
            woken.put(1, new ItemStack(Items.APPLE), CUBE, new ItemPose(12.0F, 1.0F, 12.0F, half, new Quaternionf(), 1.0F, -1));
            woken.run(1.0F);
            woken.put(1, ItemStack.EMPTY, null, null);
            woken.run(1.0F);
        }
        float moved = movedOf(woken, start.subList(0, 1));
        log("aBlockWokenAgainAndAgainNeverCreeps", String.format("moved %.4f", moved));
        check(helper, moved < 0.002F, "woken four times, it did not creep: " + moved);
        helper.succeed();
    }

    /** The physics with {@code wake: all}: every change wakes everything. */
    static PlacingPhysics wakingAll(PlacingPhysics d) {
        return new PlacingPhysics(d.gravity(), d.friction(), d.uprightFlat(), d.rock(), d.flatDepth(), d.hover(), d.carryLag(), d.reach(),
                d.climb(), d.swing(), d.swingAngle(), d.swingCalm(), d.hopTime(), d.toss(), d.handPushes(), d.handForce(), d.turnPower(),
                d.fallOff(), d.maxSpeed(), d.bounce(), d.pileRides(), d.flatPoses(), d.flush(), d.landingSound(), d.pushOut(),
                PlacingPhysics.Wake.ALL, d.fallEase());
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void flatThingsAcrossEachOtherLieStill(GameTestHelper helper) {
        PlacingOptions options = drawer();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 3);
        Quaternionf[] turns = {new Quaternionf(LYING), new Quaternionf().rotateY((float) Math.toRadians(45.0)).mul(LYING),
                new Quaternionf().rotateY((float) Math.toRadians(20.0)).mul(LYING)};
        drop(scene, 0, new ItemStack(Items.PAPER), ICON, 6.5F, -3.0F, turns[0], 1.0F);
        settle(scene, 2.0F);
        drop(scene, 1, new ItemStack(Items.PAPER), ICON, 8.5F, -2.0F, turns[1], 1.0F);
        settle(scene, 2.0F);
        drop(scene, 2, new ItemStack(Items.PAPER), ICON, 7.5F, -2.5F, turns[2], 1.0F);
        float rest = settle(scene, 4.0F);
        float most = 0.0F;
        for (int i = 0; i < 3; i++) {
            float dot = Math.min(1.0F, Math.abs(new Quaternionf(scene.poses.get(i).rotation()).dot(turns[i])));
            most = Math.max(most, (float) Math.toDegrees(2.0 * Math.acos(dot)));
        }
        log("flatThingsAcrossEachOtherLieStill", String.format("rest %.2f s turned %.2f deg", rest, most));
        check(helper, rest < 1.5F, "at rest within 1.5 s: " + rest);
        check(helper, most < 8.0F, "each lies about as it was let go of: " + most + " degrees");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aThingPutInsideAnotherGlidesOut(GameTestHelper helper) {
        PlacingOptions options = crate();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 2);
        float half = half(options, CUBE, 1.0F).y;
        scene.put(0, new ItemStack(Items.STONE), CUBE, new ItemPose(7.0F, 8.0F, -2.5F, half, new Quaternionf(), 1.0F, -1));
        scene.run(0.5F);
        // Put down a pixel inside it.
        scene.put(1, new ItemStack(Items.STONE), CUBE, new ItemPose(7.0F + half * 2.0F - 1.0F, 8.0F, -2.5F, half, new Quaternionf(), 1.0F, -1));
        float jump = 0.0F;
        ItemPose last = scene.poses.get(1);
        float apart = -1.0F;
        for (float time = 0.0F; time < 2.0F; time += FRAME) {
            scene.physics.update(FRAME);
            ItemPose now = scene.poses.get(1);
            jump = Math.max(jump, Math.abs(now.x() - last.x()) + Math.abs(now.z() - last.z()));
            last = now;
            if (apart < 0.0F && scene.physics.deepestOverlap() < 0.02F) {
                apart = time;
            }
        }
        log("aThingPutInsideAnotherGlidesOut", String.format("largest frame move %.3f apart at %.2f s", jump, apart));
        check(helper, apart >= 0.0F && apart < 0.6F, "apart within 0.6 s: " + apart);
        check(helper, jump < 0.4F, "it glides out, no frame jumps: " + jump);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aStandingFlatThingUnderALoadRests(GameTestHelper helper) {
        // A fridge shelf: bread stood up, a stone let go of leaning against it.
        PlacingOptions options = PlacingGameTests.fridge();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 2);
        float icon = ICON.half(options.itemSize()).y;
        scene.put(0, new ItemStack(Items.BREAD), ICON, new ItemPose(8.0F, 1.0F, 8.0F, icon, new Quaternionf(), 1.0F, -1));
        scene.run(0.5F);
        float half = CUBE.half(options.itemSize()).y;
        scene.physics.toss(1, 0.0F, 0.0F);
        scene.put(1, new ItemStack(Items.STONE), CUBE, new ItemPose(8.0F, 1.0F, 8.0F - 0.1F - half * 1.2F, half + 0.5F,
                new Quaternionf().rotateX(0.5F), 1.0F, -1));
        float rest = settle(scene, 4.0F);
        List<ItemPose> rested = scene.poses();
        String still = stillness(scene, 5.0F);
        log("aStandingFlatThingUnderALoadRests", String.format("rest %.2f s, then %s; bread %s", rest, still, scene.poses.get(0)));
        check(helper, rest < 1.5F, "both rest within 1.5 s: " + rest);
        check(helper, movedOf(scene, rested) < 0.01F, "then they stay: " + still);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void thingsFallAsGravitySays(GameTestHelper helper) {
        float[] gravities = {PlacingPhysics.DEFAULT.gravity(), 200.0F, 314.0F};
        StringBuilder text = new StringBuilder();
        for (float gravity : gravities) {
            PlacingOptions base = crate();
            PlacingPhysics physics = withGravity(gravity);
            PlacingOptions options = with(base, base.surfaces().get(0), physics);
            PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 1);
            float half = half(options, CUBE, 1.0F).y;
            float height = 6.0F;
            scene.put(0, new ItemStack(Items.STONE), CUBE, new ItemPose(8.0F, 8.0F, -2.5F, half + height, new Quaternionf(), 1.0F, -1));
            float landed = -1.0F;
            float at015 = Float.NaN;
            float time = 0.0F;
            for (int frame = 1; frame <= 120 && landed < 0.0F; frame++) {
                scene.physics.update(FRAME);
                time = frame * FRAME;
                ItemPose pose = scene.poses.get(0);
                float bottom = pose.y() + pose.lift() - half;
                if (frame == 9) {
                    at015 = 8.0F + height - bottom;
                }
                if (bottom < 8.0F + 0.01F) {
                    landed = time;
                }
            }
            float expected = (float) Math.sqrt(2.0 * height / gravity);
            float fallenExpected = 0.5F * gravity * 0.15F * 0.15F;
            text.append(String.format("g %.0f: landed %.3f s (expected %.3f), fallen at 0.15 s %.2f (expected %.2f); ", gravity, landed,
                    expected, at015, fallenExpected));
            check(helper, landed > 0.0F && Math.abs(landed - expected) < 1.5F * FRAME, "g " + gravity + ": lands at " + landed + ", " + expected
                    + " expected");
            check(helper, Math.abs(at015 - fallenExpected) < 0.15F, "g " + gravity + ": fallen " + at015 + " at 0.15 s, " + fallenExpected
                    + " expected");
        }
        log("thingsFallAsGravitySays", text.toString());
        check(helper, Math.abs(PlacingPhysics.DEFAULT.gravity() - 9.81F * 16.0F) < 1.0F, "the default is the real one at 16 pixels a metre");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aHeapCostsLittle(GameTestHelper helper) {
        PlacingOptions crate = crate();
        Heap heap = throwIn(with(crate, crate.surfaces().get(0), wakingAll(crate.physics().orElseThrow())), 60, Mix.MIXED, 2, 31L);
        PlacingGameTests.Scene scene = heap.scene;
        // Everything awake at once (wake: all): what a frame costs at worst.
        scene.put(61, new ItemStack(Items.STONE), CUBE, new ItemPose(14.0F, 8.0F, 2.0F, 1.0F, new Quaternionf(), 0.5F, -1));
        long worst = 0L;
        long total = 0L;
        int frames = 0;
        for (int frame = 0; frame < 120; frame++) {
            long start = System.nanoTime();
            scene.physics.update(FRAME);
            long took = System.nanoTime() - start;
            if (frame >= 20) {
                total += took;
                worst = Math.max(worst, took);
                frames++;
            }
        }
        float mean = total / (float) frames / 1.0e6F;
        log("aHeapCostsLittle", String.format("mean %.3f ms worst %.3f ms (60 things)", mean, worst / 1.0e6F));
        check(helper, mean < 8.0F, "a frame of a heap costs little: " + mean + " ms");
        helper.succeed();
    }
}
