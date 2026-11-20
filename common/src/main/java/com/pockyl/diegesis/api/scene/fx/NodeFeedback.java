package com.pockyl.diegesis.api.scene.fx;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;

/**
 * A node's own feedback ({@code "feedback"} on a node): the library's sounds for this node (grabbing it, setting it down, its
 * lever or knob), over the scene's, and the marks that say what touching it means ({@link Marks}).
 *
 * @param sounds the library's sounds for this node, as in {@link FeedbackOptions#sounds()}
 * @param marks  what touching the node exposes a carried thing to: {@code diegesis:fire}, {@code diegesis:wet}, any id
 */
@ApiStatus.Experimental
public record NodeFeedback(Map<FeedbackOptions.Cue, SoundCue> sounds, List<Identifier> marks) {
    public static final NodeFeedback DEFAULT = new NodeFeedback(Map.of(), List.of());

    public static final Codec<NodeFeedback> CODEC = StrictCodecs.record(instance -> instance.group(
            StrictCodecs.optional(FeedbackOptions.SOUNDS_CODEC, "sounds", Map.of()).forGetter(NodeFeedback::sounds),
            StrictCodecs.optional(Identifier.CODEC.listOf(), "marks", List.of()).forGetter(NodeFeedback::marks)
    ).apply(instance, NodeFeedback::new));

    public NodeFeedback {
        sounds = Map.copyOf(sounds);
        marks = List.copyOf(marks);
    }
}
