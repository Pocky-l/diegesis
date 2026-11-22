package com.pockyl.diegesis.gametest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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
 * Registers the {@link DiegesisTest} methods of the test classes as game tests (registry based since Minecraft 1.21.5). A test
 * is named after its method in snake case, for example {@code diegesis:mod_loads}.
 */
public final class GameTests {
    private static final List<Class<?>> CLASSES = List.of(ModGameTests.class, FeedbackGameTests.class, ListenerGameTests.class,
            MenuGameTests.class, PlacingGameTests.class, PlacingHeapTests.class, PlacingStressTests.class, PlacingSyncTests.class);
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION,
            Diegesis.MOD_ID);
    private static final List<Test> TESTS = new ArrayList<>();

    private record Test(String name, Method method, DiegesisTest settings) {
    }

    private GameTests() {
    }

    /** Registers the tests when the game test framework is on (the game test server and dev runs that enable it). */
    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        for (Class<?> type : CLASSES) {
            List<Method> methods = new ArrayList<>(List.of(type.getDeclaredMethods()));
            methods.sort(Comparator.comparing(Method::getName));
            for (Method method : methods) {
                DiegesisTest settings = method.getAnnotation(DiegesisTest.class);
                if (settings == null) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != GameTestHelper.class) {
                    throw new IllegalStateException("A game test must be static and take a GameTestHelper: " + method);
                }
                method.setAccessible(true);
                Test test = new Test(snakeCase(method.getName()), method, settings);
                TESTS.add(test);
                FUNCTIONS.register(test.name(), () -> helper -> run(test.method(), helper));
            }
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(GameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(DiegesisMod.id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test test : TESTS) {
            Identifier id = DiegesisMod.id(test.name());
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Level.OVERWORLD,
                    DiegesisMod.id(test.settings().structure()), test.settings().timeoutTicks(), 0, true, Rotation.NONE, false, 1, 1,
                    false, 0);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }

    private static void run(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
