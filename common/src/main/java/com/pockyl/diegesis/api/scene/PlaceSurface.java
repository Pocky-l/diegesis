package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector2f;
import org.joml.Vector2fc;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A level surface items can stand on.
 *
 * @param from      one corner, {@code [x, z]} in scene pixels
 * @param to        the opposite corner
 * @param height    its height in scene pixels
 * @param clearance how much room there is above it, in pixels: nothing that stands here grows through what is over it
 * @param aimHeight how the cursor finds a place. 0: it points at the surface itself, right for a table seen from above.
 *                  A shelf seen from the front is too thin a sliver for that: with a height, the cursor points at a slope
 *                  from the front edge (the side of the smaller z) up to this height over the back edge, so that low in
 *                  the opening means at the front and high in the opening means at the back
 * @param wall      the z of a wall behind the surface: light in the room falls on it too
 * @param open      the sides with no wall, for {@code placing.physics}: a thing pushed over such an edge falls off, onto a
 *                  surface below or, with none there, out of the station into its player's inventory. Every side has a wall
 *                  by default
 * @param headroom  with {@code placing.physics}: how high over the surface the stack in the hand may be lifted to pass over
 *                  what stands there (and things stacked); {@link #clearance} when not given. More than the clearance where
 *                  open air is over the surface (a drawer pulled out), less to keep the hand low
 * @param flatRest  how a flat thing (an icon: bread, paper) is put down here unless the player turns it otherwise:
 *                  {@code stand}, standing facing the front, as on a shelf seen from the front; {@code lie}, lying on its back
 *                  with its top towards the far side, as in a drawer or box seen from above, where it reads whole and piles up
 * @param fill      where a thing that comes without a place (shift-clicked in, brought by a hopper) is set out here: {@code back},
 *                  from the back row to the front, each row from its middle out, as on a shelf; {@code middle}, as near the
 *                  middle of the surface as there is room, then outwards round it, as on a plate or a turntable
 */
public record PlaceSurface(Vector2fc from, Vector2fc to, float height, float clearance, float aimHeight, Optional<Float> wall,
        Set<Side> open, Optional<Float> headroom, FlatRest flatRest, Fill fill) {
    /** Where a thing that comes without a place is set out on a surface. */
    public enum Fill implements StringRepresentable {
        BACK, MIDDLE;

        public static final Codec<Fill> CODEC = StringRepresentable.fromEnum(Fill::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** How a flat thing is put down on a surface. */
    public enum FlatRest implements StringRepresentable {
        STAND, LIE;

        public static final Codec<FlatRest> CODEC = StringRepresentable.fromEnum(FlatRest::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** A side of a surface, as the scene's front (-z) sees it: left is +x, the viewer's left. */
    public enum Side implements StringRepresentable {
        FRONT, BACK, LEFT, RIGHT;

        public static final Codec<Side> CODEC = StringRepresentable.fromEnum(Side::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final Codec<Vector2fc> XZ = Codec.FLOAT.listOf().comapFlatMap(
            list -> list.size() == 2 ? DataResult.success((Vector2fc) new Vector2f(list.get(0), list.get(1)))
                    : DataResult.error(() -> "Expected [x, z], got " + list),
            vector -> List.of(vector.x(), vector.y()));

    public static final Codec<PlaceSurface> CODEC = StrictCodecs.record(instance -> instance.group(
            XZ.fieldOf("from").forGetter(PlaceSurface::from),
            XZ.fieldOf("to").forGetter(PlaceSurface::to),
            Codec.FLOAT.fieldOf("height").forGetter(PlaceSurface::height),
            StrictCodecs.optional(Codec.floatRange(0.5F, 256.0F), "clearance", 64.0F).forGetter(PlaceSurface::clearance),
            StrictCodecs.optional(Codec.floatRange(0.0F, 256.0F), "aim_height", 0.0F).forGetter(PlaceSurface::aimHeight),
            StrictCodecs.optional(Codec.FLOAT, "wall").forGetter(PlaceSurface::wall),
            StrictCodecs.optional(Side.CODEC.listOf().<Set<Side>>xmap(Set::copyOf, List::copyOf), "open", Set.<Side>of())
                    .forGetter(PlaceSurface::open),
            StrictCodecs.optional(Codec.floatRange(0.0F, 256.0F), "headroom").forGetter(PlaceSurface::headroom),
            StrictCodecs.optional(FlatRest.CODEC, "flat_rest", FlatRest.STAND).forGetter(PlaceSurface::flatRest),
            StrictCodecs.optional(Fill.CODEC, "fill", Fill.BACK).forGetter(PlaceSurface::fill)
    ).apply(instance, PlaceSurface::new));

    /** A surface walled on every side. */
    public PlaceSurface(Vector2fc from, Vector2fc to, float height, float clearance, float aimHeight, Optional<Float> wall) {
        this(from, to, height, clearance, aimHeight, wall, Set.of());
    }

    /** A surface with the hand's headroom its clearance. */
    public PlaceSurface(Vector2fc from, Vector2fc to, float height, float clearance, float aimHeight, Optional<Float> wall, Set<Side> open) {
        this(from, to, height, clearance, aimHeight, wall, open, Optional.empty());
    }

    /** A surface where flat things stand. */
    public PlaceSurface(Vector2fc from, Vector2fc to, float height, float clearance, float aimHeight, Optional<Float> wall, Set<Side> open,
            Optional<Float> headroom) {
        this(from, to, height, clearance, aimHeight, wall, open, headroom, FlatRest.STAND);
    }

    /** A surface where things without a place are set out from the back. */
    public PlaceSurface(Vector2fc from, Vector2fc to, float height, float clearance, float aimHeight, Optional<Float> wall, Set<Side> open,
            Optional<Float> headroom, FlatRest flatRest) {
        this(from, to, height, clearance, aimHeight, wall, open, headroom, flatRest, Fill.BACK);
    }

    /** Whether things without a place are set out from the middle ({@code fill: middle}). */
    public boolean fillsFromMiddle() {
        return fill == Fill.MIDDLE;
    }

    /** Whether flat things lie here ({@code flat_rest: lie}). */
    public boolean flatLies() {
        return flatRest == FlatRest.LIE;
    }

    /** How high over the surface the stack in the hand may be lifted ({@link #headroom}, else the clearance). */
    public float liftRoom() {
        return headroom.orElse(clearance);
    }

    public PlaceSurface {
        open = open.isEmpty() ? Set.of() : java.util.Collections.unmodifiableSet(java.util.EnumSet.copyOf(open));
        Vector2f low = new Vector2f(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()));
        Vector2f high = new Vector2f(Math.max(from.x(), to.x()), Math.max(from.y(), to.y()));
        from = low;
        to = high;
    }

    /** Whether {@code x, z} lies on the surface and the height {@code y} in the room above it. */
    public boolean holds(float x, float y, float z) {
        return x >= from.x() - 0.01F && x <= to.x() + 0.01F && z >= from.y() - 0.01F && z <= to.y() + 0.01F
                && y >= height - 0.01F && y <= height + clearance;
    }
}
