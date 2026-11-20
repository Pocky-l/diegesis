package com.pockyl.diegesis.impl.client.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.client.ParticleStyle;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.scene.fx.EffectDefinition;
import com.pockyl.diegesis.impl.codec.StrictCodecs;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Effects and particle styles written as files: {@code assets/<namespace>/diegesis/effects/<path>.json} and
 * {@code .../diegesis/particles/<path>.json}. Loaded with the scenes (F3+T reloads them); a file wins over what code registered
 * under the same id.
 */
@SuppressWarnings("removal")
public final class FxLibrary {
    private static final String EFFECT_DIR = "diegesis/effects";
    private static final String PARTICLE_DIR = "diegesis/particles";

    /** {@code {"base": "minecraft:spark", "count": 8}}: sprites {@code minecraft:spark_0} .. {@code spark_7}. */
    private record Frames(Identifier base, int count) {
        static final Codec<Frames> CODEC = StrictCodecs.record(instance -> instance.group(
                Identifier.CODEC.fieldOf("base").forGetter(Frames::base),
                Codec.intRange(1, 64).fieldOf("count").forGetter(Frames::count)
        ).apply(instance, Frames::new));

        List<Identifier> sprites() {
            return java.util.stream.IntStream.range(0, count)
                    .mapToObj(i -> Identifier.fromNamespaceAndPath(base.getNamespace(), base.getPath() + "_" + i)).toList();
        }
    }

    private static final Codec<List<Identifier>> SPRITES = Codec.either(Identifier.CODEC.listOf(), Frames.CODEC).comapFlatMap(
            either -> {
                List<Identifier> sprites = either.map(list -> list, Frames::sprites);
                return sprites.isEmpty() ? DataResult.error(() -> "A particle style needs at least one sprite") : DataResult.success(sprites);
            }, Either::left);

    private static final ParticleStyle DEFAULTS = ParticleStyle.builder(Identifier.fromNamespaceAndPath("minecraft", "generic_0")).build();

    /** A particle style as a file; every field but {@code sprites} has the builder's default. */
    public static final Codec<ParticleStyle> PARTICLE_CODEC = StrictCodecs.<ParticleStyle>record(instance -> instance.group(
            SPRITES.fieldOf("sprites").forGetter(ParticleStyle::sprites),
            StrictCodecs.optional(Visual.COLOR, "color", DEFAULTS.color()).forGetter(ParticleStyle::color),
            StrictCodecs.optional(StrictCodecs.RANGE, "size", DEFAULTS.size()).forGetter(ParticleStyle::size),
            StrictCodecs.optional(StrictCodecs.RANGE, "life", DEFAULTS.life()).forGetter(ParticleStyle::life),
            StrictCodecs.optional(Codec.FLOAT, "gravity", DEFAULTS.gravity()).forGetter(ParticleStyle::gravity),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "drag", DEFAULTS.drag()).forGetter(ParticleStyle::drag),
            StrictCodecs.optional(Codec.BOOL, "emissive", false).forGetter(ParticleStyle::emissive),
            StrictCodecs.optional(Codec.BOOL, "stretch", false).forGetter(ParticleStyle::stretch),
            StrictCodecs.optional(Codec.floatRange(0.0F, 20.0F), "grow", 1.0F).forGetter(ParticleStyle::grow),
            StrictCodecs.optional(Codec.BOOL, "fade", true).forGetter(ParticleStyle::fade),
            StrictCodecs.optional(Codec.floatRange(0.0F, 16.0F), "wobble", 0.0F).forGetter(ParticleStyle::wobble),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "fade_in", 0.0F).forGetter(ParticleStyle::fadeIn),
            StrictCodecs.optional(Identifier.CODEC, "surface").forGetter(style -> Optional.ofNullable(style.surface()))
    ).apply(instance, (sprites, color, size, life, gravity, drag, emissive, stretch, grow, fade, wobble, fadeIn, surface) ->
            new ParticleStyle(sprites, color, size, life, gravity, drag, emissive, stretch, grow, fade, wobble, fadeIn, surface.orElse(null))));

    private static volatile Map<Identifier, EffectDefinition> effects = Map.of();
    private static volatile Map<Identifier, ParticleStyle> particles = Map.of();

    /** What the files hold, read off the render thread. */
    public record Prepared(Map<Identifier, EffectDefinition> effects, Map<Identifier, ParticleStyle> particles) {
    }

    private FxLibrary() {
    }

    public static Prepared load(ResourceManager manager) {
        Map<Identifier, EffectDefinition> loadedEffects = new HashMap<>();
        Map<Identifier, ParticleStyle> loadedParticles = new HashMap<>();
        read(manager, EFFECT_DIR, EffectDefinition.CODEC, "Effect", loadedEffects::put);
        read(manager, PARTICLE_DIR, PARTICLE_CODEC, "Particle style", loadedParticles::put);
        return new Prepared(Map.copyOf(loadedEffects), Map.copyOf(loadedParticles));
    }

    private static <T> void read(ResourceManager manager, String dir, Codec<T> codec, String what, BiConsumer<Identifier, T> sink) {
        Map<Identifier, Resource> files = manager.listResources(dir, path -> path.getPath().endsWith(".json"));
        files.forEach((file, resource) -> {
            Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(),
                    file.getPath().substring(dir.length() + 1, file.getPath().length() - ".json".length()));
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
                result.resultOrPartial(error -> DiegesisMod.LOGGER.error("{} {} ({}) is broken: {}", what, id, file, error))
                        .filter(value -> result.error().isEmpty())
                        .ifPresent(value -> sink.accept(id, value));
            } catch (Exception e) {
                DiegesisMod.LOGGER.error("Could not read {} {} ({})", what.toLowerCase(java.util.Locale.ROOT), id, file, e);
            }
        });
    }

    public static void apply(Prepared prepared) {
        effects = prepared.effects();
        particles = prepared.particles();
    }

    public static EffectDefinition effect(Identifier id) {
        return effects.get(id);
    }

    public static ParticleStyle particle(Identifier id) {
        return particles.get(id);
    }

    public static int effectCount() {
        return effects.size();
    }

    public static int particleCount() {
        return particles.size();
    }
}
