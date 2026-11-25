package com.pockyl.diegesis.impl.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import com.pockyl.diegesis.api.scene.HudOptions;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The game's HUD sliding off the screen while the player starts to work at a station, and back when they leave
 * ({@link HudOptions}). The loaders draw each part of the HUD between {@link #begin} and {@link #end}: a NeoForge layer, a part of
 * the vanilla HUD on Fabric, all that mods draw through the loader's HUD hook. While a part is drawn, what it draws through
 * {@link GuiGraphics} is measured ({@link #include}), so each part goes to the edge of the screen nearest to it, as far as it
 * takes to be off the screen, wherever its mod put it. Parts are measured while the HUD is in place too, so that what covers
 * most of the screen (a vignette, a frost border) is known before a station opens: it stays where it is, and stays drawn once
 * the HUD is gone while the scene keeps it ({@link HudOptions#keepVignette()}).
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
    /** How deep the pose stack was when {@link #begin} pushed the slide's level for the part being drawn. */
    private static int below;
    /** How many pose levels the part's listeners found, before any of them ran ({@link #partListeners}). */
    private static int partBase;
    /**
     * Pose levels that listeners pushed for parts the library then hid ({@link #hidden}), to be popped in a Post that a cancelled
     * part never gets, and how many levels the pose had under them.
     */
    private static int owed;
    private static int owedBase;
    private static final Vector2f CORNER = new Vector2f();

    private HudSlide() {
    }

    /**
     * Whether the station draws the hotbar itself, in the HUD's place ({@code inventory_drawer.layout: hotbar}): the HUD's own
     * hotbar is then gone at once as the station opens and back at once as it closes, so the swap is not seen.
     */
    private static boolean hotbarKept;

    /** {@link #hotbarKept}, for the station being opened. */
    public static void keepHotbar(boolean value) {
        hotbarKept = value;
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
        hotbarKept = false;
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
        current = key;
        measured = false;
        minX = Float.POSITIVE_INFINITY;
        minY = Float.POSITIVE_INFINITY;
        maxX = Float.NEGATIVE_INFINITY;
        maxY = Float.NEGATIVE_INFINITY;
        moved = moving();
        if (!moved) {
            shiftX = 0.0F;
            shiftY = 0.0F;
            return;
        }
        offset(key, graphics.guiWidth(), graphics.guiHeight());
        below = StackDepth.of(graphics.pose());
        graphics.pose().pushMatrix();
        graphics.pose().translate(shiftX, shiftY);
    }

    /**
     * The part {@link #begin} started is drawn. Only the slide's own level is taken off the pose: what the part pushed and left
     * stays (another mod may pop it in a later part), but without the slide's move, else the station's hotbar was drawn away
     * from where it is clicked.
     */
    public static void end(GuiGraphics graphics) {
        if (depth == 0 || --depth > 0) {
            return;
        }
        if (moved) {
            unwind(graphics.pose());
        }
        if (measured) {
            BOXES.put(current, new float[] {minX, minY, maxX, maxY});
        }
        current = null;
    }

    /** The HUD starts to be drawn: nothing is owed yet. */
    public static void frameStart() {
        owed = 0;
    }

    /**
     * Before the first listener of a part: pays what hidden parts left ({@link #settle}) and notes where the part's pose begins.
     */
    public static void partListeners(GuiGraphics graphics) {
        settle(graphics);
        partBase = StackDepth.of(graphics.pose());
    }

    /**
     * The library hid the part after other listeners of it ran. A mod that moves its part pushes a pose level there and pops it
     * in the part's Post, which a cancelled part never gets: those levels are owed, else everything drawn after them, the
     * station's screen too, is moved (Souls Combat HUD, on Forge 1.20.1 and NeoForge 1.21.1, lifts the status bars this way:
     * the screen's slot row went off its bottom edge).
     */
    public static void hidden(GuiGraphics graphics) {
        int left = StackDepth.of(graphics.pose()) - partBase;
        if (left > 0) {
            if (owed == 0) {
                owedBase = partBase;
            }
            owed += left;
        }
    }

    /**
     * Pops what hidden parts left, before the next part and at the end of the HUD; never more than the pose has over where the
     * first of them began, so a level a mod took back itself when it saw the part cancelled is not popped twice.
     */
    public static void settle(GuiGraphics graphics) {
        if (owed == 0) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        for (int extra = StackDepth.of(pose) - owedBase; extra > 0 && owed > 0; extra--) {
            pose.popMatrix();
            owed--;
        }
        owed = 0;
    }

    /**
     * Takes the slide's own level out of the pose and the slide's move out of every level the part left above it; the pose ends
     * one level lower than the part left it. Popping those levels too broke a mod that pops its own later, outside a part: its
     * pop then took a level of the HUD's, or found the stack at its bottom. Where the part popped the slide's level itself (one
     * pop too many), nothing is done. JOML gives no way into the middle of the stack: the levels above the slide's are popped,
     * kept, and pushed back.
     */
    private static void unwind(Matrix3x2fStack pose) {
        int above = StackDepth.of(pose) - below - 1;
        if (above < 0) {
            return;
        }
        Matrix3x2f[] left = new Matrix3x2f[above];
        for (int i = above - 1; i >= 0; i--) {
            left[i] = new Matrix3x2f(pose);
            pose.popMatrix();
        }
        pose.popMatrix();
        if (above == 0) {
            return;
        }
        // A level left on the slide's is under * move * own: it becomes under * own.
        Matrix3x2f undo = null;
        if (pose.determinant() != 0.0F) {
            undo = new Matrix3x2f(pose).translate(-shiftX, -shiftY).mul(new Matrix3x2f(pose).invert());
        }
        for (Matrix3x2f level : left) {
            pose.pushMatrix();
            pose.set(undo != null ? level.mulLocal(undo) : level);
        }
    }

    /**
     * How many levels a pose stack has pushed. JOML keeps the count private, without the entry objects a {@code PoseStack} has
     * to compare, and writes it out after the matrix when the stack is written as an {@code Externalizable}: this stream
     * keeps that number and ignores the rest. The render thread only.
     */
    private static final class StackDepth extends ObjectOutputStream {
        private static StackDepth stream;
        private int depth;

        private StackDepth() throws IOException {
            super();
        }

        static int of(Matrix3x2fStack pose) {
            try {
                if (stream == null) {
                    stream = new StackDepth();
                }
                stream.depth = 0;
                pose.writeExternal(stream);
                return stream.depth;
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the depth of the HUD's pose stack", e);
            }
        }

        @Override
        public void writeFloat(float value) {
        }

        @Override
        public void writeInt(int value) {
            depth = value;
        }

        @Override
        protected void writeObjectOverride(Object object) {
        }
    }

    /** Whether part {@code key} covered most of the screen when it was drawn last: a vignette, not a part of the HUD. */
    public static boolean coversScreen(String key, int width, int height) {
        float[] box = BOXES.get(key);
        return box != null && wholeScreen(box, width, height);
    }

    private static boolean wholeScreen(float[] box, int width, int height) {
        return box[2] - box[0] > width * WHOLE_SCREEN && box[3] - box[1] > height * WHOLE_SCREEN;
    }

    /** Something is drawn over {@code x0, y0} to {@code x1, y1} through {@code pose}: it belongs to the part being drawn. */
    public static void include(Matrix3x2fc pose, float x0, float y0, float x1, float y1) {
        if (current == null) {
            return;
        }
        corner(pose, x0, y0);
        corner(pose, x1, y0);
        corner(pose, x0, y1);
        corner(pose, x1, y1);
        measured = true;
    }

    private static void corner(Matrix3x2fc pose, float x, float y) {
        pose.transformPosition(CORNER.set(x, y));
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
        if (hotbarKept && HOTBAR.equals(key)) {
            if (target > 0.0F) {
                shiftY = height * 2.0F;
            }
            return;
        }
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
        if (wholeScreen(box, width, height)) {
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
