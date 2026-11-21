package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

/**
 * Keeps the view of the station free while a player works at it: other players, mobs, dropped items and world particles between
 * the camera and the station dissolve, softly towards the edge of a cone round the line of sight, and come back as the camera
 * leaves; blocks in the camera's way are left out of the picture. Nothing changes for anybody else. Written as
 * {@code "clear_view"} in a scene file; every field is optional.
 *
 * @param entities  entities dissolve
 * @param particles the world's particles (a campfire's smoke) dissolve; the scene's own never do
 * @param radius    how wide the clear cone is at the station, in pixels
 * @param soft      how far beyond {@code radius} things come back to full strength, in pixels
 * @param blocks    blocks the camera stands in, or that stand between it and the station's work surface (a wall cabinet over the
 *                  station, a low ceiling), are not drawn while the player works here, and come back when the camera has flown
 *                  back. The shot stays as the scene has it. Blocks count by their visual shape, as for the vanilla third-person
 *                  camera (glass never does); the station's own block always stays
 * @param surface   what {@code blocks} keeps in view: {@code block}, the block's middle at the height the camera looks at (the
 *                  default); {@code placing}, the scene's {@code placing} surfaces where they are written, each on its own. A
 *                  drawer pulled out before its block is worked on there, not over the block: counted over the block, the block
 *                  on top of it, seen past the drawer's own front, went from the picture though it hid nothing
 */
public record ClearViewOptions(boolean entities, boolean particles, float radius, float soft, boolean blocks, Surface surface) {
    public static final ClearViewOptions DEFAULT = new ClearViewOptions(true, true, 16.0F, 12.0F, true, Surface.BLOCK);
    public static final ClearViewOptions OFF = new ClearViewOptions(false, false, 0.0F, 1.0F, false, Surface.BLOCK);

    /** Which work surface blocks must leave in view. */
    public enum Surface implements StringRepresentable {
        /** The middle of the station's block, at the height of the camera's target. */
        BLOCK,
        /** The scene's placing surfaces; the block's middle when the scene has none. */
        PLACING;

        public static final Codec<Surface> CODEC = StringRepresentable.fromEnum(Surface::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Codec<ClearViewOptions> CODEC = StrictCodecs.record(instance -> instance.group(
            StrictCodecs.optional(Codec.BOOL, "entities", DEFAULT.entities).forGetter(ClearViewOptions::entities),
            StrictCodecs.optional(Codec.BOOL, "particles", DEFAULT.particles).forGetter(ClearViewOptions::particles),
            StrictCodecs.optional(Codec.floatRange(0.0F, 256.0F), "radius", DEFAULT.radius).forGetter(ClearViewOptions::radius),
            StrictCodecs.optional(Codec.floatRange(1.0F, 256.0F), "soft", DEFAULT.soft).forGetter(ClearViewOptions::soft),
            StrictCodecs.optional(Codec.BOOL, "blocks", DEFAULT.blocks).forGetter(ClearViewOptions::blocks),
            StrictCodecs.optional(Surface.CODEC, "surface", DEFAULT.surface).forGetter(ClearViewOptions::surface)
    ).apply(instance, ClearViewOptions::new));

    /** Options that leave blocks in the camera's way out of the picture, as by default. */
    public ClearViewOptions(boolean entities, boolean particles, float radius, float soft) {
        this(entities, particles, radius, soft, DEFAULT.blocks);
    }

    /** Options that keep the block's middle in view, as by default. */
    public ClearViewOptions(boolean entities, boolean particles, float radius, float soft, boolean blocks) {
        this(entities, particles, radius, soft, blocks, DEFAULT.surface);
    }

    /** The same options, leaving blocks in the way out of the picture or not. */
    public ClearViewOptions withBlocks(boolean value) {
        return new ClearViewOptions(entities, particles, radius, soft, value, surface);
    }

    /** The same options, keeping another work surface in view. */
    public ClearViewOptions withSurface(Surface value) {
        return new ClearViewOptions(entities, particles, radius, soft, blocks, value);
    }
}
