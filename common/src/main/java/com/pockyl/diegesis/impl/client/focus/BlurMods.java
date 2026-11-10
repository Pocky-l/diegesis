package com.pockyl.diegesis.impl.client.focus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.pockyl.diegesis.DiegesisMod;

/**
 * Keeps GUI blur mods off the focus screen. Blur (Fabric), its Forge port and ReBlured blur the whole world behind every
 * screen that is not on their exclusion list, and at a station the world is the interface: the station sat behind
 * frosted glass while only the slots stayed sharp. Each of them checks its list by the screen's class name when a screen
 * opens, so the focus screen is put on it just before it opens.
 *
 * <p>Blur+ and Blur Perfected on 1.21 need nothing: they blur from {@code renderBlurredBackground}, which the focus
 * screen never calls.
 */
final class BlurMods {
    private static final String SCREEN = FocusScreen.class.getName();

    private static List<Fix> fixes;

    private BlurMods() {
    }

    /** Puts the focus screen on the exclusion list of every blur mod present. */
    static void keepOff() {
        if (fixes == null) {
            fixes = find();
        }
        fixes.removeIf(fix -> {
            try {
                fix.apply();
                return false;
            } catch (ReflectiveOperationException | RuntimeException e) {
                DiegesisMod.LOGGER.warn("Could not keep a blur mod off station screens", e);
                return true;
            }
        });
    }

    private static List<Fix> find() {
        List<Fix> found = new ArrayList<>();
        try {
            Class<?> blur = load("com.tterrag.blur.config.BlurConfig");
            if (blur != null) {
                Field exclusions = blur.getField("blurExclusions");
                if (Modifier.isStatic(exclusions.getModifiers())) {
                    // Blur (Fabric): a MidnightConfig list, matched by "class name contains".
                    found.add(() -> exclusions.set(null, withScreen((List<?>) exclusions.get(null))));
                } else {
                    // Blur Forge: a ForgeConfigSpec value, matched by "class name starts with"; set() saves its file.
                    Object instance = blur.getField("INSTANCE").get(null);
                    found.add(() -> {
                        Object value = exclusions.get(instance);
                        List<?> current = (List<?>) value.getClass().getMethod("get").invoke(value);
                        if (!current.contains(SCREEN)) {
                            value.getClass().getMethod("set", Object.class).invoke(value, withScreen(current));
                        }
                    });
                }
            }
            Class<?> reblured = load("de.cheaterpaul.blur.BlurConfig");
            if (reblured != null) {
                // ReBlured: the set its screen listener checks, refilled from its config on every reload.
                Field exclusions = reblured.getField("guiExlusions");
                found.add(() -> {
                    @SuppressWarnings("unchecked")
                    Collection<String> current = (Collection<String>) exclusions.get(null);
                    if (!current.contains(SCREEN)) {
                        current.add(SCREEN);
                    }
                });
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            DiegesisMod.LOGGER.warn("Could not keep a blur mod off station screens", e);
        }
        return found;
    }

    private static List<String> withScreen(List<?> current) {
        List<String> list = new ArrayList<>();
        for (Object entry : current) {
            list.add(String.valueOf(entry));
        }
        if (!list.contains(SCREEN)) {
            list.add(SCREEN);
        }
        return list;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, BlurMods.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface Fix {
        void apply() throws ReflectiveOperationException;
    }
}
