package com.pockyl.diegesis.impl.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * Keyboard calls whose form differs between the Minecraft lines the library is built for: Minecraft 26.1.2 reads the keyboard
 * through GLFW, 26.3 through SDL. One copy of this class per line, with nothing else in it.
 */
public final class Input {
    private Input() {
    }

    /**
     * Has typed characters delivered to {@code screen} while it is open. GLFW delivers them always: nothing to switch on here
     * (on 26.3 the game hands them out only while text input is on).
     */
    public static void textInput(Screen screen, boolean on) {
    }

    /** Whether the key {@code key} ({@code InputConstants.KEY_*}) is held down now. */
    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    /** The second number of a key event, given to a station as {@code scanCode}: the keyboard's scancode (GLFW). */
    public static int scanCode(KeyEvent event) {
        return event.scancode();
    }
}
