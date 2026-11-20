package com.pockyl.diegesis.api.scene.fx;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How the scene answers what the player does ({@code "feedback"} in the scene file): the library's own sounds, effects played
 * on events, and how hard a knock must be to count. Nothing here changes by default.
 *
 * @param sounds         the library's sounds swapped, changed or silenced at this station (missing: as they are)
 * @param reactions      effects played on events, see {@link Reaction}
 * @param impactMinSpeed how fast (pixels a second) placed things must knock together for {@code SceneEvent.Impact}
 */
@ApiStatus.Experimental
public record FeedbackOptions(Map<Cue, SoundCue> sounds, List<Reaction> reactions, float impactMinSpeed) {
    public static final float DEFAULT_IMPACT_SPEED = 12.0F;
    public static final FeedbackOptions DEFAULT = new FeedbackOptions(Map.of(), List.of(), DEFAULT_IMPACT_SPEED);

    /** The library's own sounds. */
    public enum Cue implements StringRepresentable {
        PICK_UP, PUT_DOWN, GRAB, SET_DOWN, TOGGLE, BUTTON, DETENT, POUR, LANDING;

        public static final Codec<Cue> CODEC = StringRepresentable.fromEnum(Cue::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<Map<Cue, SoundCue>> SOUNDS_CODEC = Codec.unboundedMap(Cue.CODEC, SoundCue.CODEC);

    public static final Codec<FeedbackOptions> CODEC = StrictCodecs.record(instance -> instance.group(
            StrictCodecs.optional(SOUNDS_CODEC, "sounds", Map.of()).forGetter(FeedbackOptions::sounds),
            StrictCodecs.optional(Reaction.CODEC.listOf(), "reactions", List.of()).forGetter(FeedbackOptions::reactions),
            StrictCodecs.optional(Codec.floatRange(0.0F, 10000.0F), "impact_min_speed", DEFAULT_IMPACT_SPEED)
                    .forGetter(FeedbackOptions::impactMinSpeed)
    ).apply(instance, FeedbackOptions::new));

    public FeedbackOptions {
        sounds = Map.copyOf(sounds);
        reactions = List.copyOf(reactions);
    }
}
