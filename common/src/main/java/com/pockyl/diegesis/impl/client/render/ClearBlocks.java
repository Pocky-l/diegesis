package com.pockyl.diegesis.impl.client.render;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.CameraGlance;
import com.pockyl.diegesis.api.scene.CameraRig;
import com.pockyl.diegesis.api.scene.CameraShot;
import com.pockyl.diegesis.api.scene.ClearViewOptions;
import com.pockyl.diegesis.impl.client.ClientStation;

/**
 * Leaves blocks in the camera's way out of the picture while a player works at a station ({@link ClearViewOptions#blocks()}): the
 * blocks the camera stands in, from any shot of the scene, and the blocks between it and the station's work surface, such as a
 * wall cabinet over a counter or a low ceiling. Chunk meshes are built as if those blocks were air (the game's, and Embeddium's or
 * Sodium's through their world slice), so the faces of their neighbours show and nothing is left hollow; light stays the world's,
 * so the station keeps the shade a cabinet gives it. Nobody else sees a change.
 *
 * <p>The shot itself never moves: what a scene hangs before the camera (a recipe book) stays where it was framed, stations side by
 * side keep their shots, and under a cabinet the camera still looks into a pot, which no lower angle does.
 */
public final class ClearBlocks {
    /** Room round the camera that must be free, so the near plane cuts into no block; more than vanilla's 0.1 for the flight. */
    private static final double ROOM = 0.3;
    /** Seconds between checks: a block placed or broken meanwhile. */
    private static final float RECHECK = 0.5F;
    /** Where the work surface is checked across the block, pixels from its side. */
    private static final float[] GRID = {2.0F, 8.0F, 14.0F};
    /** How high things lying on the surface stand, pixels: their tops must show too. */
    private static final float RISE = 4.0F;
    private static final LongOpenHashSet NONE = new LongOpenHashSet();

    /** The blocks left out; never changed once published, as the chunk builders read it on their own threads. */
    private static volatile LongOpenHashSet hidden = NONE;
    private static float sinceCheck;
    private static ClientStation checkedStation;
    private static ClientStation checkedLeaving;

    private ClearBlocks() {
    }

    /** Whether the chunk meshes leave out the block at {@code pos}. */
    public static boolean hides(BlockPos pos) {
        LongOpenHashSet set = hidden;
        return !set.isEmpty() && set.contains(pos.asLong());
    }

    /** Whether the chunk meshes leave out the block at {@code x y z}. */
    public static boolean hides(int x, int y, int z) {
        LongOpenHashSet set = hidden;
        return !set.isEmpty() && set.contains(BlockPos.asLong(x, y, z));
    }

    /**
     * Keeps the way clear for the station worked at, once a frame while the camera is at it or flying to or from it.
     *
     * @param leaving the station the camera flies over from, whose blocks stay out until it arrives; or null
     */
    public static void keep(ClientStation station, ClientStation leaving, float dt) {
        sinceCheck += dt;
        if (station == checkedStation && leaving == checkedLeaving && sinceCheck < RECHECK) {
            return;
        }
        sinceCheck = 0.0F;
        checkedStation = station;
        checkedLeaving = leaving;
        ClientLevel level = Minecraft.getInstance().level;
        LongOpenHashSet found = new LongOpenHashSet();
        if (level != null) {
            collect(level, station, found);
            collect(level, leaving, found);
        }
        publish(level, found);
    }

    /** Puts every block back. */
    public static void clear() {
        checkedStation = null;
        checkedLeaving = null;
        publish(Minecraft.getInstance().level, NONE);
    }

    private static void publish(ClientLevel level, LongOpenHashSet found) {
        LongOpenHashSet before = hidden;
        if (before.equals(found)) {
            return;
        }
        hidden = found.isEmpty() ? NONE : found;
        if (level == null) {
            return;
        }
        // Rebuilt at once where the loader can (Sodium and Embeddium rebuild important sections in the frame).
        rebuildChanged(level, before, found);
        rebuildChanged(level, found, before);
    }

    private static void rebuildChanged(ClientLevel level, LongOpenHashSet from, LongOpenHashSet other) {
        for (LongIterator it = from.iterator(); it.hasNext(); ) {
            long packed = it.nextLong();
            if (!other.contains(packed)) {
                BlockPos pos = BlockPos.of(packed);
                BlockState state = level.getBlockState(pos);
                Minecraft.getInstance().levelRenderer.blockChanged(level, pos, state, state, 8);
            }
        }
    }

    private static void collect(ClientLevel level, ClientStation station, LongOpenHashSet found) {
        if (station == null || station.scene() == null) {
            return;
        }
        if (!station.scene().definition().clearView().blocks()) {
            return;
        }
        CameraRig rig = station.scene().definition().camera();
        CollisionContext context = CollisionContext.empty();
        shot(level, station, rig.position(), rig.target(), context, found);
        for (CameraShot seat : rig.seats().values()) {
            shot(level, station, seat.position(), seat.target(), context, found);
        }
        rig.inventory().ifPresent(inventory -> shot(level, station, inventory.position(), inventory.target(), context, found));
        for (CameraGlance glance : rig.glances()) {
            shot(level, station, glance.position(), glance.target(), context, found);
        }
    }

    /** What is in the way of one shot: blocks round the camera, and between it and the work surface round the target. */
    private static void shot(ClientLevel level, ClientStation station, Vector3fc position, Vector3fc target, CollisionContext context,
            LongOpenHashSet found) {
        Vec3 camera = station.toWorld(position.x(), position.y(), position.z());
        BlockPos own = station.pos();
        AABB room = new AABB(camera, camera).inflate(ROOM);
        VoxelShape roomShape = Shapes.create(room);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
            if (!pos.equals(own)) {
                VoxelShape shape = level.getBlockState(pos).getVisualShape(level, pos, context);
                if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()), roomShape, BooleanOp.AND)) {
                    found.add(pos.asLong());
                }
            }
        }
        sight(level, own, camera, station.toWorld(target.x(), target.y(), target.z()), context, found);
        for (float rise : new float[] {0.0F, RISE}) {
            for (float x : GRID) {
                for (float z : GRID) {
                    sight(level, own, camera, station.toWorld(x, target.y() + rise, z), context, found);
                }
            }
        }
    }

    /** Every block whose visual shape the line from {@code from} to {@code to} passes through. */
    private static void sight(ClientLevel level, BlockPos own, Vec3 from, Vec3 to, CollisionContext context, LongOpenHashSet found) {
        BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> {
            if (!pos.equals(own)) {
                VoxelShape shape = level.getBlockState(pos).getVisualShape(level, pos, ctx);
                if (!shape.isEmpty() && shape.clip(from, to, pos) != null) {
                    found.add(pos.asLong());
                }
            }
            return null;
        }, ctx -> null);
    }
}
