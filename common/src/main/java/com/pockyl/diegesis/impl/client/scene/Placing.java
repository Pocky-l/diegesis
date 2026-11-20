package com.pockyl.diegesis.impl.client.scene;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.state.ItemPose;

import java.util.List;

/** Where items placed freely can stand: the geometry of the surfaces of a scene's {@code "placing"}. */
public final class Placing {
    /** How far (pixels) past its edges a surface still catches the cursor. */
    private static final float AIM_MARGIN = 1.5F;
    /** Pixels a thing made to fit under what is over it stays under it. */
    private static final float CEILING_SKIN = 0.05F;
    /** Pixels under an item's top within which a hit counts as on its top face. */
    private static final float TOP_FACE = 0.02F;
    /** How many things a pile is followed up at most. */
    private static final int PILE_MOST = 32;

    private Placing() {
    }

    /** How big a thing is, as the geometry of placing needs it (a {@link PlacedShape}, or a plain box). */
    public interface Shape {
        /** Half the box round it in scene pixels, turned as given, for a thing {@code size} pixels big. */
        Vector3f extents(Quaternionfc rotation, float size);

        /** Whether it is an icon a pixel thin, not a thing with a body. */
        boolean isFlat();
    }

    /** A point on a surface the cursor is aimed at. */
    public record Aim(PlaceSurface surface, float x, float z) {
    }

    /** The surface the cursor ray finds first, or null. */
    public static Aim aim(PlacingOptions options, Vector3fc origin, Vector3fc direction) {
        return aim(options, origin, direction, null);
    }

    /**
     * The surface the cursor ray finds first, or null; surfaces the scene moves (on a door swung open) are found where they are
     * now, and the point is given on the surface as written.
     */
    public static Aim aim(PlacingOptions options, Vector3fc rayOrigin, Vector3fc rayDirection, SceneInstance scene) {
        Aim best = null;
        float nearest = Float.MAX_VALUE;
        for (int index = 0; index < options.surfaces().size(); index++) {
            PlaceSurface surface = options.surfaces().get(index);
            if (scene != null && !scene.isSurfaceVisible(index)) {
                // Shut away: it takes nothing.
                continue;
            }
            float[] hit = hit(surface, index, rayOrigin, rayDirection, scene);
            if (hit == null || hit[0] >= nearest || hit[1] < surface.from().x() - AIM_MARGIN || hit[1] > surface.to().x() + AIM_MARGIN
                    || hit[2] < surface.from().y() - AIM_MARGIN || hit[2] > surface.to().y() + AIM_MARGIN) {
                continue;
            }
            nearest = hit[0];
            best = new Aim(surface, hit[1], hit[2]);
        }
        return best;
    }

    /**
     * Where the cursor ray meets the plane of one surface, wherever that is, also past its edges: a thing slid along a shelf stays
     * on that shelf when the cursor leaves it, rather than jumping to whatever surface lies behind. Null when the ray misses the
     * plane.
     */
    public static Aim aimOn(PlacingOptions options, PlaceSurface surface, Vector3fc rayOrigin, Vector3fc rayDirection, SceneInstance scene) {
        float[] hit = hit(surface, options.surfaces().indexOf(surface), rayOrigin, rayDirection, scene);
        return hit == null ? null : new Aim(surface, hit[1], hit[2]);
    }

    /** The ray's length to the surface's plane, and x, z where it meets it (as the surface is written), or null. */
    private static float[] hit(PlaceSurface surface, int index, Vector3fc rayOrigin, Vector3fc rayDirection, SceneInstance scene) {
        org.joml.Matrix4f move = scene == null || index < 0 ? null : scene.surfaceMove(index);
        Vector3fc origin = rayOrigin;
        Vector3fc direction = rayDirection;
        if (move != null) {
            // The ray as the surface sees it: its move undone (turns and shifts keep the ray's length, so t still compares).
            org.joml.Matrix4f back = new org.joml.Matrix4f(move).invert();
            origin = back.transformPosition(new Vector3f(rayOrigin));
            direction = back.transformDirection(new Vector3f(rayDirection));
        }
        float depth = Math.max(0.01F, surface.to().y() - surface.from().y());
        float slope = surface.aimHeight() / depth;
        // The slope: y = height + slope * (z - front).
        float denominator = direction.y() - slope * direction.z();
        if (Math.abs(denominator) < 1.0e-6F) {
            return null;
        }
        float t = (surface.height() + slope * (origin.z() - surface.from().y()) - origin.y()) / denominator;
        return t <= 0.0F ? null : new float[] {t, origin.x() + direction.x() * t, origin.z() + direction.z() * t};
    }

    /**
     * Whether a point hit on {@code below} at the height {@code hitY} means on top of it: anywhere on a low thing ({@code stack_low}
     * of {@code item_size}: a lying book, a plate), on its top face, or from {@code stack_from} of its height up; lower on a tall
     * thing, the stack in the hand goes in front of it. Measured against the scene's item size, so that it holds as well in a
     * fridge of small things as on a table of big ones.
     */
    public static boolean isOnTop(PlacingOptions options, ItemPose below, float hitY) {
        float height = below.lift() * 2.0F;
        PlacingOptions.Handling handling = options.handling();
        return height < handling.stackLow() * options.itemSize() || hitY >= below.top() - TOP_FACE
                || hitY >= below.y() + height * handling.stackFrom();
    }

    /**
     * Where on top of a support the middle of a thing of half size {@code halfX} by {@code halfZ} goes, aimed at {@code aimX,
     * aimZ}: there, kept so its middle stays over the support's top ({@code box}, as {@link #boxOf} gives it) by {@code edge} of
     * its own half size, and on the support's middle along each way it is within {@code snap} of the support's half size of it.
     */
    public static float[] onTopOf(float aimX, float aimZ, float[] box, float halfX, float halfZ, float snap, float edge) {
        return new float[] {onTopAlong(aimX, box[0], box[1], halfX, snap, edge), onTopAlong(aimZ, box[4], box[5], halfZ, snap, edge)};
    }

    private static float onTopAlong(float aim, float low, float high, float half, float snap, float edge) {
        float middle = (low + high) / 2.0F;
        float reach = (high - low) / 2.0F;
        if (Math.abs(aim - middle) <= snap * reach + 1.0e-4F) {
            return middle;
        }
        float in = edge * half;
        return low + in > high - in ? middle : Mth.clamp(aim, low + in, high - in);
    }

    /** Degrees off the level a thing's first side may be for its turn about the vertical to settle on a detent. */
    private static final float DETENT_LEVEL = 10.0F;

    /**
     * A turn by hand let go of: its turn about the vertical, within {@code detent} degrees of a multiple of {@code every} degrees,
     * settled onto it; for a thing standing upright or lying flat (its first side within 10 degrees of level) only, and
     * unchanged elsewhere.
     */
    public static Quaternionf detent(Quaternionfc rotation, float every, float detent) {
        Quaternionf turned = new Quaternionf(rotation);
        Vector3f side = turned.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        if (detent <= 0.0F || every <= 0.0F || Math.abs(side.y) > Math.sin(Math.toRadians(DETENT_LEVEL))) {
            return turned;
        }
        double yaw = Math.toDegrees(Math.atan2(-side.z, side.x));
        double nearest = Math.round(yaw / every) * every;
        double off = nearest - yaw;
        if (Math.abs(off) > detent) {
            return turned;
        }
        return new Quaternionf().rotateY((float) Math.toRadians(off)).mul(turned).normalize();
    }

    /** Pixels a dragged thing steps up onto what it is slid onto, at most; higher things it pushes. */
    public static final float SHOVE_STEP = 0.5F;

    /**
     * The level a thing dragged along its surface is held at: what it is over now ({@code ground}) where that is lower than
     * where it is ({@code level}), so it steps down past an edge; a hair higher where it still fits under {@code ceiling} with its
     * {@code height}; else where it is, so it slides across a top and pushes what stands higher in its way, never pressed into
     * what it is on.
     */
    public static float shoveLevel(float level, float ground, float height, float ceiling) {
        if (ground <= level) {
            return ground;
        }
        return ground - level <= SHOVE_STEP && ground + height <= ceiling ? ground : level;
    }

    /** GUI pixels the cursor may move off where it took an item before the item leaves its old place. */
    public static final float GRIP_STILL = 3.0F;

    /**
     * How much of the way from where the cursor took an item to where the item stood is still kept, by how far (GUI pixels) the
     * cursor has gone since: all of it within {@link #GRIP_STILL}, then less and less over {@code blend}, none after.
     */
    public static float gripShare(double travel, float blend) {
        if (travel < GRIP_STILL) {
            return 1.0F;
        }
        return blend <= 0.0F ? 0.0F : (float) Mth.clamp(1.0 - (travel - GRIP_STILL) / blend, 0.0, 1.0);
    }

    /**
     * The top of the pile the item {@code start} is in, as the hand sees it: from {@code start} up through each thing standing on
     * it ({@code on}) that a box of half size {@code halfX} by {@code halfZ} set on that one's top at {@code aim} (x, z; null: its
     * middle) would touch, to the highest. What stands beside the spot aimed at is passed by: the stack goes on the support there.
     *
     * @param poses the pose of each slot's item, null for none
     * @param boxes the box of each slot's item, as {@link #boxOf} gives it
     * @param aim   where the stack would stand (x, z) on the top of the thing in a slot; null for always its middle
     */
    public static int topOfPile(int start, int count, java.util.function.IntFunction<ItemPose> poses, java.util.function.IntFunction<float[]> boxes,
            java.util.function.IntFunction<float[]> aim, float halfX, float halfZ) {
        int at = start;
        for (int depth = 0; depth < PILE_MOST; depth++) {
            ItemPose base = poses.apply(at);
            if (base == null) {
                break;
            }
            float[] spot = aim == null ? null : aim.apply(at);
            float x = spot == null ? base.x() : spot[0];
            float z = spot == null ? base.z() : spot[1];
            int next = -1;
            float nearest = Float.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                ItemPose rider = i == at ? null : poses.apply(i);
                if (rider == null || rider.on() != at) {
                    continue;
                }
                float[] box = boxes.apply(i);
                if (box == null || x + halfX <= box[0] || x - halfX >= box[1] || z + halfZ <= box[4] || z - halfZ >= box[5]) {
                    continue;
                }
                float distance = (rider.x() - x) * (rider.x() - x) + (rider.z() - z) * (rider.z() - z);
                if (distance < nearest) {
                    nearest = distance;
                    next = i;
                }
            }
            if (next < 0) {
                break;
            }
            at = next;
        }
        return at;
    }

    /**
     * The surface an item standing at this pose belongs to, or null. Where the rooms of two surfaces overlap (a door's bin written
     * inside a shelf's room), the one whose floor is highest under the item: the one it stands on.
     */
    public static PlaceSurface surfaceOf(PlacingOptions options, ItemPose pose) {
        PlaceSurface found = null;
        for (PlaceSurface surface : options.surfaces()) {
            if (surface.holds(pose.x(), pose.y(), pose.z()) && (found == null || surface.height() > found.height())) {
                found = surface;
            }
        }
        return found;
    }

    /**
     * The surface of an item standing at {@code pose}: for one standing on another ({@code on}), that one's, down to the bottom
     * of the stack ({@code support} gives the pose of a slot). By its height alone, a thing on another near the front of a shelf
     * belonged to a door's bin written there, over the shelf's floor.
     */
    public static PlaceSurface surfaceOf(PlacingOptions options, ItemPose pose, java.util.function.IntFunction<ItemPose> support) {
        ItemPose at = pose;
        for (int depth = 0; at.on() >= 0 && depth < 32; depth++) {
            ItemPose under = support.apply(at.on());
            if (under == null) {
                break;
            }
            at = under;
        }
        return surfaceOf(options, at);
    }

    /**
     * The least size a thing of the size {@code wanted} the player gave it is made to stand on another: {@code stack_shrink} of
     * it, never under the smallest of {@code scale_range}.
     */
    public static float stackFloor(PlacingOptions options, float wanted) {
        return Math.max(options.scaleRange()[0], wanted * options.handling().stackShrink());
    }

    /** {@code item_size * scale} of an item, cut down so that it fits under what is above it. */
    public static float fittingScale(PlacingOptions options, Shape shape, Quaternionfc rotation, float scale, float room) {
        float height = shape.extents(rotation, options.itemSize() * scale).y * 2.0F;
        return height <= room ? scale : scale * room / height;
    }

    /** An item standing on {@code surface} at {@code x, z}, kept within its edges; at {@code y} over what it stands on. */
    public static ItemPose standing(PlacingOptions options, Shape shape, PlaceSurface surface, float x, float y, float z, Quaternionfc rotation,
            float wantedScale, int on) {
        // A hair under what is over it: made to fit, it never presses against the shelf above.
        float room = surface.height() + surface.clearance() - y - CEILING_SKIN;
        float scale = fittingScale(options, shape, rotation, wantedScale, room);
        Vector3f extents = shape.extents(rotation, options.itemSize() * scale);
        float keptX = keepWithin(snapped(options, x), surface.from().x(), surface.to().x(), extents.x);
        float keptZ = keepWithin(snapped(options, z), surface.from().y(), surface.to().y(), extents.z);
        return new ItemPose(keptX, y, keptZ, Math.max(extents.y, leastHalf(options, shape)), rotation, scale, on, wantedScale);
    }

    /** Half the least thickness a thing is reckoned with: a flat one's with physics (see {@code PlacedPhysics.flatHalf}), else 0. */
    public static float leastHalf(PlacingOptions options, Shape shape) {
        return shape.isFlat() ? options.physics().map(PlacedPhysics::flatHalf).orElse(0.0F) : 0.0F;
    }

    private static float keepWithin(float value, float low, float high, float half) {
        return low + half > high - half ? (low + high) / 2.0F : Mth.clamp(value, low + half, high - half);
    }

    private static float snapped(PlacingOptions options, float value) {
        return options.snap() <= 0.0F ? value : Math.round(value / options.snap()) * options.snap();
    }

    /**
     * How a thing is put down on {@code surface} unless the player turns it otherwise: a flat thing on a {@code flat_rest: lie}
     * surface lies on its back, top towards the far side; anything else as it comes.
     */
    public static Quaternionf baseTurn(PlaceSurface surface, Shape shape) {
        return baseTurn(surface, shape.isFlat());
    }

    /** As {@link #baseTurn(PlaceSurface, Shape)}, for a thing that is flat or not. */
    public static Quaternionf baseTurn(PlaceSurface surface, boolean flat) {
        return flat && surface.flatLies() ? new Quaternionf().rotateX(Mth.HALF_PI) : new Quaternionf();
    }

    /** A pile a thing set out by {@link #freeSpot} may go on: the top piece of same things lying on a surface that lies them. */
    public record PileTop(int slot, ItemPose pose) {
    }

    /** Pixels between an item set out by {@link #freeSpot} and its neighbours, and the step its search goes in. */
    private static final float SPOT_GAP = 0.25F;
    private static final float SPOT_STEP = 0.5F;
    /** How much smaller (of {@code scale_range}'s least) each try of {@link #freeSpot} makes an item that does not fit. */
    private static final float SPOT_SHRINK = 0.15F;

    /**
     * A free place for an item nobody put down by hand (shift-clicked in, brought by a hopper): the first spot on {@code surfaces}
     * where it stands clear of the boxes {@code taken} (each {min x, max x, min y, max y, min z, max z}), searched from the back of
     * each surface to the front, so the newest stands in front where it is seen, and along each row from its middle out, to the
     * side away from the camera first. At its full size where it fits, else smaller, down to the least of {@code scale_range}.
     * Null when there is no room left: the station is full.
     *
     * @param least   the least half width and depth the item takes (a flat icon's collision depth)
     * @param cameraX the camera's x in station pixels, or NaN when unknown
     */
    public static ItemPose freeSpot(PlacingOptions options, Shape shape, List<PlaceSurface> surfaces, List<float[]> taken, float least,
            float cameraX) {
        return freeSpot(options, shape, surfaces, taken, least, cameraX, List.of());
    }

    /**
     * As {@link #freeSpot(PlacingOptions, Shape, List, List, float, float)}; on a surface that lies flat things, the same
     * things go on the piles of them first ({@code piles}, the tops of such piles), as long as a pile stays under the clearance,
     * so a drawer of paper fills as stacks of paper.
     */
    public static ItemPose freeSpot(PlacingOptions options, Shape shape, List<PlaceSurface> surfaces, List<float[]> taken, float least,
            float cameraX, List<PileTop> piles) {
        for (PileTop pile : piles) {
            PlaceSurface surface = surfaceOf(options, pile.pose());
            Vector3f extents = shape.extents(pile.pose().rotation(), options.itemSize() * pile.pose().scale());
            float height = Math.max(extents.y, least) * 2.0F;
            if (surface != null && pile.pose().top() + height <= surface.height() + surface.clearance() + 1.0e-3F) {
                return new ItemPose(pile.pose().x(), pile.pose().top(), pile.pose().z(), height / 2.0F, pile.pose().rotation(), pile.pose().scale(),
                        pile.slot());
            }
        }
        float smallest = Math.min(1.0F, options.scaleRange()[0]);
        for (float scale = 1.0F; scale >= smallest - 1.0e-4F; scale -= SPOT_SHRINK) {
            for (PlaceSurface surface : surfaces) {
                Quaternionf upright = baseTurn(surface, shape);
                Vector3f extents = shape.extents(upright, options.itemSize() * scale);
                float halfX = Math.max(extents.x, least) + SPOT_GAP * 0.5F;
                float halfZ = Math.max(extents.z, least) + SPOT_GAP * 0.5F;
                float halfY = Math.max(extents.y, least);
                if (halfY * 2.0F > surface.clearance() + 1.0e-3F) {
                    continue;
                }
                float bottom = surface.height();
                float top = bottom + halfY * 2.0F;
                float lowX = surface.from().x() + halfX;
                float highX = surface.to().x() - halfX;
                if (lowX > highX + 1.0e-3F) {
                    continue;
                }
                float middle = (lowX + highX) / 2.0F;
                float farSide = !Float.isNaN(cameraX) && cameraX > middle ? -1.0F : 1.0F;
                if (surface.fillsFromMiddle()) {
                    ItemPose spot = middleOut(surface, taken, lowX, highX, halfX, halfY, halfZ, farSide, upright, scale);
                    if (spot != null) {
                        return spot;
                    }
                    continue;
                }
                int steps = (int) Math.floor((highX - lowX) / 2.0F / SPOT_STEP + 1.0e-3F);
                for (float z = surface.to().y() - halfZ; z >= surface.from().y() + halfZ - 1.0e-3F; z -= SPOT_STEP) {
                    // Each row from its middle out to both sides, the far side first: a camera from a front corner sees the
                    // middle and the far side, while the side nearest it hides behind that side's wall.
                    for (int step = 0; step <= steps * 2; step++) {
                        float x = middle + (step % 2 == 1 ? farSide : -farSide) * ((step + 1) / 2) * SPOT_STEP;
                        if (clear(taken, x - halfX, x + halfX, bottom, top, z - halfZ, z + halfZ)) {
                            return new ItemPose(x, bottom, z, halfY, upright, scale, -1);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * The free spot nearest the middle of a {@code fill: middle} surface (a plate): the middle itself while it is free, then the
     * spots round it, nearest first. Null when none is clear.
     */
    private static ItemPose middleOut(PlaceSurface surface, List<float[]> taken, float lowX, float highX, float halfX, float halfY,
            float halfZ, float farSide, Quaternionf upright, float scale) {
        float lowZ = surface.from().y() + halfZ;
        float highZ = surface.to().y() - halfZ;
        if (lowZ > highZ + 1.0e-3F) {
            return null;
        }
        float middleX = (lowX + highX) / 2.0F;
        float middleZ = (lowZ + highZ) / 2.0F;
        // Steps out from the middle, and the very edges: on a small plate the room beside a thing in the middle is often less
        // than a step.
        List<Float> xs = offsets(middleX, lowX, highX);
        List<Float> zs = offsets(middleZ, lowZ, highZ);
        List<float[]> spots = new java.util.ArrayList<>();
        for (float x : xs) {
            for (float z : zs) {
                spots.add(new float[] {x - middleX, z - middleZ});
            }
        }
        // Between spots as near: beside the middle before behind or in front of it, where a thing in the middle hides one or is
        // hidden by it; the far side from the camera first; then the back.
        spots.sort(java.util.Comparator.<float[]>comparingDouble(spot -> Math.round((spot[0] * spot[0] + spot[1] * spot[1]) * 64.0F))
                .thenComparingDouble(spot -> Math.abs(spot[1]))
                .thenComparingDouble(spot -> -Math.signum(spot[0]) * farSide)
                .thenComparingDouble(spot -> -spot[1]));
        float bottom = surface.height();
        float top = bottom + halfY * 2.0F;
        for (float[] spot : spots) {
            float x = middleX + spot[0];
            float z = middleZ + spot[1];
            if (clear(taken, x - halfX, x + halfX, bottom, top, z - halfZ, z + halfZ)) {
                return new ItemPose(x, bottom, z, halfY, upright, scale, -1);
            }
        }
        return null;
    }

    /** The middle, the steps out from it to both sides within {@code low..high}, and the two ends themselves. */
    private static List<Float> offsets(float middle, float low, float high) {
        List<Float> values = new java.util.ArrayList<>();
        values.add(middle);
        int steps = (int) Math.floor((high - low) / 2.0F / SPOT_STEP + 1.0e-3F);
        for (int i = 1; i <= steps; i++) {
            values.add(middle + i * SPOT_STEP);
            values.add(middle - i * SPOT_STEP);
        }
        if (high - low > 1.0e-3F) {
            values.add(high);
            values.add(low);
        }
        return values;
    }

    private static boolean clear(List<float[]> taken, float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        for (float[] box : taken) {
            if (box[1] > minX && box[0] < maxX && box[3] > minY + 0.02F && box[2] < maxY - 0.02F && box[5] > minZ && box[4] < maxZ) {
                return false;
            }
        }
        return true;
    }

    /** The box {min x, max x, min y, max y, min z, max z} an item takes at {@code pose}, at least {@code least} wide and deep. */
    public static float[] boxOf(PlacingOptions options, Shape shape, ItemPose pose, float least) {
        Vector3f extents = shape.extents(pose.rotation(), options.itemSize() * pose.scale());
        float halfX = Math.max(extents.x, least);
        float halfZ = Math.max(extents.z, least);
        float middle = pose.y() + pose.lift();
        return new float[] {pose.x() - halfX, pose.x() + halfX, middle - extents.y, middle + extents.y, pose.z() - halfZ, pose.z() + halfZ};
    }

    /**
     * A placed item as the cursor finds it: its hit box (the narrow box round a slanted icon's pixels, turned with it) in its own
     * frame, given as the move from the station's space into that frame; half its size there, exact and grown to the least the
     * cursor needs ({@code pick_min}); and the floor it stands on, in the space {@code toSurface} takes the station's into.
     */
    public record Pickable(org.joml.Matrix4fc toLocal, Vector3fc half, Vector3fc padded, org.joml.Matrix4fc toSurface, float floor) {
    }

    /** What a ray picked of a list of {@link Pickable}: its index and the ray parameter where it enters it. */
    public record Picked(int index, float distance) {
    }

    /** Points along the ray tried for how near it passes a box it missed. */
    private static final int GAP_SAMPLES = 8;

    /**
     * Which item a ray points at. The exact boxes first: the piece the cursor is really on wins. Only when it is on none, the boxes
     * grown to {@code pick_min} catch it (a lying sheet seen edge-on, the tip of a slanted icon), the one it passes nearest; a
     * grown box never reaches under the floor its item stands on. Nothing is picked from behind {@code before}, the ray
     * parameter of what else the ray hits first.
     */
    public static Picked pick(List<Pickable> items, Vector3fc origin, Vector3fc direction, float before) {
        Vector3f localOrigin = new Vector3f();
        Vector3f localDirection = new Vector3f();
        int best = -1;
        float bestT = before;
        for (int i = 0; i < items.size(); i++) {
            Pickable item = items.get(i);
            if (item == null) {
                continue;
            }
            item.toLocal().transformPosition(origin, localOrigin);
            item.toLocal().transformDirection(direction, localDirection);
            float[] span = through(item.half(), localOrigin, localDirection);
            if (span != null && span[0] < bestT) {
                best = i;
                bestT = span[0];
            }
        }
        if (best >= 0) {
            return new Picked(best, bestT);
        }
        float bestGap = Float.MAX_VALUE;
        Vector3f point = new Vector3f();
        for (int i = 0; i < items.size(); i++) {
            Pickable item = items.get(i);
            if (item == null) {
                continue;
            }
            item.toLocal().transformPosition(origin, localOrigin);
            item.toLocal().transformDirection(direction, localDirection);
            float[] span = through(item.padded(), localOrigin, localDirection);
            if (span == null || span[0] >= before) {
                continue;
            }
            point.set(direction).mul(span[0]).add(origin);
            if (item.toSurface() != null) {
                item.toSurface().transformPosition(point);
            }
            if (point.y < item.floor() - 0.01F) {
                // Come in from under the floor: the cursor is on the surface in front of the item, not on the item.
                continue;
            }
            float gap = Float.MAX_VALUE;
            for (int sample = 0; sample <= GAP_SAMPLES; sample++) {
                float t = span[0] + (span[1] - span[0]) * sample / GAP_SAMPLES;
                point.set(localDirection).mul(t).add(localOrigin);
                gap = Math.min(gap, outside(item.half(), point));
            }
            if (gap < bestGap - 1.0e-5F || gap < bestGap + 1.0e-5F && span[0] < bestT) {
                best = i;
                bestGap = gap;
                bestT = span[0];
            }
        }
        return best < 0 ? null : new Picked(best, bestT);
    }

    /** Where a ray enters and leaves the box of half size {@code half} round the origin, as ray parameters; null when it misses. */
    public static float[] through(Vector3fc half, Vector3fc origin, Vector3fc direction) {
        float tMin = 0.0F;
        float tMax = Float.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            float o = origin.get(axis);
            float d = direction.get(axis);
            float max = half.get(axis);
            if (Math.abs(d) < 1.0e-7F) {
                if (o < -max || o > max) {
                    return null;
                }
                continue;
            }
            float t1 = (-max - o) / d;
            float t2 = (max - o) / d;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) {
                return null;
            }
        }
        return new float[] {tMin, tMax};
    }

    /** How far {@code point} is outside the box of half size {@code half} round the origin; 0 inside. */
    private static float outside(Vector3fc half, Vector3fc point) {
        float x = Math.max(0.0F, Math.abs(point.x()) - half.x());
        float y = Math.max(0.0F, Math.abs(point.y()) - half.y());
        float z = Math.max(0.0F, Math.abs(point.z()) - half.z());
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * A place for an item nobody put down by hand: in rows along the surfaces, by its slot's number; only on the surfaces that are
     * not shut away ({@code hidden}, per surface; any when all are).
     */
    static ItemPose fallback(PlacingOptions options, int index, Shape shape, boolean[] hidden) {
        List<PlaceSurface> open = new java.util.ArrayList<>();
        for (int i = 0; i < options.surfaces().size(); i++) {
            if (i >= hidden.length || !hidden[i]) {
                open.add(options.surfaces().get(i));
            }
        }
        if (open.isEmpty()) {
            open = options.surfaces();
        }
        PlaceSurface surface = open.get(index % open.size());
        int place = index / open.size();
        float width = surface.to().x() - surface.from().x();
        int perRow = Math.max(1, (int) (width / options.itemSize()));
        float x = surface.from().x() + (place % perRow + 0.5F) * width / perRow;
        float z = (surface.from().y() + surface.to().y()) / 2.0F;
        return standing(options, shape, surface, x, surface.height(), z, baseTurn(surface, shape), 1.0F, -1);
    }
}
