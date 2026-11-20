package com.pockyl.diegesis.impl.client.fx;

import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.fx.Reaction;
import com.pockyl.diegesis.impl.client.ClientStation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one carried thing (a node, or the stack in the hand) is exposed to: for each mark, the marked nodes it touches now. Tells
 * {@code SceneEvent.Exposed} when the first node with a mark is touched, and its end when the last one is left.
 */
public final class ExposureTracker {
    /** The carried node, or null for the stack in the hand. */
    private final String node;
    private final Map<Identifier, Set<String>> sources = new LinkedHashMap<>();

    public ExposureTracker(String node) {
        this.node = node;
    }

    /** The carried thing started touching {@code source}. */
    public void touch(ClientStation station, String source) {
        for (Identifier mark : Exposure.marks(station, source)) {
            Set<String> touched = sources.computeIfAbsent(mark, key -> new LinkedHashSet<>());
            if (touched.isEmpty()) {
                tell(station, mark, source, true);
            }
            touched.add(source);
        }
    }

    /** It stopped touching {@code source}. */
    public void leave(ClientStation station, String source) {
        List<Identifier> ended = new ArrayList<>();
        Iterator<Map.Entry<Identifier, Set<String>>> iterator = sources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Identifier, Set<String>> entry = iterator.next();
            if (entry.getValue().remove(source) && entry.getValue().isEmpty()) {
                iterator.remove();
                ended.add(entry.getKey());
            }
        }
        for (Identifier mark : ended) {
            tell(station, mark, source, false);
        }
    }

    /** It is let go, put away or gone: every exposure ends. */
    public void clear(ClientStation station) {
        if (sources.isEmpty()) {
            return;
        }
        List<Map.Entry<Identifier, Set<String>>> open = new ArrayList<>(sources.entrySet());
        sources.clear();
        for (Map.Entry<Identifier, Set<String>> entry : open) {
            String last = null;
            for (String source : entry.getValue()) {
                last = source;
            }
            tell(station, entry.getKey(), last, false);
        }
    }

    private void tell(ClientStation station, Identifier mark, String source, boolean exposed) {
        station.onEvent(new SceneEvent.Exposed(node, mark, source, exposed), exposed ? Reaction.On.EXPOSED : Reaction.On.UNEXPOSED, node,
                null, source, null, 0.0F, null);
    }
}
