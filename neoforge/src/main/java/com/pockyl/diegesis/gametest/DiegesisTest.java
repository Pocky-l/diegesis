package com.pockyl.diegesis.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a static {@code void (GameTestHelper)} method as a game test; {@link GameTests} registers it. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface DiegesisTest {
    /** The structure the test runs in, from {@code data/diegesis/structure}. */
    String structure() default "empty";

    /** Ticks before the test fails. */
    int timeoutTicks() default 100;
}
