package com.pockyl.diegesis.impl.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.MenuRefusal;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.MenuBinding;
import com.pockyl.diegesis.impl.action.StationAction;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.menu.MenuClicks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The client side of a menu-backed station: the use goes to the server as vanilla sends it, the menu it opens is taken
 * without its screen ({@link #onOpenScreen}), the station's slots read the menu, every hand move is a vanilla click, and
 * leaving closes the menu. Rules that keep the server's menu and the client's in step:
 * <ul>
 * <li>An open that arrives after the player left is closed at once: otherwise the server keeps a menu the client does not
 * have, and the inventory screen's clicks (container 0) go nowhere.</li>
 * <li>A use the server does not answer in time ends the station without a word.</li>
 * <li>Only the expected menu types are taken; any other opens its own screen, and the block is left to it while it stays as
 * it was ({@link #answeredElsewhere}).</li>
 * <li>No automatic click before the menu has been open its delay, and at most one a tick.</li>
 * </ul>
 */
public final class MenuSession implements MenuAccess {
    private static final MenuSession INSTANCE = new MenuSession();

    private record Expect(ClientStation station, MenuBinding binding, int armedAt) {
    }

    /** A use whose station the player left before its menu arrived: the menu is closed as it comes. */
    private record Stale(MenuBinding binding, int armedAt) {
    }

    /** The use of a station gone over to, waiting for the answer to an earlier use. */
    private record PendingUse(ClientStation station, Runnable use) {
    }

    private Expect expect;
    private final Deque<Stale> stale = new ArrayDeque<>();
    /**
     * The container of a stale answer left open on the server because a newer use was already on its way (-1: none): that use's
     * menu replaces it there; if none comes, it is closed then.
     */
    private int staleOpen = -1;
    private PendingUse pendingUse;
    private ClientStation station;
    private MenuBinding binding;
    private AbstractContainerMenu menu;
    /** Whether the open menu was already found shorter than the server's (logged once a menu). */
    private boolean shortLogged;
    /**
     * The server is done with the menu: it closed it, or opened another one (which closes it there). The client's open
     * menu tells nothing: a screen with a recipe book (the inventory) makes the inventory menu the open one by itself.
     */
    private boolean serverDone;
    private int openedAt;
    private int ticks;
    private final Deque<Integer> autoMoves = new ArrayDeque<>();
    private int lastAutoTick = -1;
    /**
     * Blocks whose use the server answered with a menu the station does not take (another mod's: Amendments' lectern editor
     * for a book and quill), with the block state they had then. Their next uses go on as without the library, so the camera
     * does not set off to come straight back.
     */
    private final Map<BlockPos, BlockState> otherMenus = new HashMap<>();

    private MenuSession() {
    }

    public static MenuSession get() {
        return INSTANCE;
    }

    /** The use of a menu station's block is about to go to the server. */
    public void arm(ClientStation target) {
        if (expect != null) {
            stale.add(new Stale(expect.binding(), expect.armedAt()));
        }
        expect = new Expect(target, target.type().menuBinding(), ticks);
        autoMoves.clear();
    }

    /**
     * Going over to {@code target}: {@code use} sends its block's use. It goes at once, or, while an earlier use is still
     * unanswered, once that answer has come and been closed: the server's close takes whatever menu is open, so a close sent
     * after the next use would shut the next station's menu.
     */
    public void useNext(ClientStation target, Runnable use) {
        pendingUse = null;
        if (expect == null && stale.isEmpty()) {
            arm(target);
            use.run();
            return;
        }
        pendingUse = new PendingUse(target, use);
    }

    /** Sends the waiting use once nothing is unanswered any more. */
    private void sendPendingUse() {
        if (pendingUse == null || expect != null || !stale.isEmpty()) {
            return;
        }
        PendingUse next = pendingUse;
        pendingUse = null;
        arm(next.station());
        next.use().run();
    }

    /** Whether {@code target}'s menu is open now. */
    public boolean isOpenAt(ClientStation target) {
        return station == target && menu != null;
    }

    /**
     * A menu opens on the server for this player (on the game thread, before vanilla makes its screen). True when it was
     * taken or closed here: then vanilla must not make a screen for it.
     */
    public boolean onOpenScreen(MenuType<?> type, int containerId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        expireStale();
        Stale old = stale.peekFirst();
        if (old != null && old.binding().accepts(type)) {
            // The answer to a use whose station is already left.
            stale.removeFirst();
            if (expect != null) {
                // A newer use is already on its way, and the server's close takes whatever menu is open: a close sent now
                // would reach the server after that use and shut its menu (the player saw the station open with nothing
                // working on the server: an enchanting table without offers). That use's menu replaces this one there.
                staleOpen = containerId;
                return true;
            }
            player.connection.send(new ServerboundContainerClosePacket(containerId));
            staleOpen = -1;
            sendPendingUse();
            return true;
        }
        Expect wanted = expect;
        if (menu != null) {
            // Any other menu the server opens closes ours there first.
            serverDone = true;
        }
        if (wanted == null) {
            return false;
        }
        expect = null;
        // Whatever menu this is, it has replaced a stale one left open on the server.
        staleOpen = -1;
        if (!wanted.binding().accepts(type)) {
            // A plugin's screen, another mod's menu: never guessed at; its own screen opens, over the world as without the
            // library: the station steps aside at once, with no flight back behind that screen.
            BlockPos pos = wanted.station().pos();
            otherMenus.put(pos, player.level().getBlockState(pos));
            DiegesisMod.LOGGER.info("Station {} at {} was answered with menu {}: the block is left to it",
                    wanted.station().type().id(), pos, net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(type));
            wanted.station().menuRefused(MenuRefusal.OTHER_MENU);
            FocusController.get().stepAside(pos);
            return false;
        }
        if (menu != null) {
            drop();
        }
        menu = type.create(containerId, player.getInventory());
        serverDone = false;
        shortLogged = false;
        player.containerMenu = menu;
        station = wanted.station();
        binding = wanted.binding();
        openedAt = ticks;
        lastAutoTick = -1;
        for (Map.Entry<String, MenuBinding.SlotRange> group : binding.slots().entrySet()) {
            station.state().bindSlots(group.getKey(), new MenuStorage(this, station, group.getValue()));
        }
        poll();
        FocusController.get().confirm(station);
        station.menuOpened();
        return true;
    }

    /** The player left the station (any way but the server's close): its menu is closed on the server too. */
    public void leave(ClientStation left) {
        if (pendingUse != null && pendingUse.station() == left) {
            pendingUse = null;
        }
        if (expect != null && expect.station() == left) {
            stale.add(new Stale(expect.binding(), expect.armedAt()));
            expect = null;
        }
        if (station != left || menu == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !serverDone) {
            // Also when another screen took over: the server closes whatever menu is open on a close.
            player.connection.send(new ServerboundContainerClosePacket(menu.containerId));
        }
        if (player != null && player.containerMenu == menu) {
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
        }
        if (serverDone) {
            station.menuRefused(MenuRefusal.CLOSED_BY_SERVER);
        }
        drop();
    }

    /** The server closed the open menu (the block broke, the player was pushed away): vanilla goes on to drop it here. */
    public void onServerClose() {
        if (menu != null) {
            serverDone = true;
        }
    }

    /**
     * Whether the server's update of {@code slot} in container {@code containerId} lies beyond the station's menu: it is left
     * out then. The server's menu can be longer than the one taken here: a mod that adds slots to a menu when its screen opens
     * (Satchels' satchel row) adds them on the server too, while a station's menu never gets a screen.
     */
    public boolean beyondMenu(int containerId, int slot) {
        AbstractContainerMenu open = takenMenu(containerId);
        if (open == null || slot < open.slots.size()) {
            return false;
        }
        noteShort(open, slot + 1);
        return true;
    }

    /**
     * The server's whole contents of container {@code containerId}. True when they are longer than the station's menu: the
     * part that fits has gone in here, and vanilla must not fill the menu.
     */
    public boolean fitContents(int containerId, int stateId, List<ItemStack> items, ItemStack carried) {
        AbstractContainerMenu open = takenMenu(containerId);
        if (open == null || items.size() <= open.slots.size()) {
            return false;
        }
        noteShort(open, items.size());
        open.initializeContents(stateId, items.subList(0, open.slots.size()), carried);
        return true;
    }

    /** The station's menu when it is the open one and has this container id. */
    private AbstractContainerMenu takenMenu(int containerId) {
        LocalPlayer player = Minecraft.getInstance().player;
        return menu != null && player != null && player.containerMenu == menu && menu.containerId == containerId ? menu : null;
    }

    private void noteShort(AbstractContainerMenu open, int serverSlots) {
        if (!shortLogged) {
            shortLogged = true;
            DiegesisMod.LOGGER.info("Station {}: the server's menu has at least {} slots, this one {}; another mod adds slots "
                    + "with the menu's screen, and the extra ones are left out", station.type().id(), serverSlots, open.slots.size());
        }
    }

    /**
     * Whether a use of the block at {@code pos}, now {@code state}, was answered with another mod's menu: its station does not
     * open. Forgotten once the block changes (the lectern's book taken off).
     */
    public boolean answeredElsewhere(BlockPos pos, BlockState state) {
        BlockState then = otherMenus.get(pos);
        if (then == null) {
            return false;
        }
        if (then != state) {
            otherMenus.remove(pos);
            return false;
        }
        return true;
    }

    /** Level change, disconnect: forget everything; the server forgets its menus too. */
    public void reset() {
        otherMenus.clear();
        expect = null;
        stale.clear();
        pendingUse = null;
        menu = null;
        station = null;
        binding = null;
        autoMoves.clear();
    }

    private void drop() {
        ClientStation was = station;
        menu = null;
        station = null;
        binding = null;
        autoMoves.clear();
        if (was != null) {
            // The bound groups read nothing now: what the server returns shows in the inventory, not on the block.
            was.state().pollStorages();
        }
    }

    public void tick() {
        ticks++;
        expireStale();
        if (expect != null && ticks - expect.armedAt() > expect.binding().openTimeoutTicks()) {
            Expect late = expect;
            expect = null;
            refuse(late.station(), MenuRefusal.TIMEOUT);
        }
        if (expect == null && staleOpen >= 0) {
            // No newer menu came to replace a stale one left open: it is closed now, as it would have been.
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && menu == null) {
                player.connection.send(new ServerboundContainerClosePacket(staleOpen));
            }
            staleOpen = -1;
        }
        sendPendingUse();
        if (menu == null) {
            return;
        }
        poll();
        if (!autoMoves.isEmpty() && ticks - openedAt >= binding.autoDelayTicks() && lastAutoTick != ticks) {
            lastAutoTick = ticks;
            int slot = MenuClicks.inventorySlot(menu, Minecraft.getInstance().player.getInventory(), autoMoves.removeFirst());
            if (slot >= 0) {
                send(new MenuClicks.Click(slot, 0, ClickType.QUICK_MOVE));
            }
        }
    }

    private void expireStale() {
        Iterator<Stale> iterator = stale.iterator();
        while (iterator.hasNext()) {
            Stale old = iterator.next();
            if (ticks - old.armedAt() > old.binding().openTimeoutTicks()) {
                iterator.remove();
            }
        }
    }

    private void refuse(ClientStation target, MenuRefusal why) {
        target.menuRefused(why);
        FocusController.get().abort(target.pos());
    }

    /** Reads the menu into the station: slot groups through their storages, outputs by copy. */
    private void poll() {
        if (station == null || menu == null) {
            return;
        }
        station.state().pollStorages();
        for (Map.Entry<String, Integer> output : binding.outputs().entrySet()) {
            ItemStack now = menu.getSlot(output.getValue()).getItem();
            if (!ItemStack.matches(station.state().getOutput(output.getKey()), now)) {
                station.state().setOutput(output.getKey(), now.copy());
            }
        }
    }

    ItemStack slotItem(ClientStation shownAt, int menuSlot) {
        return menu == null || shownAt != station || menuSlot >= menu.slots.size() ? ItemStack.EMPTY : menu.getSlot(menuSlot).getItem();
    }

    /**
     * Does a player action at {@code target} with menu clicks. True when anything changed in the menu (for feedback); the
     * server stays the judge.
     */
    public boolean perform(ClientStation target, StationAction action) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (station != target || menu == null || player == null || player.containerMenu != menu) {
            return false;
        }
        List<MenuClicks.Click> clicks = MenuClicks.of(menu, binding, player.getInventory(), action);
        return sendAll(clicks);
    }

    /** One vanilla click on whatever a target shows: number keys, Q, middle click. */
    public boolean click(ClientStation target, StationAction.Target where, int button, ClickType type) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (station != target || menu == null || player == null || player.containerMenu != menu) {
            return false;
        }
        int slot = where == null ? MenuClicks.OUTSIDE : MenuClicks.slotOf(menu, binding, player.getInventory(), where);
        if (slot < 0 && slot != MenuClicks.OUTSIDE && where instanceof StationAction.Target.Inventory inventory
                && inventory.index() == net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND) {
            return sendAll(offhandKey(player, button, type));
        }
        if (slot < 0 && slot != MenuClicks.OUTSIDE) {
            return false;
        }
        return sendAll(List.of(new MenuClicks.Click(slot, button, type)));
    }

    /**
     * The wheel over a stack, as Mouse Tweaks has it: down sends one item of it to the other side (from the inventory into the
     * station, from the station into the inventory), up brings one more of the same from the other side into it. Plain clicks.
     */
    public boolean wheel(ClientStation target, StationAction.Target where, double amount) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (station != target || menu == null || player == null || player.containerMenu != menu || !menu.getCarried().isEmpty()
                || amount == 0.0) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        int at = MenuClicks.slotOf(menu, binding, inventory, where);
        if (at < 0 || binding.outputs().containsValue(at)) {
            return false;
        }
        ItemStack here = menu.getSlot(at).getItem();
        if (here.isEmpty()) {
            return false;
        }
        boolean inInventory = where instanceof StationAction.Target.Inventory;
        int other = amount < 0.0 ? placeFor(here, !inInventory, at, inventory) : sourceOf(here, !inInventory, at, inventory);
        if (other < 0) {
            return false;
        }
        int from = amount < 0.0 ? at : other;
        int to = amount < 0.0 ? other : at;
        return sendAll(List.of(new MenuClicks.Click(from, 0, ClickType.PICKUP), new MenuClicks.Click(to, 1, ClickType.PICKUP),
                new MenuClicks.Click(from, 0, ClickType.PICKUP)));
    }

    /** The menu slots on one side: the inventory's, or the station's own slot groups. */
    private List<Integer> side(boolean inventorySide, net.minecraft.world.entity.player.Inventory inventory) {
        List<Integer> slots = new java.util.ArrayList<>();
        if (inventorySide) {
            for (int i = 0; i < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; i++) {
                int slot = MenuClicks.inventorySlot(menu, inventory, (i + 9) % net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE);
                if (slot >= 0) {
                    slots.add(slot);
                }
            }
        } else {
            for (MenuBinding.SlotRange range : binding.slots().values()) {
                for (int i = 0; i < range.count(); i++) {
                    slots.add(range.first() + i);
                }
            }
        }
        return slots;
    }

    /** Where one more of {@code stack} goes on that side: onto the same with room first, else into an empty slot. */
    private int placeFor(ItemStack stack, boolean inventorySide, int not, net.minecraft.world.entity.player.Inventory inventory) {
        int empty = -1;
        for (int slot : side(inventorySide, inventory)) {
            ItemStack there = menu.getSlot(slot).getItem();
            if (slot == not || !menu.getSlot(slot).mayPlace(stack)) {
                continue;
            }
            if (there.isEmpty()) {
                empty = empty < 0 ? slot : empty;
            } else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                return slot;
            }
        }
        return empty;
    }

    /** Where one more of {@code stack} comes from on that side. */
    private int sourceOf(ItemStack stack, boolean inventorySide, int not, net.minecraft.world.entity.player.Inventory inventory) {
        if (stack.getCount() >= stack.getMaxStackSize()) {
            return -1;
        }
        for (int slot : side(inventorySide, inventory)) {
            if (slot != not && ItemStack.isSameItemSameComponents(menu.getSlot(slot).getItem(), stack)) {
                return slot;
            }
        }
        return -1;
    }

    /** A key over the off hand, which this menu has no slot for: through vanilla's off-hand swap. */
    private List<MenuClicks.Click> offhandKey(LocalPlayer player, int button, ClickType type) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        int offhand = net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND;
        if (type == ClickType.SWAP && button >= 0 && button < 9) {
            // A number key: that hotbar slot and the off hand change places.
            int hotbar = MenuClicks.inventorySlot(menu, inventory, button);
            return hotbar < 0 ? List.of() : List.of(new MenuClicks.Click(hotbar, offhand, ClickType.SWAP));
        }
        int free = MenuClicks.freeSlot(menu, inventory);
        if (type == ClickType.THROW && free >= 0) {
            return List.of(new MenuClicks.Click(free, offhand, ClickType.SWAP), new MenuClicks.Click(free, button, ClickType.THROW),
                    new MenuClicks.Click(free, offhand, ClickType.SWAP));
        }
        return List.of();
    }

    private boolean sendAll(List<MenuClicks.Click> clicks) {
        if (clicks.isEmpty()) {
            return false;
        }
        String before = snapshot();
        for (MenuClicks.Click click : clicks) {
            send(click);
        }
        poll();
        return !before.equals(snapshot());
    }

    private String snapshot() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            text.append(stack.getCount()).append(stack.getItem()).append(';');
        }
        return text.append(menu.getCarried().getCount()).append(menu.getCarried().getItem()).toString();
    }

    private void send(MenuClicks.Click click) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.player == null || minecraft.player.containerMenu != menu) {
            return;
        }
        try {
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, click.slot(), click.button(), click.type(), minecraft.player);
        } catch (RuntimeException e) {
            // A menu of another mod that throws on a click must not take the game down; the server corrects the slots.
            DiegesisMod.LOGGER.error("Menu click {} failed in {}", click, menu, e);
        }
    }

    /** What the station shows of the open menu for addons. */
    public MenuAccess accessFor(ClientStation target) {
        return station == target && menu != null ? this : null;
    }

    // MenuAccess -----------------------------------------------------------------------------------------------------

    @Override
    public AbstractContainerMenu handle() {
        return menu;
    }

    @Override
    public int ticksOpen() {
        return menu == null ? 0 : ticks - openedAt;
    }

    @Override
    public void click(int menuSlot, int button, ClickType type) {
        if (menu != null) {
            sendAll(List.of(new MenuClicks.Click(menuSlot, button, type)));
        }
    }

    @Override
    public void quickMoveFromInventory(int inventorySlot) {
        autoMoves.add(inventorySlot);
    }

    @Override
    public void pressButton(int id) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (menu != null && player != null && player.containerMenu == menu && menu.clickMenuButton(player, id)) {
            player.connection.send(new ServerboundContainerButtonClickPacket(menu.containerId, id));
        }
    }

    @Override
    public void placeRecipe(net.minecraft.world.item.crafting.display.RecipeDisplayId recipe, boolean all) {
        Minecraft minecraft = Minecraft.getInstance();
        if (menu == null || minecraft.player == null || minecraft.gameMode == null || minecraft.player.containerMenu != menu) {
            return;
        }
        minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, all);
    }

    /** A slot group's window on the open menu; empty while no menu is open. Never written by the library. */
    private record MenuStorage(MenuSession session, ClientStation shownAt, MenuBinding.SlotRange range) implements SlotStorage {
        @Override
        public int size() {
            return range.count();
        }

        @Override
        public ItemStack get(int index) {
            return session.slotItem(shownAt, range.first() + index);
        }

        @Override
        public void set(int index, ItemStack stack) {
        }
    }
}
