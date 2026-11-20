package com.pockyl.diegesis.impl.client.fx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.scene.fx.EffectDefinition;
import com.pockyl.diegesis.api.scene.fx.Reaction;
import com.pockyl.diegesis.impl.client.ClientStation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A scene's reactions ({@code feedback.reactions}), sorted by event once when the scene is built, so an event without reactions
 * costs one look-up. Each reaction keeps its own cooldown.
 */
public final class Reactions {
    public static final Reactions NONE = new Reactions(new EnumMap<>(Reaction.On.class), Map.of());

    private final Map<Reaction.On, Entry[]> byEvent;
    /** The node bound to each control, for {@code control} and {@code button} reactions. */
    private final Map<String, String> controlNodes;

    private static final class Entry {
        final Reaction reaction;
        final EffectDefinition steps;
        float readyAt;

        Entry(Reaction reaction) {
            this.reaction = reaction;
            this.steps = reaction.steps().isEmpty() ? null : new EffectDefinition(reaction.steps());
        }
    }

    private Reactions(Map<Reaction.On, Entry[]> byEvent, Map<String, String> controlNodes) {
        this.byEvent = byEvent;
        this.controlNodes = controlNodes;
    }

    public static Reactions of(SceneDefinition scene) {
        List<Reaction> reactions = scene.feedback().reactions();
        if (reactions.isEmpty()) {
            return NONE;
        }
        Map<Reaction.On, List<Entry>> sorted = new EnumMap<>(Reaction.On.class);
        for (Reaction reaction : reactions) {
            sorted.computeIfAbsent(reaction.on(), on -> new ArrayList<>()).add(new Entry(reaction));
        }
        Map<Reaction.On, Entry[]> byEvent = new EnumMap<>(Reaction.On.class);
        sorted.forEach((on, entries) -> byEvent.put(on, entries.toArray(new Entry[0])));
        Map<String, String> controlNodes = new HashMap<>();
        scene.forEachNode(node -> node.control().ifPresent(control -> controlNodes.putIfAbsent(control.control(), node.id())));
        return new Reactions(byEvent, controlNodes);
    }

    public boolean isEmpty() {
        return byEvent.isEmpty();
    }

    /** Whether any reaction follows {@code on}: callers skip the work of finding an event's details without one. */
    public boolean wants(Reaction.On on) {
        return byEvent.containsKey(on);
    }

    /** Plays what follows a scene event. */
    public void handle(ClientStation station, SceneEvent event) {
        if (byEvent.isEmpty()) {
            return;
        }
        if (event instanceof SceneEvent.Click click) {
            fire(station, Reaction.On.CLICK, click.node(), null, 0.0F, null);
        } else if (event instanceof SceneEvent.Grab grab) {
            fire(station, Reaction.On.GRAB, grab.node(), null, 0.0F, null);
        } else if (event instanceof SceneEvent.Release release) {
            fire(station, Reaction.On.RELEASE, release.node(), release.target(), 0.0F, null);
        } else if (event instanceof SceneEvent.Contact contact) {
            fire(station, contact.touching() ? Reaction.On.CONTACT : Reaction.On.CONTACT_END, contact.node(), contact.other(), contact.speed(),
                    null);
        } else if (event instanceof SceneEvent.Pour pour) {
            fire(station, pour.pouring() ? Reaction.On.POUR : Reaction.On.POUR_END, pour.node(), pour.target(), 0.0F, null);
        } else if (event instanceof SceneEvent.ControlMoved moved) {
            fire(station, Reaction.On.CONTROL, controlNodes.getOrDefault(moved.control(), moved.control()), null, 0.0F, null);
        } else if (event instanceof SceneEvent.ButtonPressed pressed) {
            fire(station, Reaction.On.BUTTON, controlNodes.getOrDefault(pressed.control(), pressed.control()), null, 0.0F, null);
        }
    }

    /**
     * Plays every reaction to {@code on} whose filters match.
     *
     * @param node  the event's own node (held, clicked, landed), or null
     * @param other the other node (touched, poured into, landed on), or null
     * @param speed pixels a second, 0 for events without one
     * @param point where it happened in scene pixels, or null
     */
    public void fire(ClientStation station, Reaction.On on, String node, String other, float speed, Vector3fc point) {
        fire(station, on, node, null, other, null, speed, point);
    }

    /**
     * As {@link #fire(ClientStation, Reaction.On, String, String, float, Vector3fc)}, where a placed thing's node also answers to
     * its slot ({@code nodeSlot}, {@code otherSlot}: {@code "shelf[2]"}) in the {@code node} and {@code other} filters.
     */
    public void fire(ClientStation station, Reaction.On on, String node, String nodeSlot, String other, String otherSlot, float speed,
            Vector3fc point) {
        Entry[] entries = byEvent.get(on);
        if (entries == null) {
            return;
        }
        float now = station.age();
        for (Entry entry : entries) {
            Reaction reaction = entry.reaction;
            if (now < entry.readyAt || speed < reaction.minSpeed()
                    || reaction.node().isPresent() && !reaction.node().get().equals(node) && !reaction.node().get().equals(nodeSlot)
                    || reaction.other().isPresent() && !reaction.other().get().equals(other) && !reaction.other().get().equals(otherSlot)
                    || reaction.mark().isPresent() && !Exposure.marks(station, other).contains(reaction.mark().get())) {
                continue;
            }
            entry.readyAt = now + reaction.cooldown();
            CompoundTag data = EffectPlayer.data(other, point, speed);
            // An event without a node of its own (the stack in the hand) plays at what it touched.
            String at = node != null ? node : other;
            if (entry.steps != null) {
                station.playSteps(entry.steps, at, data);
            } else {
                Identifier effect = reaction.effect().orElseThrow();
                if (reaction.shared()) {
                    station.shareEffect(effect, at, data);
                } else {
                    station.playLook(effect, at, data);
                }
            }
        }
    }
}
