package com.pockyl.diegesis.neoforge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4d;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;

import com.pockyl.diegesis.impl.SubLevels;

/**
 * The only class that touches Sable, through the companion API it bundles: a station on a Sable structure (a Create
 * Aeronautics ship) is drawn, framed and reached where the structure is. Installed only when Sable is there.
 */
public final class SableSubLevels implements SubLevels.Bridge {
    private SableSubLevels() {
    }

    public static void install() {
        SubLevels.install(new SableSubLevels());
    }

    @Override
    public boolean pose(Level level, BlockPos pos, boolean render, Matrix4d dest) {
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sub == null) {
            return false;
        }
        Pose3dc pose = render && sub instanceof ClientSubLevelAccess client ? client.renderPose() : sub.logicalPose();
        pose.bakeIntoMatrix(dest);
        return true;
    }
}
