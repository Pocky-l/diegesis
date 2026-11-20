package com.pockyl.diegesis.api.scene.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.impl.codec.StrictCodecs;

import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * One thing an effect does: a burst of particles, a sound, a flash of light, a shake, a while of a node's emitter, a ripple.
 * Written as an object with a {@code "type"}: {@code particles}, {@code sound}, {@code light}, {@code shake}, {@code emit},
 * {@code ripple}. Only the look: no step changes the station's state.
 */
@ApiStatus.Experimental
public sealed interface EffectStep {
    /** Where a step happens: the effect's node, the other node of the event (what was touched), or the event's point. */
    enum At implements StringRepresentable {
        NODE, OTHER, POINT;

        public static final Codec<At> CODEC = StringRepresentable.fromEnum(At::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where particles start: at the place itself, anywhere in its liquid, off its liquid's surface, or along its walls. */
    enum Where implements StringRepresentable {
        PLACE, IN_LIQUID, ON_LIQUID, LIQUID_EDGE;

        public static final Codec<Where> CODEC = StringRepresentable.fromEnum(Where::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    String type();

    /**
     * A burst of scene particles.
     *
     * @param style    a particle style ({@code diegesis:spark}, a mod's, or one from {@code diegesis/particles/})
     * @param count    how many
     * @param color    an ARGB tint; the style's colour without one
     * @param at       where it bursts
     * @param where    {@code place} at that point; the liquid kinds need a node with a tank
     * @param offset   pixels from that point, in scene space
     * @param velocity their start velocity, pixels a second; without one they burst up a little (falling kinds) or drift
     * @param spread   random spread of the start, in pixels
     */
    record Particles(Identifier style, int count, Optional<Integer> color, At at, Where where, Vector3fc offset,
            Optional<Vector3fc> velocity, float spread) implements EffectStep {
        static final MapCodec<Particles> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                typeField("particles"),
                Identifier.CODEC.fieldOf("style").forGetter(Particles::style),
                StrictCodecs.optional(Codec.intRange(1, 64), "count", 8).forGetter(Particles::count),
                StrictCodecs.optional(Visual.COLOR, "color").forGetter(Particles::color),
                StrictCodecs.optional(At.CODEC, "at", At.NODE).forGetter(Particles::at),
                StrictCodecs.optional(Where.CODEC, "where", Where.PLACE).forGetter(Particles::where),
                StrictCodecs.optional(StrictCodecs.VEC3, "offset", new Vector3f()).forGetter(p -> new Vector3f(p.offset)),
                StrictCodecs.optional(StrictCodecs.VEC3, "velocity").forGetter(p -> p.velocity.map(Vector3f::new)),
                StrictCodecs.optional(Codec.floatRange(0.0F, 32.0F), "spread", 0.4F).forGetter(Particles::spread)
        ).apply(instance, (type, style, count, color, at, where, offset, velocity, spread) -> new Particles(style, count, color, at, where,
                offset, velocity.map(v -> (Vector3fc) v), spread)));

        public Particles {
            offset = new Vector3f(offset);
            velocity = velocity.map(Vector3f::new);
        }

        @Override
        public String type() {
            return "particles";
        }
    }

    /**
     * A sound at the place.
     *
     * @param sound        a sound event id
     * @param volume       its volume
     * @param pitch        its pitch
     * @param pitchBySpeed how much higher it sounds per 100 pixels a second of the event's speed (a harder knock rings higher)
     * @param at           where it sounds
     */
    record Sound(Identifier sound, float volume, float pitch, float pitchBySpeed, At at) implements EffectStep {
        static final MapCodec<Sound> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                typeField("sound"),
                Identifier.CODEC.fieldOf("id").forGetter(Sound::sound),
                StrictCodecs.optional(Codec.floatRange(0.0F, 4.0F), "volume", 1.0F).forGetter(Sound::volume),
                StrictCodecs.optional(Codec.floatRange(0.1F, 4.0F), "pitch", 1.0F).forGetter(Sound::pitch),
                StrictCodecs.optional(Codec.floatRange(-4.0F, 4.0F), "pitch_by_speed", 0.0F).forGetter(Sound::pitchBySpeed),
                StrictCodecs.optional(At.CODEC, "at", At.NODE).forGetter(Sound::at)
        ).apply(instance, (type, sound, volume, pitch, bySpeed, at) -> new Sound(sound, volume, pitch, bySpeed, at)));

        @Override
        public String type() {
            return "sound";
        }
    }

    /**
     * A flash of the node's own {@code light}: up to {@code level} at once, fading back over {@code seconds}. A node without a
     * {@code light} in the scene has nothing to flash.
     */
    record Light(int level, float seconds, At at) implements EffectStep {
        static final MapCodec<Light> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                typeField("light"),
                StrictCodecs.optional(Codec.intRange(1, 15), "level", 15).forGetter(Light::level),
                StrictCodecs.optional(Codec.floatRange(0.05F, 10.0F), "seconds", 0.4F).forGetter(Light::seconds),
                StrictCodecs.optional(At.CODEC, "at", At.NODE).forGetter(Light::at)
        ).apply(instance, (type, level, seconds, at) -> new Light(level, seconds, at)));

        @Override
        public String type() {
            return "light";
        }
    }

    /** The node shakes by up to {@code pixels}, calming over {@code seconds}. */
    record Shake(float pixels, float seconds, At at) implements EffectStep {
        static final MapCodec<Shake> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                typeField("shake"),
                StrictCodecs.optional(Codec.floatRange(0.0F, 4.0F), "pixels", 0.3F).forGetter(Shake::pixels),
                StrictCodecs.optional(Codec.floatRange(0.05F, 5.0F), "seconds", 0.3F).forGetter(Shake::seconds),
                StrictCodecs.optional(At.CODEC, "at", At.NODE).forGetter(Shake::at)
        ).apply(instance, (type, pixels, seconds, at) -> new Shake(pixels, seconds, at)));

        @Override
        public String type() {
            return "shake";
        }
    }

    /** The node's own {@code emitter} runs for {@code seconds}, also when it is switched off. */
    record Emit(float seconds, At at) implements EffectStep {
        static final MapCodec<Emit> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                typeField("emit"),
                StrictCodecs.optional(Codec.floatRange(0.05F, 30.0F), "seconds", 1.0F).forGetter(Emit::seconds),
                StrictCodecs.optional(At.CODEC, "at", At.NODE).forGetter(Emit::at)
        ).apply(instance, (type, seconds, at) -> new Emit(seconds, at)));

        @Override
        public String type() {
            return "emit";
        }
    }

    /** Ripples over the node's liquid, from the event's point when it has one, else from the middle. */
    record Ripple(float strength, At at) implements EffectStep {
        static final MapCodec<Ripple> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                typeField("ripple"),
                StrictCodecs.optional(Codec.floatRange(0.0F, 4.0F), "strength", 0.5F).forGetter(Ripple::strength),
                StrictCodecs.optional(At.CODEC, "at", At.NODE).forGetter(Ripple::at)
        ).apply(instance, (type, strength, at) -> new Ripple(strength, at)));

        @Override
        public String type() {
            return "ripple";
        }
    }

    Map<String, MapCodec<? extends EffectStep>> TYPES = Map.of(
            "particles", StrictCodecs.closed(Particles.MAP_CODEC),
            "sound", StrictCodecs.closed(Sound.MAP_CODEC),
            "light", StrictCodecs.closed(Light.MAP_CODEC),
            "shake", StrictCodecs.closed(Shake.MAP_CODEC),
            "emit", StrictCodecs.closed(Emit.MAP_CODEC),
            "ripple", StrictCodecs.closed(Ripple.MAP_CODEC));

    Codec<EffectStep> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<EffectStep, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getMap(input).flatMap(map -> {
                T typeValue = map.get("type");
                if (typeValue == null) {
                    return DataResult.error(() -> "An effect step needs a \"type\": one of " + String.join(", ", TYPES.keySet()));
                }
                return ops.getStringValue(typeValue).flatMap(type -> {
                    MapCodec<? extends EffectStep> codec = TYPES.get(type);
                    if (codec == null) {
                        return DataResult.error(() -> "Unknown effect step type \"" + type + "\", known: " + String.join(", ", TYPES.keySet()));
                    }
                    return codec.decode(ops, map).map(step -> Pair.of((EffectStep) step, input));
                });
            });
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> DataResult<T> encode(EffectStep step, DynamicOps<T> ops, T prefix) {
            MapCodec<EffectStep> codec = (MapCodec<EffectStep>) TYPES.get(step.type());
            return codec.encoder().encode(step, ops, prefix);
        }
    };

    private static <S extends EffectStep> RecordCodecBuilder<S, String> typeField(String type) {
        return Codec.STRING.fieldOf("type").forGetter((Function<S, String>) step -> type);
    }
}
