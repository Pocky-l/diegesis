package com.pockyl.diegesis.impl.client.hud;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.scene.HudOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * The game's HUD sliding off the screen while the player starts to work at a station, and back when they leave
 * ({@link HudOptions}). The loaders draw each part of the HUD between {@link #begin} and {@link #end}: a Forge overlay, a part of
 * the vanilla HUD on Fabric, all that mods draw through the loader's HUD hook. While a part is drawn, what it draws through
 * {@link GuiGraphics} is measured ({@link #include}), so each part goes to the edge of the screen nearest to it, as far as it
 * takes to be off the screen, wherever its mod put it. What covers most of the screen (a vignette, a frost border) stays where
 * it is until the HUD is hidden.
 */
public final class HudSlide {
    /** Where a part goes. */
    private enum Edge {
        LEFT, RIGHT, UP, DOWN, NONE
    }

    /** Parts the owner placed by hand: the chat goes left, the hotbar down. */
    public static final String CHAT = "chat";
    public static final String HOTBAR = "hotbar";
    /** A part this much of the screen wide and high is the screen's own: it does not move. */
    private static final float WHOLE_SCREEN = 0.6F;
    /** Pixels past the edge a part goes, so that nothing of its shadow is left. */
    private static final float PAST = 4.0F;

    /** 0: in place, 1: off the screen; where it is going. */
    private static float amount;
    private static float target;
    private static float seconds = HudOptions.DEFAULT.seconds();
    private static long lastNanos = Util.getNanos();
    /** Where each part was drawn last, unmoved ({min x, min y, max x, max y}, GUI pixels), and where it goes in this slide. */
    private static final Map<String, float[]> BOXES = new HashMap<>();
    private static final Map<String, Edge> EDGES = new HashMap<>();
    /** The part being drawn now, how deep in parts it is drawn, how far it was moved, what it covered. */
    private static String current;
    private static int depth;
    private static boolean moved;
    private static float shiftX;
    private static float shiftY;
    private static float minX;
    private static float minY;
    private static float maxX;
    private static float maxY;
    private static boolean measured;
    private static final Vector3f CORNER = new Vector3f();

    private HudSlide() {
    }

    /** The HUD starts to leave. */
    public static void out(HudOptions options) {
        if (amount <= 0.0F) {
            EDGES.clear();
        }
        seconds = options.seconds();
        target = 1.0F;
        if (!options.slide()) {
            amount = 1.0F;
        }
    }

    /** The HUD starts to come back. */
    public static void in(HudOptions options) {
        seconds = options.seconds();
        target = 0.0F;
        if (!options.slide()) {
            amount = 0.0F;
        }
    }

    /** Back in place at once: the world was left. */
    public static void reset() {
        amount = 0.0F;
        target = 0.0F;
        EDGES.clear();
    }

    /** Moves the slide on; once per frame. */
    public static void frame() {
        long now = Util.getNanos();
        float dt = Math.min((now - lastNanos) / 1.0e9F, 0.1F);
        lastNanos = now;
        float step = dt / Math.max(seconds, 0.01F);
        amount = amount < target ? Math.min(target, amount + step) : Math.max(target, amount - step);
    }

    /** Whether the HUD is all off the screen and staying off: it may be hidden now. */
    public static boolean gone() {
        return amount >= 1.0F && target >= 1.0F;
    }

    /** Whether any of the HUD is off its place: going, gone or coming back. */
    public static boolean moving() {
        return amount > 0.0F || target > 0.0F;
    }

    /** Draws a part of the HUD from here on where the slide has it, measuring it; {@code key} names the part. */
    public static void begin(String key, GuiGraphics graphics) {
        if (depth++ > 0) {
            return;
        }
        moved = moving();
        if (!moved) {
            return;
        }
        current = key;
        measured = false;
        minX = Float.POSITIVE_INFINITY;
        minY = Float.POSITIVE_INFINITY;
        maxX = Float.NEGATIVE_INFINITY;
        maxY = Float.NEGATIVE_INFINITY;
        offset(key, graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        graphics.pose().translate(shiftX, shiftY, 0.0F);
    }

    /** The part {@link #begin} started is drawn. */
    public static void end(GuiGraphics graphics) {
        if (depth == 0 || --depth > 0) {
            return;
        }
        if (!moved) {
            return;
        }
        graphics.pose().popPose();
        if (measured) {
            BOXES.put(current, new float[] {minX, minY, maxX, maxY});
        }
        current = null;
    }

    /** Something is drawn over {@code x0, y0} to {@code x1, y1} through {@code pose}: it belongs to the part being drawn. */
    public static void include(Matrix4f pose, float x0, float y0, float x1, float y1) {
        if (current == null) {
            return;
        }
        corner(pose, x0, y0);
        corner(pose, x1, y0);
        corner(pose, x0, y1);
        corner(pose, x1, y1);
        measured = true;
    }

    private static void corner(Matrix4f pose, float x, float y) {
        pose.transformPosition(CORNER.set(x, y, 0.0F));
        // Where it would be without the slide.
        float atX = CORNER.x - shiftX;
        float atY = CORNER.y - shiftY;
        minX = Math.min(minX, atX);
        minY = Math.min(minY, atY);
        maxX = Math.max(maxX, atX);
        maxY = Math.max(maxY, atY);
    }

    /** How far part {@code key} is moved now, into {@link #shiftX} and {@link #shiftY}. */
    private static void offset(String key, int width, int height) {
        shiftX = 0.0F;
        shiftY = 0.0F;
        float[] box = BOXES.get(key);
        if (box == null) {
            // Never seen yet: drawn off the screen while the HUD is mostly away, measured, and placed from the next frame on.
            if (amount > 0.5F) {
                shiftY = height * 2.0F;
            }
            return;
        }
        Edge edge = EDGES.computeIfAbsent(key, k -> nearest(k, box, width, height));
        float eased = amount * amount * (3.0F - 2.0F * amount);
        switch (edge) {
            case LEFT -> shiftX = -(box[2] + PAST) * eased;
            case RIGHT -> shiftX = (width - box[0] + PAST) * eased;
            case UP -> shiftY = -(box[3] + PAST) * eased;
            case DOWN -> shiftY = (height - box[1] + PAST) * eased;
            case NONE -> {
            }
        }
    }

    private static Edge nearest(String key, float[] box, int width, int height) {
        if (CHAT.equals(key)) {
            return Edge.LEFT;
        }
        if (HOTBAR.equals(key)) {
            return Edge.DOWN;
        }
        if (box[2] - box[0] > width * WHOLE_SCREEN && box[3] - box[1] > height * WHOLE_SCREEN) {
            return Edge.NONE;
        }
        float left = box[0];
        float right = width - box[2];
        float up = box[1];
        float down = height - box[3];
        float least = Math.min(Math.min(left, right), Math.min(up, down));
        return least == down ? Edge.DOWN : least == left ? Edge.LEFT : least == right ? Edge.RIGHT : Edge.UP;
    }
}
