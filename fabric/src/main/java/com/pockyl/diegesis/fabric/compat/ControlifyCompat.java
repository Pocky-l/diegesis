package com.pockyl.diegesis.fabric.compat;

import com.pockyl.diegesis.api.scene.Box;
import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.focus.FocusInput;
import com.pockyl.diegesis.impl.client.focus.FocusScreen;
import com.pockyl.diegesis.impl.client.focus.Projector;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;
import com.pockyl.diegesis.impl.client.scene.SceneInstance;
import dev.isxander.controlify.api.vmousesnapping.SnapPoint;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.screenop.ScreenProcessorFactory;
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.function.Consumer;

public final class ControlifyCompat {
    private static final int SLOT_SNAP_RANGE = 16;
    private static final int NODE_SNAP_RANGE = 18;

    private ControlifyCompat() {}

    public static void init() {
        ScreenProcessorFactory.registerProvider(FocusScreen.class, FocusScreenProcessor::new);
    }

    public static void collectSnapPoints(Consumer<SnapPoint> consumer) {
        // 1. Gesture/overlay guard: disable snap while busy or covered
        if (FocusScreen.isCovered() || FocusInput.get().isBusy()) {
            return;
        }

        // 2. Inventory slot snap points
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (FocusScreen.isSlotShown(slot)) {
                int[] center = FocusScreen.slotCenter(slot);
                if (center[0] > -1000 && center[1] > -1000) {
                    consumer.accept(new SnapPoint(new Vector2i(center[0], center[1]), SLOT_SNAP_RANGE));
                }
            }
        }

        // 3. Station in-world targets
        ClientStation station = FocusController.get().station();
        if (station == null || station.scene() == null || !FocusController.get().isInteractive()) {
            return;
        }

        SceneInstance scene = station.scene();

        // 3a. Scene inventory slots (in-world layout)
        if (scene.inventoryLayout() != null) {
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                Matrix4f mat = scene.inventorySlot(slot);
                if (mat != null) {
                    Vector3f origin = mat.transformPosition(new Vector3f());
                    Vec3 world = station.toWorld(origin.x, origin.y, origin.z);
                    Vector2f screenPos = Projector.toScreen(world);
                    if (screenPos != null && isOnScreen(screenPos)) {
                        consumer.accept(new SnapPoint(new Vector2i(Math.round(screenPos.x), Math.round(screenPos.y)), SLOT_SNAP_RANGE));
                    }
                }
            }
        }

        boolean carryingItem = Minecraft.getInstance().player != null
                && !Minecraft.getInstance().player.containerMenu.getCarried().isEmpty();

        // 3b. Scene interactable nodes
        for (NodeInstance node : scene.nodes()) {
            if (!node.isVisible() || !node.isEnabled() || node.definition().trigger()) {
                continue;
            }
            NodeDefinition def = node.definition();
            if (FocusInput.isLockedControl(station, def)) {
                continue;
            }
            if (carryingItem) {
                if (!(def.slot().isPresent() || def.dropZone().isPresent() || def.takesItems())) {
                    continue;
                }
            } else {
                boolean interactive = def.isInteractive()
                        || def.control().isPresent()
                        || def.dropZone().isPresent()
                        || def.grabbable().isPresent()
                        || def.strokes()
                        || def.slot().isPresent()
                        || def.output().isPresent()
                        || def.takesItems()
                        || (node.placed() != null && node.placed().pose() != null);
                if (!interactive) {
                    continue;
                }
            }

            Box box = node.collider();
            Vec3 world;
            if (box != null) {
                Vector3f center = new Vector3f(
                        (box.from().x() + box.to().x()) * 0.5f,
                        (box.from().y() + box.to().y()) * 0.5f,
                        (box.from().z() + box.to().z()) * 0.5f
                );
                Vector3f stationSpace = node.matrix().transformPosition(center);
                world = station.toWorld(stationSpace.x, stationSpace.y, stationSpace.z);
            } else {
                world = node.worldPosition();
            }

            Vector2f screenPos = Projector.toScreen(world);
            if (screenPos != null && isOnScreen(screenPos)) {
                consumer.accept(new SnapPoint(new Vector2i(Math.round(screenPos.x), Math.round(screenPos.y)), NODE_SNAP_RANGE));
            }
        }
    }

    private static boolean isOnScreen(Vector2f pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return false;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        return pos.x >= 0 && pos.x <= w && pos.y >= 0 && pos.y <= h;
    }

    public static class FocusScreenProcessor extends ScreenProcessor<FocusScreen> {
        public FocusScreenProcessor(FocusScreen screen) {
            super(screen);
        }

        @Override
        public VirtualMouseBehaviour virtualMouseBehaviour() {
            return VirtualMouseBehaviour.ENABLED;
        }
    }
}

