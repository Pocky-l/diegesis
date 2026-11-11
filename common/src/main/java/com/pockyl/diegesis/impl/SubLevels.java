package com.pockyl.diegesis.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import com.pockyl.diegesis.DiegesisMod;

/**
 * Blocks that move: physics mods such as Sable (Create Aeronautics' ships) keep a moving structure's blocks in a far area of the
 * same level and draw them where the structure is. A station on one has its block there, so every distance to it and every
 * place drawn or heard at it goes through here: from the level's coordinates of its block to where it is in the world. Without
 * such a mod both are the same.
 */
public final class SubLevels {
    /** What a loader plugs in when a mod with moving structures is there. */
    public interface Bridge {
        /**
         * Sets {@code dest} to the structure's pose, from the level's coordinates of its blocks to the world's, when {@code pos} is
         * on a moving structure; false when it is not. {@code render}: the pose drawn this frame (client), else the logical one.
         */
        boolean pose(Level level, BlockPos pos, boolean render, Matrix4d dest);
    }

    private static volatile Bridge bridge;

    private SubLevels() {
    }

    public static void install(Bridge moving) {
        bridge = moving;
    }

    /**
     * {@code dest} set to the matrix from block-relative coordinates of {@code pos} (0 to 1 over the block) to the world's: the
     * block's corner, carried by the structure it is on.
     */
    public static Matrix4d blockToWorld(Level level, BlockPos pos, boolean render, Matrix4d dest) {
        if (!pose(level, pos, render, dest)) {
            dest.identity();
        }
        return dest.translate(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Whether {@code pos} is on a moving structure. */
    public static boolean moves(Level level, BlockPos pos) {
        return pose(level, pos, false, new Matrix4d());
    }

    /** Where {@code point}, in the level's coordinates near {@code pos}, is in the world. */
    public static Vec3 toWorld(Level level, BlockPos pos, Vec3 point, boolean render) {
        Matrix4d pose = new Matrix4d();
        if (!pose(level, pos, render, pose)) {
            return point;
        }
        Vector3d at = pose.transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(at.x, at.y, at.z);
    }

    /** Where the world's {@code point} is in the level's coordinates of the structure {@code pos} is on. */
    public static Vec3 toLevel(Level level, BlockPos pos, Vec3 point, boolean render) {
        Matrix4d pose = new Matrix4d();
        if (!pose(level, pos, render, pose)) {
            return point;
        }
        Vector3d at = pose.invert().transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(at.x, at.y, at.z);
    }

    /** The world's centre of the block at {@code pos}. */
    public static Vec3 center(Level level, BlockPos pos, boolean render) {
        return toWorld(level, pos, pos.getCenter(), render);
    }

    /** Squared distance from the world's {@code point} to the centre of the block at {@code pos}, wherever it is. */
    public static double distanceSqr(Level level, BlockPos pos, Vec3 point) {
        return center(level, pos, false).distanceToSqr(point);
    }

    private static boolean pose(Level level, BlockPos pos, boolean render, Matrix4d dest) {
        Bridge moving = bridge;
        if (moving == null || level == null) {
            return false;
        }
        try {
            return moving.pose(level, pos, render, dest);
        } catch (Throwable broken) {
            // A changed API of the other mod must never break stations: they work as on the ground from then on.
            bridge = null;
            DiegesisMod.LOGGER.warn("Stations on moving structures are off: {}", broken.toString());
            return false;
        }
    }
}
