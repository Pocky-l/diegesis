package com.pockyl.diegesis.impl.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;

import com.pockyl.diegesis.impl.client.HiddenBlocks;

/**
 * The light of a place the game's meshes and the scenes treat as open while the light engine still holds a block there: a
 * block in the camera's way at a station ({@link ClearBlocks}) or a station block a scene stands in for ({@link HiddenBlocks}).
 * Inside a solid block the engine keeps no light, so the faces of the blocks around that look into it, built now that it no
 * longer hides them, came out black: through the fading wall, at the station, and in the gaps of the scene's copy.
 *
 * <p>Such a place is lit as the engine would light it as air: per layer, the most of its own light and of each neighbour's
 * less one, where sky light from straight above at its full strength stays full. Neighbours that are open themselves hold
 * no light of their own and count for nothing.
 */
public final class OpenLight {
    private static final int FULL = 15;

    private OpenLight() {
    }

    /** Whether the chunk meshes treat the block at {@code pos} as gone. */
    public static boolean isOpen(BlockPos pos) {
        return ClearBlocks.hides(pos) || HiddenBlocks.isHidden(pos);
    }

    /** The light of {@code layer} at {@code pos}, read from {@code level}, as if the block there were air. */
    public static int brightness(BlockAndTintGetter level, LightLayer layer, BlockPos pos) {
        int light = level.getBrightness(layer, pos);
        BlockPos.MutableBlockPos around = new BlockPos.MutableBlockPos();
        for (Direction side : Direction.values()) {
            around.setWithOffset(pos, side);
            if (isOpen(around)) {
                continue;
            }
            int there = level.getBrightness(layer, around);
            light = Math.max(light, layer == LightLayer.SKY && side == Direction.UP && there == FULL ? FULL : there - 1);
        }
        return Math.max(0, light);
    }
}
