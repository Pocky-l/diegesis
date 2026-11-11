package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

/**
 * How the game's HUD leaves when the player starts to work at a station and comes back when they leave: every part of it
 * slides off to the edge of the screen nearest to it (the hotbar down, the chat left, a minimap in a corner to its side),
 * other mods' overlays too. Written as {@code "hud"} in a scene file; every field is optional. Whether the HUD and the chat
 * go at all is {@link InterfaceOptions#hideHud()} and {@link InterfaceOptions#hideChat()}.
 *
 * @param slide   the parts slide away and back; false: they are gone and back at once
 * @param seconds how long the slide takes
 */
public record HudOptions(boolean slide, float seconds) {
    public static final HudOptions DEFAULT = new HudOptions(true, 0.3F);
    /** Gone and back at once. */
    public static final HudOptions INSTANT = new HudOptions(false, DEFAULT.seconds);

    public static final Codec<HudOptions> CODEC = StrictCodecs.record(instance -> instance.group(
            StrictCodecs.optional(Codec.BOOL, "slide", DEFAULT.slide).forGetter(HudOptions::slide),
            StrictCodecs.optional(Codec.floatRange(0.05F, 3.0F), "seconds", DEFAULT.seconds).forGetter(HudOptions::seconds)
    ).apply(instance, HudOptions::new));
}
