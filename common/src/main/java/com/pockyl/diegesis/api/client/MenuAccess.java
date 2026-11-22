package com.pockyl.diegesis.api.client;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;

/**
 * The open vanilla menu of a menu-backed station ({@code Authority.MENU}), from {@link StationView#menu()}. Everything sent
 * through it is what the vanilla screen would have sent.
 */
public interface MenuAccess {
    /** The menu itself: cast it for what its type offers ({@code AbstractFurnaceMenu.getBurnProgress()}). */
    AbstractContainerMenu handle();

    /** Ticks since the server opened the menu. */
    int ticksOpen();

    /** A click on a menu slot, as the vanilla screen makes it: predicted here, sent to the server. */
    void click(int menuSlot, int button, ClickType type);

    /**
     * Shift-clicks an inventory slot into the station, the way vanilla routes it. Called before the menu is open or right
     * after, it waits until the menu has been open the binding's {@code autoDelayTicks}; at most one such click goes out a tick.
     */
    void quickMoveFromInventory(int inventorySlot);

    /** A menu button (a stonecutter's result, an enchantment): {@code ServerboundContainerButtonClickPacket}. */
    void pressButton(int id);

    /**
     * Lays a recipe into the menu as the vanilla recipe book does ({@code ServerboundPlaceRecipePacket}): one set, or as many as fit
     * with {@code all}. The server takes only recipes the player has unlocked, in menus with a recipe book. Since Minecraft 1.21.2 a
     * client knows recipes only as the recipe book shows them: {@code recipe} is the id of one of its entries
     * ({@code RecipeDisplayEntry.id()}), no longer the recipe's own id.
     */
    default void placeRecipe(net.minecraft.world.item.crafting.display.RecipeDisplayId recipe, boolean all) {
    }
}
