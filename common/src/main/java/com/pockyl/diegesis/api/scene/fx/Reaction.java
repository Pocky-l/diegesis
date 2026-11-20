package com.pockyl.diegesis.api.scene.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Plays an effect when something happens in the scene, written in the scene file ({@code "feedback": {"reactions": [...]}}):
 * sparks when a blade touches the grindstone, a splash where a stream lands. Only the look, seen by the local player (and by
 * everyone near with {@code shared}); a reaction never changes the station's state. It runs before the behaviour's
 * {@code onEvent}.
 *
 * @param on       the event
 * @param node     only for this node (the one held, clicked, landed...); any without it
 * @param other    only when the other node of the event is this one (what was touched, poured into, landed on)
 * @param mark     only when the other node carries this mark ({@code contact}, {@code exposed} and {@code unexposed})
 * @param minSpeed only at this speed or faster, pixels a second (events with a speed)
 * @param effect   an effect id to play: a file's, a registered one or a particle style
 * @param steps    the effect written out here instead
 * @param cooldown seconds before this reaction plays again
 * @param shared   everyone near the station sees it too (sent through the server)
 */
@ApiStatus.Experimental
public record Reaction(On on, Optional<String> node, Optional<String> other, Optional<Identifier> mark, float minSpeed,
        Optional<Identifier> effect, List<EffectStep> steps, float cooldown, boolean shared) {
    /** The events a reaction can follow. */
    public enum On implements StringRepresentable {
        /** A node without a role was clicked. */
        CLICK,
        /** A grabbable was picked up. */
        GRAB,
        /** A grabbable was let go. */
        RELEASE,
        /** A carried node started touching another node. */
        CONTACT,
        /** It stopped touching it. */
        CONTACT_END,
        /** A held vessel started pouring. */
        POUR,
        /** It stopped pouring. */
        POUR_END,
        /** A control was moved. */
        CONTROL,
        /** A button was pressed. */
        BUTTON,
        /** A placed thing knocked into another or into a wall (physics). */
        IMPACT,
        /** A placed thing came to rest where it was put. */
        LANDED,
        /** A placed thing fell off its surfaces. */
        FELL_OFF,
        /** A stream of liquid reached where it lands. */
        STREAM_LANDED,
        /** A carried node went under a liquid's surface. */
        SUBMERGE,
        /** It came out again. */
        EMERGE,
        /** The stack in the hand started touching a node. */
        ITEM_CONTACT,
        /** It stopped touching it. */
        ITEM_CONTACT_END,
        /** A carried node or the stack in the hand started touching a marked node. */
        EXPOSED,
        /** It left the last node with that mark. */
        UNEXPOSED;

        public static final Codec<On> CODEC = StringRepresentable.fromEnum(On::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<Reaction> CODEC = StrictCodecs.<Reaction>record(instance -> instance.group(
            On.CODEC.fieldOf("on").forGetter(Reaction::on),
            StrictCodecs.optional(Codec.STRING, "node").forGetter(Reaction::node),
            StrictCodecs.optional(Codec.STRING, "other").forGetter(Reaction::other),
            StrictCodecs.optional(Identifier.CODEC, "mark").forGetter(Reaction::mark),
            StrictCodecs.optional(Codec.floatRange(0.0F, 10000.0F), "min_speed", 0.0F).forGetter(Reaction::minSpeed),
            StrictCodecs.optional(Identifier.CODEC, "effect").forGetter(Reaction::effect),
            StrictCodecs.optional(EffectStep.CODEC.listOf(), "steps", List.of()).forGetter(Reaction::steps),
            StrictCodecs.optional(Codec.floatRange(0.0F, 60.0F), "cooldown", 0.1F).forGetter(Reaction::cooldown),
            StrictCodecs.optional(Codec.BOOL, "shared", false).forGetter(Reaction::shared)
    ).apply(instance, Reaction::new)).comapFlatMap(Reaction::validate, reaction -> reaction);

    public Reaction {
        steps = List.copyOf(steps);
    }

    private static DataResult<Reaction> validate(Reaction reaction) {
        if (reaction.effect.isPresent() == !reaction.steps.isEmpty()) {
            return DataResult.error(() -> "A reaction to \"" + reaction.on.getSerializedName()
                    + "\" names an \"effect\" or lists its \"steps\", one of the two");
        }
        if (reaction.shared && reaction.effect.isEmpty()) {
            return DataResult.error(() -> "A shared reaction names its \"effect\" by id: the others play it from their own files");
        }
        return DataResult.success(reaction);
    }
}
