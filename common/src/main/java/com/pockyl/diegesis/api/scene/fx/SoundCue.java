package com.pockyl.diegesis.api.scene.fx;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/**
 * What one of the library's own sounds (picking up, putting down, a lever...) is at a station or node: another sound, the same
 * one louder or higher, or nothing. Written as a sound id ({@code "mymod:station.clink"}), as {@code false} for silence, or as
 * {@code {"id": ..., "volume": 0.5, "pitch": 1.2}}, where {@code volume} and {@code pitch} multiply what the library plays (it
 * still sounds louder for a hard landing and higher for a fuller vessel).
 *
 * @param sound  the sound played instead; empty keeps the library's own
 * @param volume times the library's volume
 * @param pitch  times the library's pitch
 * @param silent nothing is played
 */
@ApiStatus.Experimental
public record SoundCue(Optional<Identifier> sound, float volume, float pitch, boolean silent) {
    public static final SoundCue SILENT = new SoundCue(Optional.empty(), 0.0F, 1.0F, true);

    private static final Codec<SoundCue> OBJECT = StrictCodecs.record(instance -> instance.group(
            StrictCodecs.optional(Identifier.CODEC, "id").forGetter(SoundCue::sound),
            StrictCodecs.optional(Codec.floatRange(0.0F, 4.0F), "volume", 1.0F).forGetter(SoundCue::volume),
            StrictCodecs.optional(Codec.floatRange(0.1F, 4.0F), "pitch", 1.0F).forGetter(SoundCue::pitch)
    ).apply(instance, (sound, volume, pitch) -> new SoundCue(sound, volume, pitch, false)));

    private static final Codec<SoundCue> FLAG = Codec.BOOL.comapFlatMap(value -> value
            ? DataResult.error(() -> "A sound is a sound id, an object, or false for none; true means nothing")
            : DataResult.success(SILENT), cue -> false);

    private static final Codec<SoundCue> ID = Identifier.CODEC.xmap(SoundCue::of, cue -> cue.sound.orElseThrow());

    public static final Codec<SoundCue> CODEC = Codec.either(FLAG, Codec.either(ID, OBJECT)).xmap(
            either -> either.map(cue -> cue, inner -> inner.map(cue -> cue, cue -> cue)),
            cue -> cue.silent ? Either.left(cue) : Either.right(cue.volume == 1.0F && cue.pitch == 1.0F && cue.sound.isPresent()
                    ? Either.left(cue) : Either.right(cue)));

    /** Another sound in place of the library's. */
    public static SoundCue of(Identifier sound) {
        return new SoundCue(Optional.of(sound), 1.0F, 1.0F, false);
    }
}
