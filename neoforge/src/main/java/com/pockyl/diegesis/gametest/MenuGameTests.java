package com.pockyl.diegesis.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.scene.CameraRig;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.Authority;
import com.pockyl.diegesis.api.station.MenuBinding;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.diegesis.impl.action.StationAction;
import com.pockyl.diegesis.impl.action.StationAction.ClickKind;
import com.pockyl.diegesis.impl.action.StationAction.Target;
import com.pockyl.diegesis.impl.menu.MenuClicks;

import java.util.List;
import java.util.UUID;

/**
 * Menu-backed stations: the clicks the client makes for each hand move, fed to real vanilla menus on the server, must
 * leave the inventories as the vanilla screen would.
 */
public final class MenuGameTests {
    private static final MenuBinding TABLE = MenuBinding.of(MenuType.CRAFTING).output("result", 0).slots("grid", 1, 9).build();
    private static final MenuBinding FURNACE = MenuBinding.of(MenuType.FURNACE).slots("input", 0, 1).slots("fuel", 1, 1)
            .output("result", 2).build();

    private MenuGameTests() {
    }

    @DiegesisTest(structure = "box")
    public static void menuClicksCraftAPickaxe(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        CraftingMenu menu = table(helper, player);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
        player.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
        // Planks along the top row, sticks down the middle: one put by each right click.
        act(menu, player, click(inv(0), 0, ClickKind.PICKUP));
        for (int i = 0; i < 3; i++) {
            act(menu, player, click(grid(i), 1, ClickKind.PICKUP));
        }
        act(menu, player, click(inv(1), 0, ClickKind.PICKUP));
        act(menu, player, click(grid(4), 1, ClickKind.PICKUP));
        act(menu, player, click(grid(7), 1, ClickKind.PICKUP));
        check(helper, menu.getSlot(0).getItem().is(Items.WOODEN_PICKAXE), "the result is a pickaxe");
        act(menu, player, click(new Target.Output("result"), 0, ClickKind.QUICK_MOVE));
        check(helper, count(player.getInventory(), Items.WOODEN_PICKAXE) == 1, "the pickaxe went to the inventory");
        check(helper, gridEmpty(menu) && menu.getCarried().isEmpty(), "nothing left in the grid or the hand");
        helper.succeed();
    }

    @DiegesisTest(structure = "box")
    public static void menuClicksBulkCraftAndSpread(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        CraftingMenu menu = table(helper, player);
        player.getInventory().setItem(9, new ItemStack(Items.OAK_LOG, 16));
        // Shift on an inventory stack sends it to the grid; shift on the result crafts all of it in one click.
        act(menu, player, click(inv(9), 0, ClickKind.QUICK_MOVE));
        check(helper, menu.getSlot(1).getItem().getCount() == 16, "the logs went to the grid");
        act(menu, player, click(new Target.Output("result"), 0, ClickKind.QUICK_MOVE));
        check(helper, count(player.getInventory(), Items.OAK_PLANKS) == 64 && gridEmpty(menu), "64 planks, the grid empty");
        // A drag with the left button shares evenly, with the right one puts one into each.
        int planks = MenuClicks.inventorySlot(menu, player.getInventory(), slotOf(player.getInventory(), Items.OAK_PLANKS));
        menu.clicked(planks, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        act(menu, player, new StationAction.Spread(List.of(grid(0), grid(1), grid(2)), 0));
        check(helper, menu.getSlot(1).getItem().getCount() == 21 && menu.getSlot(3).getItem().getCount() == 21
                && menu.getCarried().getCount() == 1, "64 shared over three cells, one left in the hand");
        act(menu, player, click(grid(0), 0, ClickKind.PICKUP));
        act(menu, player, click(grid(1), 0, ClickKind.PICKUP));
        act(menu, player, new StationAction.Spread(List.of(grid(3), grid(4), grid(5)), 1));
        check(helper, menu.getSlot(4).getItem().getCount() == 1 && menu.getSlot(6).getItem().getCount() == 1
                && menu.getCarried().getCount() == 18 && menu.getSlot(1).getItem().getCount() == 22, "one into each of three cells");
        check(helper, total(menu, player, Items.OAK_PLANKS) == 64, "no plank made or lost");
        helper.succeed();
    }

    @DiegesisTest(structure = "box")
    public static void menuKeysSwapThrowAndPutAway(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        CraftingMenu menu = table(helper, player);
        player.getInventory().setItem(10, new ItemStack(Items.COBBLESTONE, 10));
        act(menu, player, click(inv(10), 0, ClickKind.QUICK_MOVE));
        int cell = MenuClicks.slotOf(menu, TABLE, player.getInventory(), grid(0));
        check(helper, cell == 1 && menu.getSlot(1).getItem().getCount() == 10, "shift moved the stack to the first cell");
        // A number key over a cell swaps it with that hotbar slot.
        menu.clicked(cell, 3, net.minecraft.world.inventory.ContainerInput.SWAP, player);
        check(helper, player.getInventory().getItem(3).getCount() == 10 && menu.getSlot(1).getItem().isEmpty(), "swapped to hotbar 4");
        // Q throws one, Ctrl+Q the stack.
        int hotbar = MenuClicks.inventorySlot(menu, player.getInventory(), 3);
        check(helper, hotbar == 40, "hotbar slot 4 is menu slot 40");
        menu.clicked(hotbar, 0, net.minecraft.world.inventory.ContainerInput.THROW, player);
        check(helper, player.getInventory().getItem(3).getCount() == 9, "one thrown");
        // A stack in the hand nothing takes goes back to the inventory.
        menu.clicked(hotbar, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        for (MenuClicks.Click click : MenuClicks.putAway(menu, player.getInventory())) {
            menu.clicked(click.slot(), click.button(), click.type(), player);
        }
        check(helper, menu.getCarried().isEmpty() && count(player.getInventory(), Items.COBBLESTONE) == 9, "put away");
        // Outside the screen: the stack in the hand is dropped.
        menu.clicked(MenuClicks.inventorySlot(menu, player.getInventory(), 0), 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        menu.clicked(MenuClicks.OUTSIDE, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().isEmpty() && count(player.getInventory(), Items.COBBLESTONE) == 0, "dropped");
        helper.runAfterDelay(2, () -> {
            int thrown = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4.0))
                    .stream().mapToInt(item -> item.getItem().getCount()).sum();
            check(helper, thrown == 10, "ten cobblestone lie on the ground, " + thrown);
            helper.succeed();
        });
    }

    @DiegesisTest(structure = "box")
    public static void theOffHandMovesThroughVanillaSwaps(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        CraftingMenu menu = table(helper, player);
        player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.TORCH, 5));
        player.getInventory().setItem(0, new ItemStack(Items.STICK, 3));
        Target offhand = inv(Inventory.SLOT_OFFHAND);
        // Taken onto the cursor, put back, merged onto, swapped with the stack in the hand.
        act(menu, player, click(offhand, 0, ClickKind.PICKUP));
        check(helper, menu.getCarried().is(Items.TORCH) && menu.getCarried().getCount() == 5
                && player.getInventory().getItem(Inventory.SLOT_OFFHAND).isEmpty(), "the torches are in the hand");
        act(menu, player, click(offhand, 0, ClickKind.PICKUP));
        check(helper, menu.getCarried().isEmpty() && player.getInventory().getItem(Inventory.SLOT_OFFHAND).getCount() == 5, "put back");
        menu.clicked(MenuClicks.inventorySlot(menu, player.getInventory(), 0), 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        act(menu, player, click(offhand, 0, ClickKind.PICKUP));
        check(helper, menu.getCarried().is(Items.TORCH) && player.getInventory().getItem(Inventory.SLOT_OFFHAND).is(Items.STICK),
                "sticks and torches changed places");
        act(menu, player, click(offhand, 0, ClickKind.PICKUP));
        check(helper, menu.getCarried().is(Items.STICK) && player.getInventory().getItem(Inventory.SLOT_OFFHAND).getCount() == 5,
                "swapped back");
        player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.STICK, 2));
        act(menu, player, click(offhand, 0, ClickKind.PICKUP));
        check(helper, menu.getCarried().isEmpty() && player.getInventory().getItem(Inventory.SLOT_OFFHAND).getCount() == 5, "merged");
        // Shift sends it into the grid.
        act(menu, player, click(offhand, 0, ClickKind.QUICK_MOVE));
        check(helper, menu.getSlot(1).getItem().getCount() == 5 && player.getInventory().getItem(Inventory.SLOT_OFFHAND).isEmpty(),
                "the off hand's sticks went to the grid");
        helper.succeed();
    }

    @DiegesisTest(structure = "box")
    public static void furnaceShiftClicksRouteAsVanilla(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() - 1.0);
        net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace =
                (net.minecraft.world.level.block.entity.FurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
        AbstractContainerMenu menu = furnace.createMenu(9, player.getInventory(), player);
        player.containerMenu = menu;
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.RAW_IRON, 5));
        inventory.setItem(1, new ItemStack(Items.COAL, 3));
        inventory.setItem(2, new ItemStack(Items.OAK_LOG, 4));
        inventory.setItem(3, new ItemStack(Items.APPLE, 2));
        // What the hand holds on opening goes in by a shift-click of its hotbar slot: ore to the top, coal to the fire.
        act(menu, player, FURNACE, click(inv(0), 0, ClickKind.QUICK_MOVE));
        act(menu, player, FURNACE, click(inv(1), 0, ClickKind.QUICK_MOVE));
        check(helper, menu.getSlot(0).getItem().is(Items.RAW_IRON) && menu.getSlot(0).getItem().getCount() == 5, "ore in the input");
        check(helper, menu.getSlot(1).getItem().is(Items.COAL) && menu.getSlot(1).getItem().getCount() == 3, "coal in the fuel");
        // A log smelts into charcoal, so it goes to the input; that is taken by the ore, so it stays in the inventory.
        act(menu, player, FURNACE, click(inv(2), 0, ClickKind.QUICK_MOVE));
        check(helper, count(inventory, Items.OAK_LOG) == 4 && menu.getSlot(1).getItem().is(Items.COAL), "the log is not burnt as fuel");
        // An apple neither smelts nor burns: shift moves it between the hotbar and the inventory, as in the screen.
        act(menu, player, FURNACE, click(inv(3), 0, ClickKind.QUICK_MOVE));
        check(helper, inventory.getItem(3).isEmpty() && count(inventory, Items.APPLE) == 2, "the apple went up to the inventory");
        // The output: shift takes it all into the inventory.
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
        act(menu, player, FURNACE, click(new Target.Output("result"), 0, ClickKind.QUICK_MOVE));
        check(helper, count(inventory, Items.IRON_INGOT) == 3 && menu.getSlot(2).getItem().isEmpty(), "three ingots taken");
        // A plain click takes it onto the cursor; a click on the fuel slot with coal on the cursor tops it up.
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 2));
        act(menu, player, FURNACE, click(new Target.Output("result"), 0, ClickKind.PICKUP));
        check(helper, menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 2, "ingots on the cursor");
        act(menu, player, FURNACE, click(inv(5), 0, ClickKind.PICKUP));
        inventory.setItem(6, new ItemStack(Items.COAL, 2));
        act(menu, player, FURNACE, click(inv(6), 0, ClickKind.PICKUP));
        act(menu, player, FURNACE, click(new Target.Station(SlotRef.of("fuel", 0)), 0, ClickKind.PICKUP));
        check(helper, menu.getSlot(1).getItem().getCount() == 5 && menu.getCarried().isEmpty(), "coal topped up to five");
        helper.succeed();
    }

    @DiegesisTest
    public static void menuStationsCheckTheirBinding(GameTestHelper helper) {
        StationSchema schema = StationSchema.builder().slots("grid", 9).output("result").build();
        StationType type = StationType.builder(Identifier.fromNamespaceAndPath(Diegesis.MOD_ID, "test_menu_table")).blocks(Blocks.CRAFTING_TABLE)
                .schema(schema).menu(TABLE).build();
        check(helper, type.authority() == Authority.MENU && type.menuBinding() == TABLE, "a menu station");
        boolean refused;
        try {
            StationType wrong = StationType.builder(Identifier.fromNamespaceAndPath(Diegesis.MOD_ID, "test_menu_wrong")).blocks(Blocks.CRAFTING_TABLE)
                    .schema(StationSchema.builder().slots("grid", 4).output("result").build()).menu(TABLE).build();
            refused = wrong == null;
        } catch (IllegalStateException e) {
            refused = true;
        }
        check(helper, refused, "a grid of 4 against a binding of 9 is refused");
        CameraRig early = CameraRig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"interactive_at\": 0.6}")).getOrThrow();
        CameraRig plain = CameraRig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        check(helper, early.interactiveAt() == 0.6F && plain.interactiveAt() == 1.0F, "interactive_at reads, 1 by default");
        helper.succeed();
    }

    private static CraftingMenu table(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() - 1.0);
        CraftingMenu menu = new CraftingMenu(7, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        return menu;
    }

    private static void act(AbstractContainerMenu menu, ServerPlayer player, StationAction action) {
        act(menu, player, TABLE, action);
    }

    private static void act(AbstractContainerMenu menu, ServerPlayer player, MenuBinding binding, StationAction action) {
        for (MenuClicks.Click click : MenuClicks.of(menu, binding, player.getInventory(), action)) {
            menu.clicked(click.slot(), click.button(), click.type(), player);
        }
    }

    private static StationAction click(Target target, int button, ClickKind kind) {
        return new StationAction.Click(target, button, kind);
    }

    private static Target grid(int index) {
        return new Target.Station(SlotRef.of("grid", index));
    }

    private static Target inv(int index) {
        return new Target.Inventory(index);
    }

    private static boolean gridEmpty(CraftingMenu menu) {
        for (int i = 1; i <= 9; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                total += inventory.getItem(i).getCount();
            }
        }
        return total;
    }

    private static int slotOf(Inventory inventory, Item item) {
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private static int total(CraftingMenu menu, ServerPlayer player, Item item) {
        int total = count(player.getInventory(), item) + (menu.getCarried().is(item) ? menu.getCarried().getCount() : 0);
        for (int i = 1; i <= 9; i++) {
            if (menu.getSlot(i).getItem().is(item)) {
                total += menu.getSlot(i).getItem().getCount();
            }
        }
        return total;
    }

    private static void check(GameTestHelper helper, boolean condition, String what) {
        if (!condition) {
            helper.fail(what);
        }
    }

    /** A server player in the test level, as in {@code ModGameTests}. */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "diegesis-test");
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                ClientInformation.createDefault()) {
            @Override
            public boolean isSpectator() {
                return false;
            }
        };
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false));
        return player;
    }
}
