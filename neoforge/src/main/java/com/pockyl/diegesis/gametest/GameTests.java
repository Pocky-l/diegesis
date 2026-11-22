package com.pockyl.diegesis.gametest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.Diegesis;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Registers the {@link GameTest} methods of the test classes with the registry based game test framework (Minecraft 1.21.5 and
 * newer). A test is named {@code diegesis:<class>.<method>}, lower case, as the annotation framework named it before.
 */
public final class GameTests {
    private static final List<Class<?>> CLASSES = List.of(ModGameTests.class, FeedbackGameTests.class, ListenerGameTests.class,
            MenuGameTests.class, PlacingGameTests.class, PlacingHeapTests.class, PlacingStressTests.class, PlacingSyncTests.class);
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Diegesis.MOD_ID);

    private record Test(String name, Method method, GameTest settings) {
    }

    private GameTests() {
    }

    /** Does nothing unless the game test framework is on (the game test server and dev runs that enable it). */
    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        List<Test> tests = collect();
        for (Test test : tests) {
            FUNCTIONS.register(test.name(), () -> helper -> run(test.method(), helper));
        }
        FUNCTIONS.register(modBus);
        modBus.addListener((RegisterGameTestsEvent event) -> {
            Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(DiegesisMod.id("default"),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            for (Test test : tests) {
                Identifier id = DiegesisMod.id(test.name());
                TestData<Holder<TestEnvironmentDefinition>> data = new TestData<>(environment, DiegesisMod.id(test.settings().template()),
                        test.settings().timeoutTicks(), 0, true, Rotation.NONE, false, 1, 1, false);
                event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
            }
        });
    }

    private static List<Test> collect() {
        List<Test> tests = new ArrayList<>();
        for (Class<?> type : CLASSES) {
            List<Method> methods = new ArrayList<>(List.of(type.getDeclaredMethods()));
            methods.sort(Comparator.comparing(Method::getName));
            for (Method method : methods) {
                GameTest settings = method.getAnnotation(GameTest.class);
                if (settings == null) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != GameTestHelper.class) {
                    throw new IllegalStateException("A game test must be static and take a GameTestHelper: " + method);
                }
                method.setAccessible(true);
                String name = type.getSimpleName().toLowerCase(Locale.ROOT) + "." + method.getName().toLowerCase(Locale.ROOT);
                tests.add(new Test(name, method, settings));
            }
        }
        return tests;
    }

    private static void run(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw new IllegalStateException(e.getCause());
        }
    }
}
