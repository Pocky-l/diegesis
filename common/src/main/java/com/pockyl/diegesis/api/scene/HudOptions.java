package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

/**
 * How the game's HUD leaves when the player starts to work at a station and comes back when they leave: every part of it
 * slides off to the edge of the screen nearest to it (the hotbar down, the chat left, a minimap in a corner to its side),
 * other mods' overlays too. Written as {@code "hud"} in a scene file; every field is optional. Whether the HUD and the chat
 * go at all is {@link InterfaceOptions#hideHud()} and {@link InterfaceOptions#hideChat()}.
 *
 * @param slide        the parts slide away and back; false: they are gone and back at once
 * @param seconds      how long the slide takes
 * @param keepVignette what covers the whole screen stays while the rest of the HUD is gone: the vignette, frost, the view
 *                     through a carved pumpkin, the portal's swirl; false: they go with the HUD
 */
public record HudOptions(boolean slide, float seconds, boolean keepVignette) {
    public static final HudOptions DEFAULT = new HudOptions(true, 0.3F, true);
    /** Gone and back at once. */
    public static final HudOptions INSTANT = new HudOptions(false, DEFAULT.seconds, DEFAULT.keepVignette);

    public static final Codec<HudOptions> CODEC = StrictCodecs.record(instance -> instance.group(
            StrictCodecs.optional(Codec.BOOL, "slide", DEFAULT.slide).forGetter(HudOptions::slide),
            StrictCodecs.optional(Codec.floatRange(0.05F, 3.0F), "seconds", DEFAULT.seconds).forGetter(HudOptions::seconds),
            StrictCodecs.optional(Codec.BOOL, "keep_vignette", DEFAULT.keepVignette).forGetter(HudOptions::keepVignette)
    ).apply(instance, HudOptions::new));

    /** The options before {@link #keepVignette} existed: the vignette stays. */
    public HudOptions(boolean slide, float seconds) {
        this(slide, seconds, true);
    }

    /** These options with {@link #keepVignette} set. */
    public HudOptions withKeepVignette(boolean value) {
        return new HudOptions(slide, seconds, value);
    }
}
