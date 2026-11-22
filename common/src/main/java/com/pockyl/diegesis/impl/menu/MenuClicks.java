package com.pockyl.diegesis.impl.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.station.MenuBinding;
import com.pockyl.diegesis.impl.action.StationAction.ClickKind;
import com.pockyl.diegesis.impl.action.StationAction.Target;
import com.pockyl.diegesis.impl.action.StationAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what a player does at a menu-backed station into the clicks the vanilla screen would make in the same menu: the
 * same slot numbers, buttons and click types. Plain logic over a menu, so the server's game tests feed the result to real
 * menus.
 */
public final class MenuClicks {
    /** The slot number vanilla uses for "outside the screen". */
    public static final int OUTSIDE = AbstractContainerMenu.SLOT_CLICKED_OUTSIDE;

    private MenuClicks() {
    }

    /** One click as {@code MultiPlayerGameMode.handleInventoryMouseClick} takes it. */
    public record Click(int slot, int button, ClickType type) {
    }

    /** The menu slot a target shows, or -1. Inventory slots are found by their container slot, never by arithmetic. */
    public static int slotOf(AbstractContainerMenu menu, MenuBinding binding, Inventory inventory, Target target) {
        if (target instanceof Target.Station station) {
            MenuBinding.SlotRange range = binding.slots().get(station.slot().group());
            int index = station.slot().index();
            return range == null || index < 0 || index >= range.count() ? -1 : checked(menu, range.first() + index);
        }
        if (target instanceof Target.Output output) {
            Integer slot = binding.outputs().get(output.output());
            return slot == null ? -1 : checked(menu, slot);
        }
        if (target instanceof Target.Inventory inv) {
            return inventorySlot(menu, inventory, inv.index());
        }
        return -1;
    }

    /** The menu slot that shows inventory slot {@code index} (0-35 main, 36-39 armour, 40 off hand), or -1. */
    public static int inventorySlot(AbstractContainerMenu menu, Inventory inventory, int index) {
        for (Slot slot : menu.slots) {
            if (slot.container == inventory && slot.getContainerSlot() == index) {
                return slot.index;
            }
        }
        return -1;
    }

    private static int checked(AbstractContainerMenu menu, int slot) {
        return slot >= 0 && slot < menu.slots.size() ? slot : -1;
    }

    /**
     * The clicks for a station action, or an empty list when the menu cannot do it (a control, a pour: menu stations have
     * only items).
     */
    public static List<Click> of(AbstractContainerMenu menu, MenuBinding binding, Inventory inventory, StationAction action) {
        List<Click> clicks = new ArrayList<>();
        if (action instanceof StationAction.Click click) {
            if (click.kind() == ClickKind.PUT_AWAY) {
                return putAway(menu, inventory);
            }
            if (click.target() instanceof Target.Inventory inv && inv.index() == Inventory.SLOT_OFFHAND
                    && inventorySlot(menu, inventory, Inventory.SLOT_OFFHAND) < 0) {
                return offhand(menu, inventory, click.kind(), click.button());
            }
            int slot = slotOf(menu, binding, inventory, click.target());
            if (slot < 0) {
                return clicks;
            }
            ClickType type = switch (click.kind()) {
                case QUICK_MOVE, SPILL -> ClickType.QUICK_MOVE;
                case PICKUP_ALL -> ClickType.PICKUP_ALL;
                default -> ClickType.PICKUP;
            };
            clicks.add(new Click(slot, click.kind() == ClickKind.PICKUP_ALL ? 0 : click.button(), type));
        } else if (action instanceof StationAction.Spread spread) {
            // Vanilla's three-phase drag: start, one per slot, end; button 0 shares evenly, 1 puts one into each, 2 (creative
            // mode's middle drag) a full stack into each, which the server allows only to a player in creative mode.
            int kind = spread.button() == 1 || spread.button() == 2 ? spread.button() : 0;
            List<Integer> slots = new ArrayList<>();
            for (Target target : spread.targets()) {
                int slot = slotOf(menu, binding, inventory, target);
                if (slot >= 0 && !slots.contains(slot)) {
                    slots.add(slot);
                }
            }
            if (slots.isEmpty()) {
                return clicks;
            }
            clicks.add(new Click(OUTSIDE, AbstractContainerMenu.getQuickcraftMask(0, kind), ClickType.QUICK_CRAFT));
            for (int slot : slots) {
                clicks.add(new Click(slot, AbstractContainerMenu.getQuickcraftMask(1, kind), ClickType.QUICK_CRAFT));
            }
            clicks.add(new Click(OUTSIDE, AbstractContainerMenu.getQuickcraftMask(2, kind), ClickType.QUICK_CRAFT));
        }
        return clicks;
    }

    /**
     * A click on the off hand in a menu without an off-hand slot (a crafting table's): vanilla's off-hand swap with an empty
     * inventory slot does it. Left or right, the whole stack goes: taken onto the cursor, put from it, swapped with it, or
     * merged onto it; shift sends it into the station as from that slot. Nothing when no inventory slot is empty.
     */
    public static List<Click> offhand(AbstractContainerMenu menu, Inventory inventory, ClickKind kind, int button) {
        List<Click> clicks = new ArrayList<>();
        int free = freeSlot(menu, inventory);
        ItemStack held = inventory.getItem(Inventory.SLOT_OFFHAND);
        ItemStack carried = menu.getCarried();
        if (free < 0 || held.isEmpty() && carried.isEmpty()) {
            return clicks;
        }
        Click swap = new Click(free, Inventory.SLOT_OFFHAND, ClickType.SWAP);
        if (kind == ClickKind.QUICK_MOVE) {
            if (!held.isEmpty()) {
                clicks.add(swap);
                clicks.add(new Click(free, 0, ClickType.QUICK_MOVE));
                clicks.add(swap);
            }
        } else if (kind == ClickKind.PICKUP) {
            if (carried.isEmpty()) {
                clicks.add(swap);
                clicks.add(new Click(free, 0, ClickType.PICKUP));
            } else if (held.isEmpty()) {
                clicks.add(new Click(free, 0, ClickType.PICKUP));
                clicks.add(swap);
            } else if (ItemStack.isSameItemSameComponents(held, carried)) {
                clicks.add(swap);
                clicks.add(new Click(free, button == 1 ? 1 : 0, ClickType.PICKUP));
                clicks.add(swap);
            } else {
                clicks.add(new Click(free, 0, ClickType.PICKUP));
                clicks.add(swap);
                clicks.add(new Click(free, 0, ClickType.PICKUP));
            }
        }
        return clicks;
    }

    /** The menu slot of the first empty inventory slot, the main rows before the hotbar; -1 when there is none. */
    public static int freeSlot(AbstractContainerMenu menu, Inventory inventory) {
        for (int i = 9; i < Inventory.INVENTORY_SIZE + 9; i++) {
            int index = i % Inventory.INVENTORY_SIZE;
            if (inventory.getItem(index).isEmpty()) {
                int slot = inventorySlot(menu, inventory, index);
                if (slot >= 0) {
                    return slot;
                }
            }
        }
        return -1;
    }

    /**
     * Puts the stack on the cursor back into the inventory by plain clicks: onto stacks of the same item first, then into the
     * first empty slot, hotbar before the rest, as vanilla's own moves fill it. Clicks are planned on a copy of the counts.
     */
    public static List<Click> putAway(AbstractContainerMenu menu, Inventory inventory) {
        List<Click> clicks = new ArrayList<>();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return clicks;
        }
        int left = carried.getCount();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            order.add(i);
        }
        for (int i = 9; i < 36; i++) {
            order.add(i);
        }
        for (int index : order) {
            ItemStack there = inventory.getItem(index);
            if (left > 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, carried)) {
                int room = Math.min(there.getMaxStackSize(), inventory.getMaxStackSize()) - there.getCount();
                int slot = inventorySlot(menu, inventory, index);
                if (room > 0 && slot >= 0) {
                    clicks.add(new Click(slot, 0, ClickType.PICKUP));
                    left -= room;
                }
            }
        }
        for (int index : order) {
            if (left > 0 && inventory.getItem(index).isEmpty()) {
                int slot = inventorySlot(menu, inventory, index);
                if (slot >= 0) {
                    clicks.add(new Click(slot, 0, ClickType.PICKUP));
                    left = 0;
                }
            }
        }
        return clicks;
    }
}
