package com.pockyl.diegesis.api.scene.fx;

import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.DiegesisMod;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marks a node can carry ({@code "feedback": {"marks": [...]}}) to say what touching it means: {@code SceneEvent.Exposed} tells
 * what the carried thing was exposed to. Any id is a mark; these are the ones the library knows. A tank with liquid in it is
 * marked {@link #WET} and with its liquid's id ({@code minecraft:lava}) by itself. Marks are labels only: nothing burns or gets
 * wet unless the station's own logic says so.
 */
@ApiStatus.Experimental
public final class Marks {
    /** A flame, an ember, a burner's mouth. */
    public static final Identifier FIRE = DiegesisMod.id("fire");
    /** Hot without a flame: a hot plate, a glowing ingot. */
    public static final Identifier HOT = DiegesisMod.id("hot");
    /** Water or any liquid: every tank with liquid in it has it. */
    public static final Identifier WET = DiegesisMod.id("wet");
    /** A blade, a saw. */
    public static final Identifier SHARP = DiegesisMod.id("sharp");

    private Marks() {
    }
}
