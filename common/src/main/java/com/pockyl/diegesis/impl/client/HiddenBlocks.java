package com.pockyl.diegesis.impl.client;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The blocks whose own model is not drawn now, because a scene shows them instead ({@code StationType.Builder.hidesBlock}).
 * The models of such blocks are wrapped when they are baked; the wrappers ask here, on the threads that build the chunks.
 */
public final class HiddenBlocks {
    private static final Set<BlockPos> HIDDEN = ConcurrentHashMap.newKeySet();
    /**
     * {@link #HIDDEN} packed, never changed once published: Sodium's mesh builder asks for every block of a section by its
     * coordinates, and went through every hidden block for each.
     */
    private static volatile LongOpenHashSet hiddenPacked = new LongOpenHashSet();
    /**
     * Blocks shown again whose chunk is not built with them yet: chunks are built on other threads and come a few frames later, so
     * the scene keeps standing in for such a block until its own model has been built and put on screen.
     */
    private static final Map<BlockPos, Showing> SHOWING = new ConcurrentHashMap<>();
    /** Frames the scene stays after the block's model was built: the chunk is put on screen at the start of a later frame. */
    private static final int FRAMES_AFTER_BUILT = 2;
    /** The most frames the scene waits, for a renderer whose builder this never hears: then the two overlap a moment instead. */
    private static final int MOST_FRAMES = 30;

    private static final class Showing {
        private volatile boolean built;
        private int frames;
        private int sinceBuilt;
    }

    private HiddenBlocks() {
    }

    public static boolean isHidden(BlockPos pos) {
        LongOpenHashSet packed = hiddenPacked;
        return !packed.isEmpty() && packed.contains(pos.asLong());
    }

    /**
     * Whether a scene draws the block now in its place: while it is hidden, and after that until the chunk shows the block's own
     * model again, so the two never leave a frame with neither.
     */
    public static boolean isStoodIn(BlockPos pos) {
        return isHidden(pos) || !SHOWING.isEmpty() && SHOWING.containsKey(pos);
    }

    /** A chunk builder builds the block's own model at {@code pos} (it is not hidden); from any thread. */
    public static void built(BlockPos pos) {
        if (!SHOWING.isEmpty()) {
            Showing showing = SHOWING.get(pos);
            if (showing != null) {
                showing.built = true;
            }
        }
    }

    /** {@link #built(BlockPos)} by coordinates, for a mesh builder that reads every block of a section: no position is made. */
    public static void built(int x, int y, int z) {
        if (SHOWING.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, Showing> entry : SHOWING.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                entry.getValue().built = true;
            }
        }
    }

    /** Once a frame, on the render thread: a block whose own model is on screen again needs its scene no longer. */
    public static void frame() {
        if (SHOWING.isEmpty()) {
            return;
        }
        SHOWING.values().removeIf(showing -> {
            showing.frames++;
            if (showing.built) {
                showing.sinceBuilt++;
            }
            return showing.sinceBuilt > FRAMES_AFTER_BUILT || showing.frames > MOST_FRAMES;
        });
    }

    /** {@link #isHidden(BlockPos)} by coordinates, for a mesh builder that reads every block of a section: no position is made. */
    public static boolean isHidden(int x, int y, int z) {
        LongOpenHashSet packed = hiddenPacked;
        return !packed.isEmpty() && packed.contains(BlockPos.asLong(x, y, z));
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
        for (BlockPos pos : changed) {
            if (wanted.contains(pos)) {
                SHOWING.remove(pos);
            } else {
                SHOWING.put(pos, new Showing());
            }
        }
        HIDDEN.retainAll(wanted);
        HIDDEN.addAll(wanted);
        LongOpenHashSet packed = new LongOpenHashSet(wanted.size());
        wanted.forEach(pos -> packed.add(pos.asLong()));
        hiddenPacked = packed;
        Minecraft minecraft = Minecraft.getInstance();
        for (BlockPos pos : changed) {
            if (minecraft.level != null) {
                BlockState state = minecraft.level.getBlockState(pos);
                minecraft.levelRenderer.blockChanged(minecraft.level, pos, state, state, Block.UPDATE_IMMEDIATE);
            }
        }
    }
}
