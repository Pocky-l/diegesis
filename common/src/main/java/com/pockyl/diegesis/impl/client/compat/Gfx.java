package com.pockyl.diegesis.impl.client.compat;

import net.minecraft.client.Minecraft;

/**
 * Client calls whose form differs between the Minecraft lines the library is built for. One copy of this class per line, with
 * nothing else in it, so a change to the logic around these calls moves between lines unchanged.
 */
public final class Gfx {
    private Gfx() {
    }

    /** Whether the HUD is hidden, as F1 hides it (Minecraft 26.1.2 keeps the flag in the options; 26.3 in its {@code Hud}). */
    public static boolean hudHidden(Minecraft minecraft) {
        return minecraft.options.hideGui;
    }

    /** Hides or shows the HUD as F1 does. */
    public static void setHudHidden(Minecraft minecraft, boolean hidden) {
        minecraft.options.hideGui = hidden;
    }
}
