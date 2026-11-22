package com.pockyl.diegesis.impl.client.compat;

import net.minecraft.client.Minecraft;

/**
 * Client calls whose form differs between the Minecraft lines the library is built for. One copy of this class per line, with
 * nothing else in it, so a change to the logic around these calls moves between lines unchanged.
 */
public final class Gfx {
    /** Whether a scene drew an outline in the last frame, so that the next one runs the game's outline pass. */
    private static boolean outlineWanted;
    /** Whether the frame being drawn runs the game's outline pass. */
    private static boolean outlinePass;

    private Gfx() {
    }

    /**
     * Whether a scene's outline can be drawn in this frame; asks for the outline pass of the next. Minecraft 26.1.2 runs the
     * pass, and gives the outline types their target, only in frames with a glowing entity in view; without it the outline types
     * draw into the frame itself, and the hovered object came out as a white silhouette. (26.3 runs it for any outline drawn.)
     */
    public static boolean drawsOutlines() {
        outlineWanted = true;
        return outlinePass;
    }

    /** Called as the level's entities are taken for a frame: whether that frame should run the outline pass for a scene. */
    public static boolean takeOutlineWanted() {
        boolean wanted = outlineWanted;
        outlineWanted = false;
        return wanted;
    }

    /** Called as the level's entities are taken for a frame: whether that frame runs the outline pass. */
    public static void setOutlinePass(boolean on) {
        outlinePass = on;
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
