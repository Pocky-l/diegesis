package com.pockyl.diegesis.api.scene.fx;

import com.mojang.serialization.Codec;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * An effect made of steps, written as a file ({@code assets/<namespace>/diegesis/effects/<path>.json}) or inline in a scene's
 * reaction. Its id plays it anywhere an effect id is taken: {@code StationContext.effect}, {@code StationView.shareEffect}, a
 * reaction's {@code effect}. A file wins over an effect registered in code under the same id, so resource packs can restyle it.
 *
 * <p>{@code {"steps": [{"type": "particles", "style": "diegesis:dust", "count": 6}, {"type": "sound", "id":
 * "minecraft:block.stone.hit", "volume": 0.3}]}}
 */
@ApiStatus.Experimental
public record EffectDefinition(List<EffectStep> steps) {
    public static final Codec<EffectDefinition> CODEC = StrictCodecs.record(instance -> instance.group(
            EffectStep.CODEC.listOf().fieldOf("steps").forGetter(EffectDefinition::steps)
    ).apply(instance, EffectDefinition::new));

    public EffectDefinition {
        steps = List.copyOf(steps);
    }

    public static EffectDefinition of(EffectStep... steps) {
        return new EffectDefinition(List.of(steps));
    }
}
