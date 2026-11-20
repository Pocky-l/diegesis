package com.pockyl.diegesis.impl.client.fx;

import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.TankBinding;
import com.pockyl.diegesis.api.scene.fx.Marks;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;

import java.util.LinkedHashSet;
import java.util.Set;

/** What touching a node means: the marks it carries, and those its liquid gives it. */
public final class Exposure {
    private Exposure() {
    }

    /** The marks of a node now: its own ({@code feedback.marks}), and {@code diegesis:wet} plus each liquid's id while its tank holds any. */
    public static Set<Identifier> marks(ClientStation station, String node) {
        NodeInstance instance = node == null || station.scene() == null ? null : station.scene().node(node);
        if (instance == null) {
            return Set.of();
        }
        NodeDefinition definition = instance.definition();
        TankBinding tank = definition.tank().orElse(null);
        if (tank == null) {
            return definition.feedback().marks().isEmpty() ? Set.of() : Set.copyOf(definition.feedback().marks());
        }
        Set<Identifier> marks = new LinkedHashSet<>(definition.feedback().marks());
        TankContents contents = station.state().getTank(tank.tank());
        if (!contents.isEmpty()) {
            marks.add(Marks.WET);
            contents.portions().forEach(portion -> marks.add(portion.liquid()));
        }
        return marks;
    }

    /** Whether a node may carry marks at all: its own, or a tank's. Nodes that cannot are skipped by the exposure scan. */
    public static boolean canBeMarked(NodeDefinition node) {
        return !node.feedback().marks().isEmpty() || node.tank().isPresent();
    }
}
