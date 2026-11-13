package com.pockyl.diegesis.impl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The blocks whose own model is not drawn now, because a scene shows them instead ({@code StationType.Builder.hidesBlock}).
 * The models of such blocks are wrapped when they are baked; the wrappers ask here, on the threads that build the chunks.
 */
public final class HiddenBlocks {
    private static final Set<BlockPos> HIDDEN = ConcurrentHashMap.newKeySet();

    private HiddenBlocks() {
    }

    public static boolean isHidden(BlockPos pos) {
        return !HIDDEN.isEmpty() && HIDDEN.contains(pos);
    }

    /** {@link #isHidden(BlockPos)} by coordinates, for a mesh builder that reads every block of a section: no position is made. */
    public static boolean isHidden(int x, int y, int z) {
        if (HIDDEN.isEmpty()) {
            return false;
        }
        for (BlockPos pos : HIDDEN) {
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    /**
     * Makes {@code wanted} the hidden blocks and rebuilds the chunks of those that changed on the main thread at the start of the
     * next frame, as the game does for a block a player places: the scene that stands in for a block, which knows this from
     * {@link #isHidden}, and the block itself never show both or neither.
     */
    static void sync(Set<BlockPos> wanted) {
        if (HIDDEN.equals(wanted)) {
            return;
        }
        Set<BlockPos> changed = new HashSet<>(HIDDEN);
        changed.addAll(wanted);
        changed.removeIf(pos -> HIDDEN.contains(pos) && wanted.contains(pos));
        HIDDEN.retainAll(wanted);
        HIDDEN.addAll(wanted);
        Minecraft minecraft = Minecraft.getInstance();
        for (BlockPos pos : changed) {
            if (minecraft.level != null) {
                BlockState state = minecraft.level.getBlockState(pos);
                minecraft.levelRenderer.blockChanged(minecraft.level, pos, state, state, Block.UPDATE_IMMEDIATE);
            }
        }
    }
}
