package com.pockyl.diegesis.impl.client.fx;

import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.TankBinding;
import com.pockyl.diegesis.api.scene.fx.Marks;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;

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
        return Marks.of(definition, tank == null ? null : station.state().getTank(tank.tank()));
    }

    /** Whether a node may carry marks at all: its own, or a tank's. Nodes that cannot are skipped by the exposure scan. */
    public static boolean canBeMarked(NodeDefinition node) {
        return !node.feedback().marks().isEmpty() || node.tank().isPresent();
    }
}
