package com.pockyl.diegesis.impl.client.focus;

import com.pockyl.diegesis.DiegesisMod;

import java.util.function.Predicate;

/**
 * Keeps camera mods off the camera while it is at a station. Real Camera binds the camera to the player model's head, and besides its
 * own hook at the end of the camera's setup it sets the camera's position once more just before the frame's frustum is built: the
 * station's view kept its angle but not its place, and the work was off screen. Real Camera lets other mods switch it off by a
 * condition (Freecam does); the condition here is working at a station, the flights in and out included.
 *
 * <p>Mods that place the camera only in the camera's setup, as Perspective API does for Leawind's Third Person, need nothing here: the
 * station's camera hook runs after theirs ({@code CameraMixin}).
 */
final class CameraMods {
    private static boolean done;

    private CameraMods() {
    }

    /** Hands the camera to the stations while one is worked at, once per game. */
    static void giveWay() {
        if (done) {
            return;
        }
        done = true;
        realCamera();
    }

    private static void realCamera() {
        Class<?> helper = load("com.xtracr.realcamera.compat.DisableHelper");
        if (helper == null) {
            return;
        }
        try {
            // Its main switch: an entry whose conditions are or-ed, tested with the camera's player every frame.
            Object mainFeature = helper.getField("MAIN_FEATURE").get(null);
            Predicate<Object> atStation = entity -> FocusController.get().isActive();
            mainFeature.getClass().getMethod("registerOr", Predicate.class).invoke(mainFeature, atStation);
        } catch (ReflectiveOperationException | RuntimeException e) {
            DiegesisMod.LOGGER.warn("Could not keep Real Camera off the stations' camera", e);
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, CameraMods.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
