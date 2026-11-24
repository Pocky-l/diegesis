package com.pockyl.diegesis.impl.client.render;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.impl.client.HiddenBlocks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Light where a block is left out of the picture: a block in the camera's way at a station ({@link ClearBlocks}) or one a scene
 * stands in for ({@link HiddenBlocks}). To the light engine it is still there, and a solid block has no light inside, while
 * the faces that look into it are now drawn: by the chunk meshes for its neighbours, and by a scene's copy of a block. Lit
 * with that darkness they came out black, at the station and all through the fade (a keg's front behind a wall in the
 * camera's way, the floor where the wall stood, the side of a keg beside a stove whose scene stands in for it).
 *
 * <p>Such places are lit as the light engine would light them if the blocks were gone: the open places that touch each other
 * are taken together, each gets the light of the blocks round the group less one a step into it, and the sky's straight down
 * as it is. What was worked out is kept a moment, for the many faces round the same places.
 */
public final class OpenLight {
    private static final Direction[] SIDES = Direction.values();
    private static final int FULL = 15;
    /** The most open places taken together: past it the rest of a group lends no light. */
    private static final int MOST_PLACES = 256;
    /** How long light worked out is kept, nanoseconds: the light round may change meanwhile (a torch set down). */
    private static final long KEPT_FOR = 500_000_000L;

    /** Light worked out for the open places as they are now, by layer and place; replaced when they change or it is old. */
    private record Known(Object cleared, int hidden, long until, ConcurrentHashMap<Long, Integer> block,
            ConcurrentHashMap<Long, Integer> sky) {
        ConcurrentHashMap<Long, Integer> of(LightLayer layer) {
            return layer == LightLayer.SKY ? sky : block;
        }
    }

    private static volatile Known known = new Known(null, 0, 0L, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

    private OpenLight() {
    }

    /** Whether the block at {@code pos} is left out of the picture: lit as open air. From any thread. */
    public static boolean isOpen(BlockPos pos) {
        return ClearBlocks.hides(pos) || HiddenBlocks.isHidden(pos);
    }

    /** {@link #isOpen(BlockPos)} by coordinates, for a mesh builder that reads every block of a section. */
    public static boolean isOpen(int x, int y, int z) {
        return ClearBlocks.hides(x, y, z) || HiddenBlocks.isHidden(x, y, z);
    }

    /**
     * The light of {@code layer} at an open place, from the blocks round its group in {@code level} (the world, or a mesh
     * builder's copy of it). From any thread.
     */
    public static int brightness(BlockAndTintGetter level, LightLayer layer, BlockPos pos) {
        Known now = current();
        ConcurrentHashMap<Long, Integer> values = now.of(layer);
        Integer value = values.get(pos.asLong());
        if (value == null) {
            spread(level, layer, pos, values);
            value = values.getOrDefault(pos.asLong(), 0);
        }
        return value;
    }

    /** What {@code LevelRenderer.getLightColor} gives an open place: its own glow kept, the light round it let in. */
    public static int color(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        if (state.emissiveRendering(level, pos)) {
            return LightTexture.FULL_BRIGHT;
        }
        int block = Math.max(brightness(level, LightLayer.BLOCK, pos), state.getLightEmission());
        return LightTexture.pack(block, brightness(level, LightLayer.SKY, pos));
    }

    private static Known current() {
        Known now = known;
        Object cleared = ClearBlocks.hiddenNow();
        int hidden = HiddenBlocks.changes();
        long time = Util.getNanos();
        if (now.cleared() != cleared || now.hidden() != hidden || time > now.until()) {
            now = new Known(cleared, hidden, time + KEPT_FOR, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
            known = now;
        }
        return now;
    }

    /** Works out the light of the group of open places {@code pos} is in, into {@code values}. */
    private static void spread(BlockAndTintGetter level, LightLayer layer, BlockPos pos, ConcurrentHashMap<Long, Integer> values) {
        LongArrayList places = new LongArrayList();
        LongOpenHashSet seen = new LongOpenHashSet();
        places.add(pos.asLong());
        seen.add(pos.asLong());
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (int i = 0; i < places.size() && places.size() < MOST_PLACES; i++) {
            BlockPos place = BlockPos.of(places.getLong(i));
            for (Direction side : SIDES) {
                next.setWithOffset(place, side);
                if (isOpen(next) && seen.add(next.asLong())) {
                    places.add(next.asLong());
                }
            }
        }
        // First the light the blocks round the group give each place, then a step into the group at a time.
        Long2IntOpenHashMap light = new Long2IntOpenHashMap();
        for (int i = 0; i < places.size(); i++) {
            BlockPos place = BlockPos.of(places.getLong(i));
            int most = 0;
            for (Direction side : SIDES) {
                next.setWithOffset(place, side);
                if (seen.contains(next.asLong()) || isOpen(next)) {
                    continue;
                }
                int around = level.getBrightness(layer, next);
                most = Math.max(most, layer == LightLayer.SKY && side == Direction.UP && around == FULL ? FULL : around - 1);
            }
            light.put(places.getLong(i), most);
        }
        boolean changed = true;
        for (int step = 0; changed && step < FULL; step++) {
            changed = false;
            for (int i = 0; i < places.size(); i++) {
                long place = places.getLong(i);
                int most = light.get(place);
                for (Direction side : SIDES) {
                    long neighbour = BlockPos.offset(place, side);
                    if (!light.containsKey(neighbour)) {
                        continue;
                    }
                    int there = light.get(neighbour);
                    most = Math.max(most, layer == LightLayer.SKY && side == Direction.UP && there == FULL ? FULL : there - 1);
                }
                if (most > light.get(place)) {
                    light.put(place, most);
                    changed = true;
                }
            }
        }
        for (Long2IntMap.Entry entry : light.long2IntEntrySet()) {
            values.put(entry.getLongKey(), Math.max(0, entry.getIntValue()));
        }
    }
}
