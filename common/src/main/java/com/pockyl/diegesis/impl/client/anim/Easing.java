package com.pockyl.diegesis.impl.client.anim;

import net.minecraft.util.Mth;

/** Easing curves for 0..1 progress values. */
public final class Easing {
    /** The inner points of {@link #flight}. */
    private static final float FLIGHT_X1 = 0.25F;
    private static final float FLIGHT_Y1 = 0.1F;
    private static final float FLIGHT_X2 = 0.25F;
    private static final float FLIGHT_Y2 = 1.0F;

    private Easing() {
    }

    /** Slow start, slow end: the camera gliding between shots at a station. */
    public static float inOutCubic(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
    }

    public static float outCubic(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    /**
     * Sets off at once and lands softly: camera flights to and from a station. The cubic Bezier (0.25, 0.1) (0.25, 1) that CSS
     * calls {@code ease}: it moves from the first frame, is fastest at a fifth of the way and spends the last third settling, where
     * a slow start ({@link #inOutCubic}) spent its first tenth standing still.
     */
    public static float flight(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return bezier(bezierParameter(t, FLIGHT_X1, FLIGHT_X2), FLIGHT_Y1, FLIGHT_Y2);
    }

    /** The time at which {@link #flight} reaches {@code value}. */
    public static float flightInverse(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return bezier(bezierParameter(value, FLIGHT_Y1, FLIGHT_Y2), FLIGHT_X1, FLIGHT_X2);
    }

    /** One coordinate of a cubic Bezier from 0 to 1 with inner points {@code p1}, {@code p2}, at parameter {@code s}. */
    private static float bezier(float s, float p1, float p2) {
        float inv = 1.0F - s;
        return 3.0F * inv * inv * s * p1 + 3.0F * inv * s * s * p2 + s * s * s;
    }

    /** The parameter at which that coordinate is {@code value}; it rises from 0 to 1, so halving finds it. */
    private static float bezierParameter(float value, float p1, float p2) {
        float low = 0.0F;
        float high = 1.0F;
        for (int i = 0; i < 24; i++) {
            float mid = (low + high) * 0.5F;
            if (bezier(mid, p1, p2) < value) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5F;
    }

    /** Moves {@code current} towards {@code target} exponentially: {@code rate} is the fraction closed per second. */
    public static float approach(float current, float target, float halfLife, float dt) {
        if (halfLife <= 0.0F) {
            return target;
        }
        float k = (float) Math.pow(0.5, dt / halfLife);
        return target + (current - target) * k;
    }
}
