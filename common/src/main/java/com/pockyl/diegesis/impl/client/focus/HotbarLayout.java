package com.pockyl.diegesis.impl.client.focus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.station.Authority;
import com.pockyl.diegesis.impl.action.StationAction.Target;
import com.pockyl.diegesis.impl.client.ClientStation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The drawer's {@code hotbar} layout: the hotbar where the HUD draws it, now taking clicks, the off hand beside it at a menu
 * station, and over it rows of the other stacks the station can use. Those rows keep the order they had when the station
 * opened: a stack used up leaves an empty slot where it was, a new one comes at the end, and one empty slot is always there
 * to put something down. Everything here is in GUI pixels and drawn as the HUD draws the hotbar, so the swap is not seen.
 */
final class HotbarLayout {
    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier HOTBAR_SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final Identifier OFFHAND_LEFT = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final Identifier OFFHAND_RIGHT = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 22;
    private static final int SLOT_PITCH = 20;
    private static final int COLUMNS = 9;
    /** Between the hotbar and the first row over it. */
    private static final int ROW_GAP = 2;
    private static final int HIGHLIGHT = 0x80FFFFFF;
    /**
     * A slot over the hotbar: a square of its own, dark and see-through with a thin light rim, so that it reads on a sunny
     * table and at night, and does not pass for a second hotbar.
     */
    private static final int SLOT_FILL = 0x9C14161A;
    private static final int SLOT_RIM = 0x55FFFFFF;
    private static final int SLOT_HALF = 9;
    private static final int FIRST_MAIN = 9;

    private static final List<Integer> UPPER = new ArrayList<>();
    private static ClientStation upperFor;

    private HotbarLayout() {
    }

    /** Which slots the rows over the hotbar show; once a frame. */
    static void update(ClientStation station, Inventory inventory) {
        Predicate<ItemStack> filter = station.inventoryFilter();
        if (upperFor != station) {
            upperFor = station;
            UPPER.clear();
        }
        for (int slot = FIRST_MAIN; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && filter.test(stack) && !UPPER.contains(slot)) {
                UPPER.add(slot);
            }
        }
        if (UPPER.stream().noneMatch(slot -> inventory.getItem(slot).isEmpty())) {
            for (int slot = FIRST_MAIN; slot < Inventory.INVENTORY_SIZE; slot++) {
                if (inventory.getItem(slot).isEmpty() && !UPPER.contains(slot)) {
                    UPPER.add(slot);
                    break;
                }
            }
        }
    }

    /** A new station starts its own order. */
    static void reset() {
        UPPER.clear();
        upperFor = null;
    }

    private static int width() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int height() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /** Whether the off hand has a place: at a menu station, while it holds something the station can use. */
    private static boolean showsOffhand(ClientStation station, Inventory inventory) {
        ItemStack stack = inventory.getItem(Inventory.SLOT_OFFHAND);
        return station.type().authority() == Authority.MENU && !stack.isEmpty() && station.inventoryFilter().test(stack);
    }

    private static boolean offhandLeft() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || player.getMainArm() == HumanoidArm.RIGHT;
    }

    /** The GUI centre of an inventory slot, or null when it is not shown. */
    static int[] center(ClientStation station, Inventory inventory, int slot) {
        int middle = width() / 2;
        int bottom = height() - BAR_HEIGHT / 2;
        if (slot >= 0 && slot < COLUMNS) {
            return new int[] {middle - 80 + slot * SLOT_PITCH, bottom};
        }
        if (slot == Inventory.SLOT_OFFHAND) {
            return showsOffhand(station, inventory) ? new int[] {middle + (offhandLeft() ? -109 : 109), bottom} : null;
        }
        int index = UPPER.indexOf(slot);
        if (index < 0) {
            return null;
        }
        int row = index / COLUMNS;
        return new int[] {middle - 80 + index % COLUMNS * SLOT_PITCH, bottom - (row + 1) * BAR_HEIGHT - ROW_GAP};
    }

    /** The inventory slot under a GUI point, -2 over the bars between slots, -1 elsewhere. */
    static int slotAt(ClientStation station, Inventory inventory, double x, double y) {
        for (int slot : shown(station, inventory)) {
            int[] at = center(station, inventory, slot);
            if (at != null && x >= at[0] - 10 && x < at[0] + 10 && y >= at[1] - 10 && y < at[1] + 10) {
                return slot;
            }
        }
        return contains(station, inventory, x, y) ? -2 : -1;
    }

    /** Whether a GUI point lies on the bars. */
    static boolean contains(ClientStation station, Inventory inventory, double x, double y) {
        int left = width() / 2 - BAR_WIDTH / 2;
        int top = height() - BAR_HEIGHT;
        if (x >= left && x < left + BAR_WIDTH && y >= top) {
            return true;
        }
        for (int row = 0; row < rows(); row++) {
            int rowTop = top - (row + 1) * BAR_HEIGHT - ROW_GAP;
            int count = Math.min(COLUMNS, UPPER.size() - row * COLUMNS);
            if (x >= left && x < left + 2 + count * SLOT_PITCH && y >= rowTop && y < rowTop + BAR_HEIGHT) {
                return true;
            }
        }
        int[] off = center(station, inventory, Inventory.SLOT_OFFHAND);
        return off != null && x >= off[0] - 12 && x < off[0] + 12 && y >= off[1] - 12 && y < off[1] + 12;
    }

    private static int rows() {
        return (UPPER.size() + COLUMNS - 1) / COLUMNS;
    }

    private static List<Integer> shown(ClientStation station, Inventory inventory) {
        List<Integer> slots = new ArrayList<>(COLUMNS + UPPER.size() + 1);
        for (int slot = 0; slot < COLUMNS; slot++) {
            slots.add(slot);
        }
        slots.addAll(UPPER);
        if (showsOffhand(station, inventory)) {
            slots.add(Inventory.SLOT_OFFHAND);
        }
        return slots;
    }

    /** Draws the bars as the HUD draws the hotbar, the rows over it in the same look, and the stacks on them. */
    static void render(GuiGraphics graphics, Font font, ClientStation station, Inventory inventory, int hoveredSlot) {
        int left = width() / 2 - BAR_WIDTH / 2;
        int top = height() - BAR_HEIGHT;
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, HOTBAR, left, top, BAR_WIDTH, BAR_HEIGHT);
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION, left - 1 + inventory.getSelectedSlot() * SLOT_PITCH, top - 1, 24, 23);
        for (int slot : UPPER) {
            int[] at = center(station, inventory, slot);
            int x0 = at[0] - SLOT_HALF;
            int y0 = at[1] - SLOT_HALF;
            int x1 = at[0] + SLOT_HALF;
            int y1 = at[1] + SLOT_HALF;
            graphics.fill(x0, y0, x1, y1, SLOT_FILL);
            graphics.fill(x0, y0, x1, y0 + 1, SLOT_RIM);
            graphics.fill(x0, y1 - 1, x1, y1, SLOT_RIM);
            graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, SLOT_RIM);
            graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, SLOT_RIM);
        }
        if (showsOffhand(station, inventory)) {
            int[] off = center(station, inventory, Inventory.SLOT_OFFHAND);
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, offhandLeft() ? OFFHAND_LEFT : OFFHAND_RIGHT, off[0] - (offhandLeft() ? 11 : 18), top - 1, 29, 24);
        }
        for (int slot : shown(station, inventory)) {
            int[] at = center(station, inventory, slot);
            ItemStack stack = inventory.getItem(slot);
            ItemStack spread = FocusInput.get().spreadPreview(new Target.Inventory(slot));
            if (!spread.isEmpty()) {
                // What the drag in progress will leave here, as a vanilla screen shows it.
                stack = spread;
            }
            if (!stack.isEmpty()) {
                // A stack that has just landed here bounces.
                float scale = FocusInput.get().gains().slotScale(slot);
                graphics.pose().pushMatrix();
                graphics.pose().translate(at[0], at[1]);
                graphics.pose().scale(scale, scale);
                graphics.renderItem(stack, -8, -8);
                graphics.renderItemDecorations(font, stack, -8, -8);
                graphics.pose().popMatrix();
            }
            if (slot == hoveredSlot || FocusInput.get().isSpreadTarget(new Target.Inventory(slot))) {
                graphics.fillGradient(at[0] - 8, at[1] - 8, at[0] + 8, at[1] + 8, HIGHLIGHT, HIGHLIGHT);
            }
        }
    }
}
