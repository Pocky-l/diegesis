package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

/**
 * A thick mass in a tank instead of a runny liquid: a batter, a porridge, a dough, whipped butter, ice cream. Its surface has relief.
 * Whatever moves through it ({@code NodeView.leaveWake}) pushes it aside into a furrow with a ridge at each side, and it flows back as
 * firmly as it is made: a batter levels in a second or two, a porridge keeps the furrows, ice cream keeps its peaks. A swirl
 * ({@code NodeView.setCurrent}) carries the relief and the texture round, and what floats in it ({@code NodeView.setFloating}) is held
 * in it, partly sunk. It is lit by the scene's light, glossy or matte, and rises a little where it meets the walls. A tank shows it
 * from the scene file ({@link TankBinding#mass}) or from code ({@code NodeView.setMass}); {@code NodeView.setThickness} lets a liquid
 * turn into it.
 *
 * @param texture  a block-atlas sprite with the mass's own colours, drawn without light or shade (the relief makes those), repeated
 *                 over the surface every {@code tile} pixels from the tank's middle
 * @param tint     an RGB colour multiplied into the texture, {@code #FFFFFF} for the texture as drawn
 * @param firmness how well it keeps its shape, 0 to 1: 0 a batter that levels in about a second, 0.5 a porridge that keeps a spoon's
 *                 furrows, 1 ice cream or whipped butter that keep peaks and swirls
 * @param gloss    how glossy it is, 0 (dough, mince) to 1 (butter, ice cream): soft highlights on the ridges turned to the light
 * @param grain    how lumpy it is at a small scale, 0 (smooth) to 1 (oat flakes, crumbs, chopped leaves)
 * @param tile     how many pixels one repeat of the texture covers: 16 is one texel a pixel, the density of the scene's models
 * @param cell     how many pixels one cell of its surface covers, the finest step its relief bends at; 0 for one texel of its texture
 *                 ({@code tile / 16}), so that the relief is as coarse as the pixels drawn on it
 */
public record MassLook(ResourceLocation texture, int tint, float firmness, float gloss, float grain, float tile, float cell) {
    public static final float DEFAULT_FIRMNESS = 0.5F;
    public static final float DEFAULT_GLOSS = 0.3F;
    public static final float DEFAULT_GRAIN = 0.3F;
    public static final float DEFAULT_TILE = 16.0F;
    /** One texel of the texture. */
    public static final float TEXEL_CELL = 0.0F;

    public static final Codec<MassLook> CODEC = StrictCodecs.record(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(MassLook::texture),
            StrictCodecs.optional(Visual.COLOR, "tint", Visual.WHITE).forGetter(MassLook::tint),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "firmness", DEFAULT_FIRMNESS).forGetter(MassLook::firmness),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "gloss", DEFAULT_GLOSS).forGetter(MassLook::gloss),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "grain", DEFAULT_GRAIN).forGetter(MassLook::grain),
            StrictCodecs.optional(Codec.floatRange(1.0F, 256.0F), "tile", DEFAULT_TILE).forGetter(MassLook::tile),
            StrictCodecs.optional(Codec.floatRange(0.0F, 16.0F), "cell", TEXEL_CELL).forGetter(MassLook::cell)
    ).apply(instance, MassLook::new));

    public MassLook {
        firmness = Math.max(0.0F, Math.min(1.0F, firmness));
        gloss = Math.max(0.0F, Math.min(1.0F, gloss));
        grain = Math.max(0.0F, Math.min(1.0F, grain));
        tile = Math.max(1.0F, tile);
        cell = Math.max(0.0F, Math.min(16.0F, cell));
    }

    /** How many pixels one cell of the surface covers, with {@link #TEXEL_CELL} worked out. */
    public float cellPixels() {
        return cell > 0.0F ? cell : tile / 16.0F;
    }

    /** A mass of {@code texture}, as drawn, firm as a porridge, a little glossy and grainy. */
    public static MassLook of(ResourceLocation texture) {
        return new MassLook(texture, Visual.WHITE, DEFAULT_FIRMNESS, DEFAULT_GLOSS, DEFAULT_GRAIN, DEFAULT_TILE, TEXEL_CELL);
    }

    /** The same mass with {@code rgb} multiplied into its texture. */
    public MassLook tinted(int rgb) {
        return new MassLook(texture, 0xFF000000 | rgb, firmness, gloss, grain, tile, cell);
    }

    /** The same mass keeping its shape as well as {@code amount}, 0 (a batter) to 1 (ice cream). */
    public MassLook firm(float amount) {
        return new MassLook(texture, tint, amount, gloss, grain, tile, cell);
    }

    /** The same mass as glossy as {@code amount}, 0 to 1. */
    public MassLook glossy(float amount) {
        return new MassLook(texture, tint, firmness, amount, grain, tile, cell);
    }

    /** The same mass as lumpy as {@code amount}, 0 to 1. */
    public MassLook grainy(float amount) {
        return new MassLook(texture, tint, firmness, gloss, amount, tile, cell);
    }

    /** The same mass with its texture repeated every {@code pixels}. */
    public MassLook tiled(float pixels) {
        return new MassLook(texture, tint, firmness, gloss, grain, pixels, cell);
    }

    /** The same mass with its surface bending at steps of {@code pixels}; {@link #TEXEL_CELL} for one texel of its texture. */
    public MassLook celled(float pixels) {
        return new MassLook(texture, tint, firmness, gloss, grain, tile, pixels);
    }
}
