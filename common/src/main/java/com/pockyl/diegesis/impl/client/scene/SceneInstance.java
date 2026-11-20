package com.pockyl.diegesis.impl.client.scene;

import net.minecraft.world.entity.player.Inventory;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.Box;
import com.pockyl.diegesis.api.scene.ControlBinding;
import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.scene.InventoryLayout;
import com.pockyl.diegesis.api.scene.LightDefinition;
import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.scene.SlotBinding;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.StationSchema.SlotGroup;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.impl.client.ClientStation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** The node tree of one shown station, built from its {@link SceneDefinition}. */
public final class SceneInstance {
    private final ClientStation station;
    private final SceneDefinition definition;
    private final List<NodeInstance> roots = new ArrayList<>();
    private final Map<String, NodeInstance> byId = new LinkedHashMap<>();
    /** One node per slot of the placing's group: a scene file does not know the items that will stand there. */
    private final List<NodeInstance> placed = new ArrayList<>();
    /** Per surface of the placing, how what stands on it is moved now (identity: where the scene has it). */
    private Matrix4f[] surfaceMoves = new Matrix4f[0];
    /** Solid items of the placing ({@code placing.physics}); null without. */
    private PlacedPhysics physics;
    /** Whether the placing's items are drawn (see {@code StationView.setPlacingVisible}). */
    private boolean placingVisible = true;
    private boolean placingReachable = true;
    /** Per surface of the placing, whether it is shut away (see {@code StationView.setSurfaceVisible}). */
    private boolean[] surfaceHidden = new boolean[0];
    private PlacingOptions placingOptions;
    /** Station-space matrices of the 36 inventory slots laid out in the scene; null without a layout. */
    private final Matrix4f[] inventorySlots;

    public SceneInstance(ClientStation station, SceneDefinition definition) {
        this.station = station;
        this.definition = definition;
        this.inventorySlots = definition.inventory().map(SceneInstance::slotMatrices).orElse(null);
        for (NodeDefinition node : definition.nodes()) {
            NodeInstance instance = new NodeInstance(this, node, null);
            roots.add(instance);
            index(instance);
        }
        definition.placing().ifPresent(this::addPlaced);
    }

    private void addPlaced(PlacingOptions options) {
        SlotGroup group = station.type().schema().slotGroups().get(options.group());
        if (group == null || !group.rules().handPlaced()) {
            DiegesisMod.LOGGER.error("Station {}: \"placing\" needs a slot group \"{}\" with placed() rules", station.type().id(), options.group());
            return;
        }
        placingOptions = options;
        surfaceMoves = new Matrix4f[options.surfaces().size()];
        surfaceHidden = new boolean[options.surfaces().size()];
        for (int i = 0; i < surfaceMoves.length; i++) {
            surfaceMoves[i] = new Matrix4f();
        }
        for (int i = 0; i < group.size(); i++) {
            NodeDefinition node = NodeDefinition.builder("placed_" + i)
                    .slot(new SlotBinding(group.ref(i), SlotBinding.ItemDisplay.UPRIGHT, 6.0F, java.util.Optional.empty(), SlotBinding.Counts.ALWAYS, true))
                    .light(new LightDefinition(0, options.lightRadius(), options.lightColor(), 0.12F, new Vector3f())).hidden().build();
            NodeInstance instance = new NodeInstance(this, node, null);
            instance.setPlaced(new PlacedItem(group.ref(i), options, surfaceMoves, surfaceHidden, new PlacedItem.Spots() {
                @Override
                public com.pockyl.diegesis.api.state.ItemPose find(PlacedItem item, PlacedShape shape) {
                    return freeSpot(item, shape);
                }

                @Override
                public com.pockyl.diegesis.api.scene.PlaceSurface surfaceOf(PlacingOptions placing, com.pockyl.diegesis.api.state.ItemPose pose) {
                    return SceneInstance.this.surfaceOf(placing, pose);
                }
            }));
            placed.add(instance);
            roots.add(instance);
            index(instance);
        }
        options.physics().ifPresent(value -> physics = new PlacedPhysics(new PhysicsHost(List.copyOf(placed)), options, value, placed.size()));
    }

    /** The scene as its physics sees it: the placed items by slot, the client's state they are shown from, the station. */
    private final class PhysicsHost implements PlacedPhysics.Host {
        private final List<NodeInstance> items;

        PhysicsHost(List<NodeInstance> items) {
            this.items = items;
        }

        private PlacedItem item(int index) {
            return items.get(index).placed();
        }

        @Override
        public net.minecraft.world.item.ItemStack stack(int index) {
            return item(index).stack();
        }

        @Override
        public PlacedPhysics.Solid solid(int index) {
            PlacedItem item = item(index);
            return item.pose() == null || item.shape() == null ? null : item.shape().solid();
        }

        @Override
        public com.pockyl.diegesis.api.state.ItemPose pose(int index) {
            return item(index).pose();
        }

        @Override
        public void setPose(int index, com.pockyl.diegesis.api.state.ItemPose pose) {
            station.state().setPose(item(index).slot(), pose);
            item(index).sync(station.state());
        }

        @Override
        public com.pockyl.diegesis.api.state.SlotRef slot(int index) {
            return item(index).slot();
        }

        @Override
        public com.pockyl.diegesis.api.scene.PlaceSurface surfaceOf(com.pockyl.diegesis.api.state.ItemPose pose) {
            return SceneInstance.this.surfaceOf(placingOptions, pose);
        }

        @Override
        public boolean surfaceMoved(int index) {
            return surfaceMove(index) != null;
        }

        @Override
        public boolean focused() {
            return station.isFocused();
        }

        @Override
        public void perform(com.pockyl.diegesis.impl.action.StationAction action) {
            station.perform(action);
        }

        @Override
        public void hideWhileTaken(int index, long millis) {
            item(index).hideWhileTaken(millis);
        }

        @Override
        public void settled(int index, int under, float speed, float mass, boolean flat) {
            com.pockyl.diegesis.api.state.SlotRef slot = item(index).slot();
            com.pockyl.diegesis.api.state.SlotRef on = under >= 0 ? item(under).slot() : null;
            com.pockyl.diegesis.api.state.ItemPose pose = item(index).pose();
            org.joml.Vector3f point = pose == null ? null : new org.joml.Vector3f(pose.x(), pose.y(), pose.z());
            station.onEvent(new com.pockyl.diegesis.api.event.SceneEvent.Landed(slot, on, speed, mass, flat),
                    com.pockyl.diegesis.api.scene.fx.Reaction.On.LANDED, items.get(index).id(), slot.toString(),
                    under >= 0 ? items.get(under).id() : null, on == null ? null : on.toString(), speed, point);
        }

        @Override
        public void impact(int index, int other, float speed, float mass, org.joml.Vector3fc point) {
            com.pockyl.diegesis.api.state.SlotRef slot = item(index).slot();
            com.pockyl.diegesis.api.state.SlotRef hit = other >= 0 ? item(other).slot() : null;
            org.joml.Vector3f at = new org.joml.Vector3f(point);
            station.onEvent(new com.pockyl.diegesis.api.event.SceneEvent.Impact(slot, hit, speed, mass, at),
                    com.pockyl.diegesis.api.scene.fx.Reaction.On.IMPACT, items.get(index).id(), slot.toString(),
                    other >= 0 ? items.get(other).id() : null, hit == null ? null : hit.toString(), speed, at);
        }

        @Override
        public float impactMinSpeed() {
            return definition.feedback().impactMinSpeed();
        }

        @Override
        public void fellOff(int index) {
            com.pockyl.diegesis.api.state.SlotRef slot = item(index).slot();
            station.onEvent(new com.pockyl.diegesis.api.event.SceneEvent.FellOff(slot), com.pockyl.diegesis.api.scene.fx.Reaction.On.FELL_OFF,
                    items.get(index).id(), slot.toString(), null, null, 0.0F, null);
        }

        @Override
        public void landed(int index, float speed, float mass, boolean flat) {
            com.pockyl.diegesis.api.state.ItemPose pose = item(index).pose();
            if (pose != null) {
                com.pockyl.diegesis.impl.client.focus.UiSounds.landed(station.toWorld(pose.x(), pose.y(), pose.z()), item(index).stack(), speed,
                        mass, flat);
            }
        }
    }

    /**
     * A free spot for an item that came without a place, among the items standing on the surfaces shown now (on any surface when
     * all are shut away); with no room left, the old rows by slot number, so that it is still somewhere.
     */
    private com.pockyl.diegesis.api.state.ItemPose freeSpot(PlacedItem item, PlacedShape shape) {
        com.pockyl.diegesis.api.state.ItemPose spot = freeSpot(shape, item);
        return spot != null ? spot : Placing.fallback(placingOptions, item.slot().index(), shape, surfaceHidden);
    }

    private com.pockyl.diegesis.api.state.ItemPose freeSpot(PlacedShape shape, PlacedItem except) {
        PlacingOptions options = placingOptions;
        List<com.pockyl.diegesis.api.scene.PlaceSurface> open = new ArrayList<>();
        for (int i = 0; i < options.surfaces().size(); i++) {
            if (isSurfaceVisible(i)) {
                open.add(options.surfaces().get(i));
            }
        }
        if (open.isEmpty()) {
            open = options.surfaces();
        }
        float least = options.physics().map(PlacedPhysics::flatHalf).orElse(0.0F);
        List<float[]> taken = new ArrayList<>();
        for (NodeInstance node : placed) {
            PlacedItem other = node.placed();
            com.pockyl.diegesis.api.state.ItemPose pose = other.pose();
            if (other != except && pose != null && other.shape() != null) {
                taken.add(Placing.boxOf(options, other.shape(), pose, other.shape().isFlat() ? least : 0.0F));
            }
        }
        float own = shape.isFlat() ? least : 0.0F;
        float cameraX = definition().camera().position().x();
        if (!Float.isNaN(station.cameraMirror())) {
            cameraX = 2.0F * station.cameraMirror() - cameraX;
        }
        List<Placing.PileTop> piles = new ArrayList<>();
        if (except != null && shape.isFlat()) {
            // Where flat things lie, the same thing goes on a pile of it: the top of each such pile, nothing on it yet.
            for (NodeInstance node : placed) {
                PlacedItem other = node.placed();
                com.pockyl.diegesis.api.state.ItemPose pose = other.pose();
                if (other == except || pose == null || other.shape() == null || !other.shape().isFlat()
                        || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(other.stack(), except.stack())) {
                    continue;
                }
                com.pockyl.diegesis.api.scene.PlaceSurface surface = surfaceOf(options, pose);
                int index = other.slot().index();
                boolean covered = placed.stream().anyMatch(n -> n.placed() != except && n.placed().pose() != null && n.placed().pose().on() == index);
                if (surface != null && surface.flatLies() && open.contains(surface) && !covered) {
                    piles.add(new Placing.PileTop(index, pose));
                }
            }
        }
        return Placing.freeSpot(options, shape, open, taken, own, cameraX, piles);
    }

    /** Whether the placing has room for {@code stack} somewhere on its surfaces shown now (see {@code StationView.hasRoomFor}). */
    public boolean hasRoomFor(net.minecraft.world.item.ItemStack stack) {
        if (placingOptions == null || stack.isEmpty()) {
            return false;
        }
        PlacedShape shape = PlacedShape.of(stack, net.minecraft.client.Minecraft.getInstance().level, placingOptions.blockModels(),
                placingOptions.framePose());
        return freeSpot(shape, null) != null;
    }

    /** The placing's physics, or null. */
    public PlacedPhysics physics() {
        return physics;
    }

    public void setPlacingVisible(boolean visible) {
        placingVisible = visible;
    }

    public boolean isPlacingVisible() {
        return placingVisible;
    }

    public void setPlacingReachable(boolean reachable) {
        placingReachable = reachable;
    }

    /** Whether the placing's items can be picked and its surfaces take items (see {@code StationView.setPlacingReachable}). */
    public boolean isPlacingReachable() {
        return placingReachable;
    }

    public void setSurfaceVisible(int surface, boolean visible) {
        if (surface >= 0 && surface < surfaceHidden.length) {
            surfaceHidden[surface] = !visible;
        }
    }

    /** Whether surface {@code index} of the placing is shown and takes items. */
    public boolean isSurfaceVisible(int index) {
        return index < 0 || index >= surfaceHidden.length || !surfaceHidden[index];
    }

    /** Moves what stands on a surface of the placing (see {@code StationView.setSurfaceTransform}). */
    public void setSurfaceTransform(int surface, org.joml.Matrix4fc transform) {
        if (surface >= 0 && surface < surfaceMoves.length) {
            surfaceMoves[surface].set(transform);
        }
    }

    /** How surface {@code index} of the placing is moved now (a door it is on swung open), or null while it stays put. */
    public Matrix4f surfaceMove(int index) {
        if (index < 0 || index >= surfaceMoves.length) {
            return null;
        }
        Matrix4f move = surfaceMoves[index];
        return (move.properties() & org.joml.Matrix4fc.PROPERTY_IDENTITY) != 0 ? null : move;
    }

    /** The surface an item standing at {@code pose} belongs to: on another item, that one's (see {@code Placing.surfaceOf}). */
    public com.pockyl.diegesis.api.scene.PlaceSurface surfaceOf(PlacingOptions options, com.pockyl.diegesis.api.state.ItemPose pose) {
        return Placing.surfaceOf(options, pose, index -> index >= 0 && index < placed.size() ? placed.get(index).placed().pose() : null);
    }

    /** The move of the surface an item standing at {@code pose} belongs to, or null. */
    public Matrix4f surfaceMoveOf(PlacingOptions options, com.pockyl.diegesis.api.state.ItemPose pose) {
        com.pockyl.diegesis.api.scene.PlaceSurface surface = surfaceOf(options, pose);
        return surface == null ? null : surfaceMove(options.surfaces().indexOf(surface));
    }

    private boolean onShownSurface(PlacedItem item) {
        com.pockyl.diegesis.api.state.ItemPose pose = item.pose();
        if (pose == null) {
            return true;
        }
        com.pockyl.diegesis.api.scene.PlaceSurface surface = surfaceOf(item.options(), pose);
        return surface == null || isSurfaceVisible(item.options().surfaces().indexOf(surface));
    }

    /** The nodes of the placing's slots, also those that are empty now. */
    public List<NodeInstance> placedNodes() {
        return placed;
    }

    private void index(NodeInstance node) {
        byId.put(node.id(), node);
        node.children().forEach(this::index);
    }

    public ClientStation station() {
        return station;
    }

    public SceneDefinition definition() {
        return definition;
    }

    public NodeInstance node(String id) {
        return byId.get(id);
    }

    /** Every node, parents before children. */
    public Iterable<NodeInstance> nodes() {
        return Collections.unmodifiableCollection(byId.values());
    }

    private static Matrix4f[] slotMatrices(InventoryLayout layout) {
        Matrix4f grid = layout.matrix();
        Matrix4f[] slots = new Matrix4f[Inventory.INVENTORY_SIZE];
        for (int slot = 0; slot < slots.length; slot++) {
            Vector3f offset = layout.slotOffset(slot);
            slots[slot] = new Matrix4f(grid).translate(offset);
        }
        return slots;
    }

    /** The scene's inventory layout, or null when the inventory is the panel on the screen. */
    public InventoryLayout inventoryLayout() {
        return definition.inventory().orElse(null);
    }

    /** The station-space matrix of an inventory slot laid out in the scene. */
    public Matrix4f inventorySlot(int slot) {
        return inventorySlots[slot];
    }

    /** The nearest inventory slot under a ray in station space, or null (also without a layout). */
    public InventoryHit pickInventory(Vector3fc origin, Vector3fc direction) {
        if (inventorySlots == null) {
            return null;
        }
        float half = definition.inventory().get().spacing() / 2.0F;
        Box box = new Box(new Vector3f(-half, 0.0F, -half), new Vector3f(half, 0.6F, half));
        InventoryHit best = null;
        Matrix4f inverse = new Matrix4f();
        Vector3f localOrigin = new Vector3f();
        Vector3f localDirection = new Vector3f();
        for (int slot = 0; slot < inventorySlots.length; slot++) {
            inventorySlots[slot].invert(inverse);
            inverse.transformPosition(origin, localOrigin);
            inverse.transformDirection(direction, localDirection);
            float t = intersect(box, localOrigin, localDirection);
            if (t >= 0.0F && (best == null || t < best.distance())) {
                best = new InventoryHit(slot, t);
            }
        }
        return best;
    }

    /** An inventory slot under a ray: {@code distance} is the ray parameter, as for {@link Hit}. */
    public record InventoryHit(int slot, float distance) {
    }

    /** Moves controls to the state's values and recomputes every node's matrix. */
    public void update(StationState state, float dt) {
        for (NodeInstance node : byId.values()) {
            node.definition().control().ifPresent(binding -> {
                ControlSpec spec = state.schema().controls().get(binding.control());
                if (spec != null && binding.type() != ControlBinding.Type.BUTTON) {
                    node.setControlTarget(spec.fraction(state.getControl(binding.control())));
                }
            });
        }
        for (NodeInstance node : placed) {
            boolean there = node.placed().sync(state);
            node.setVisible(there && placingVisible && onShownSurface(node.placed()));
            node.setLight(there && node.placed().options().itemLight() ? node.placed().shape().light() : 0);
        }
        if (physics != null) {
            physics.update(dt);
        }
        for (NodeInstance root : roots) {
            root.update(dt);
        }
    }

    /**
     * The nearest visible node hit by a ray in station space that passes {@code filter}, or null.
     *
     * @param origin    ray start in station pixels
     * @param direction ray direction in station pixels (any length)
     */
    public Hit pick(Vector3fc origin, Vector3fc direction, Predicate<NodeInstance> filter) {
        Hit best = null;
        Matrix4f inverse = new Matrix4f();
        Vector3f localOrigin = new Vector3f();
        Vector3f localDirection = new Vector3f();
        List<NodeInstance> items = new ArrayList<>();
        List<Placing.Pickable> boxes = new ArrayList<>();
        for (NodeInstance node : byId.values()) {
            Box box = node.collider();
            if (box == null || !node.isVisible() || !filter.test(node) || !placingReachable && node.placed() != null) {
                continue;
            }
            if (node.placed() != null) {
                // Placed items are found by their own hit boxes, all at once: the exact ones first, then near misses.
                Placing.Pickable pickable = node.placed().pickable(node.placed().options().handling().pickMin());
                if (pickable != null) {
                    items.add(node);
                    boxes.add(pickable);
                }
                continue;
            }
            node.pickMatrix().invert(inverse);
            inverse.transformPosition(origin, localOrigin);
            inverse.transformDirection(direction, localDirection);
            float t = intersect(box, localOrigin, localDirection);
            if (t >= 0.0F && node.definition().precise()) {
                t = node.preciseHit(localOrigin, localDirection, t);
            }
            // The parameter t is the same in every space: both ends of the ray are transformed linearly.
            if (t >= 0.0F && (best == null || t < best.distance())) {
                Vector3f point = new Vector3f(direction).mul(t).add(origin);
                best = new Hit(node, t, point);
            }
        }
        Placing.Picked item = boxes.isEmpty() ? null
                : Placing.pick(boxes, origin, direction, best == null ? Float.MAX_VALUE : best.distance());
        if (item != null) {
            best = new Hit(items.get(item.index()), item.distance(), new Vector3f(direction).mul(item.distance()).add(origin));
        }
        return best;
    }

    /** Ray parameter of the entry into a box (slab method), or -1. */
    private static float intersect(Box box, Vector3fc origin, Vector3fc direction) {
        float tMin = 0.0F;
        float tMax = Float.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            float o = origin.get(axis);
            float d = direction.get(axis);
            float min = box.from().get(axis);
            float max = box.to().get(axis);
            if (Math.abs(d) < 1.0e-7F) {
                if (o < min || o > max) {
                    return -1.0F;
                }
                continue;
            }
            float t1 = (min - o) / d;
            float t2 = (max - o) / d;
            if (t1 > t2) {
                float swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return -1.0F;
            }
        }
        return tMin;
    }

    /** A node under a ray: {@code distance} is the ray parameter, {@code point} the hit point in station space. */
    public record Hit(NodeInstance node, float distance, Vector3f point) {
    }
}
