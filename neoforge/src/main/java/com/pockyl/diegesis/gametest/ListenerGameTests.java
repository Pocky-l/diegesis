package com.pockyl.diegesis.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationListener;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.diegesis.impl.action.ActionHandler;
import com.pockyl.diegesis.impl.action.StationAction;
import com.pockyl.diegesis.impl.action.StationAction.ClickKind;
import com.pockyl.diegesis.impl.action.StationAction.Target;
import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.impl.server.StationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Listeners added to a station from outside: their order, deny-only refusals, failures and effects. */
@GameTestHolder(Diegesis.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ListenerGameTests {
    private static final String GRID = "grid";
    private static final String BELL = "bell";
    private static final List<String> CALLS = new CopyOnWriteArrayList<>();
    private static StationType station;

    private ListenerGameTests() {
    }

    /**
     * A station on crying obsidian whose logic refuses diamonds, with three listeners of its own (one refuses flint, one
     * throws) and one for every station that only counts what happens here.
     */
    private static synchronized StationType station() {
        if (station == null) {
            ResourceLocation id = DiegesisMod.id("listener_station");
            station = Diegesis.station(id)
                    .blocks(Blocks.CRYING_OBSIDIAN)
                    .schema(StationSchema.builder().slots(GRID, 2).control(BELL, ControlSpec.button()).build())
                    .logic(context -> new StationLogic() {
                        @Override
                        public boolean mayPlace(StationContext context, Player player, SlotRef slot, ItemStack stack) {
                            return !stack.is(Items.DIAMOND);
                        }

                        @Override
                        public void onButton(StationContext context, Player player, String control) {
                            CALLS.add("logic:button");
                        }
                    })
                    .register();
            Diegesis.addListener(id, new StationListener() {
                @Override
                public boolean mayPlace(StationContext context, Player player, SlotRef slot, ItemStack stack) {
                    if (stack.is(Items.DIAMOND)) {
                        CALLS.add("a:asked-diamond");
                    }
                    return !stack.is(Items.FLINT);
                }

                @Override
                public void onButton(StationContext context, Player player, String control) {
                    CALLS.add("a:button");
                }

                @Override
                public void onEffect(StationContext context, ResourceLocation effect, String node, CompoundTag data) {
                    CALLS.add("a:effect:" + effect.getPath() + ":" + node);
                }
            });
            Diegesis.addListener(id, new StationListener() {
                @Override
                public void onButton(StationContext context, Player player, String control) {
                    throw new IllegalStateException("A listener that fails on purpose (game test)");
                }
            });
            Diegesis.addListener(new StationListener() {
                @Override
                public void onOpen(StationContext context, Player player) {
                    if (context.type().id().equals(id)) {
                        CALLS.add("all:open");
                    }
                }

                @Override
                public void onButton(StationContext context, Player player, String control) {
                    if (context.type().id().equals(id)) {
                        CALLS.add("all:button");
                    }
                }

                @Override
                public void onClose(StationContext context, Player player) {
                    if (context.type().id().equals(id)) {
                        CALLS.add("all:close");
                    }
                }
            });
        }
        return station;
    }

    @GameTest(template = "box")
    public static void listenersRefuseButNeverAllow(GameTestHelper helper) {
        station();
        CALLS.clear();
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.CRYING_OBSIDIAN);
        BlockPos at = helper.absolutePos(pos);
        ServerPlayer player = mockPlayer(helper);
        try {
            Vec3 standing = Vec3.atBottomCenterOf(helper.absolutePos(pos.north()));
            player.teleportTo(standing.x, standing.y, standing.z);
            StationManager manager = StationManager.get(helper.getLevel().getServer());
            check(helper, manager.openWithoutChannelCheck(player, at), "opened");
            StationContext context = Diegesis.activeStation(helper.getLevel(), at).orElseThrow();
            check(helper, CALLS.contains("all:open"), "the listener for every station heard the opening: " + CALLS);

            // Flint: the logic allows it, a listener refuses it. Nothing moves, nothing is lost.
            player.getInventory().setItem(0, new ItemStack(Items.FLINT, 3));
            put(manager, player, at, 0);
            check(helper, context.state().getItem(SlotRef.of(GRID, 0)).isEmpty(), "flint refused by the listener");
            check(helper, player.getInventory().countItem(Items.FLINT) + carried(player).getCount() == 3, "no flint lost");
            manager.handle(player, new Packets.Action(at, new StationAction.Click(new Target.Inventory(0), 0, ClickKind.PICKUP)));

            // Diamond: the logic refuses it, so no listener is even asked.
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 2));
            manager.handle(player, new Packets.Action(at, new StationAction.Click(new Target.Inventory(1), 0, ClickKind.PICKUP)));
            manager.handle(player, new Packets.Action(at, new StationAction.Click(new Target.Station(SlotRef.of(GRID, 1)), 0,
                    ClickKind.PICKUP)));
            check(helper, context.state().getItem(SlotRef.of(GRID, 1)).isEmpty(), "diamond refused by the logic");
            check(helper, !CALLS.contains("a:asked-diamond"), "a listener was asked after the logic refused");
            manager.handle(player, new Packets.Action(at, new StationAction.Click(new Target.Inventory(1), 0, ClickKind.PICKUP)));
            check(helper, player.getInventory().countItem(Items.DIAMOND) == 2 && carried(player).isEmpty(), "diamonds back");

            // A stick passes both.
            player.getInventory().setItem(0, new ItemStack(Items.STICK, 1));
            put(manager, player, at, 0);
            check(helper, context.state().getItem(SlotRef.of(GRID, 0)).is(Items.STICK), "stick accepted");

            // The logic first, then the listeners in order; one that throws is skipped and the rest still hear it.
            CALLS.clear();
            manager.handle(player, new Packets.Action(at, new StationAction.Press(BELL)));
            check(helper, CALLS.equals(List.of("logic:button", "a:button", "all:button")), "order of the button calls: " + CALLS);

            // A client's prediction runs without the logic and so without listeners.
            CALLS.clear();
            ActionHandler.apply(context, StationLogic.NONE, player, new StationAction.Press(BELL));
            check(helper, CALLS.isEmpty(), "listeners heard a prediction: " + CALLS);

            context.effect(ResourceLocation.withDefaultNamespace("flame"), "burner", new CompoundTag());
            context.effect(ResourceLocation.withDefaultNamespace("flame"), null, new CompoundTag());
            check(helper, CALLS.equals(List.of("a:effect:flame:burner", "a:effect:flame:null")), "effects heard: " + CALLS);

            // A client shares an effect: passed on (the listeners hear it), at most 10 a second, small data only.
            CALLS.clear();
            ResourceLocation clatter = ResourceLocation.fromNamespaceAndPath("mymod", "clatter");
            for (int i = 0; i < 15; i++) {
                manager.handle(player, new Packets.Action(at, new StationAction.Effect(clatter, "pot", new CompoundTag())));
            }
            check(helper, CALLS.size() == 10 && CALLS.get(0).equals("a:effect:clatter:pot"), "10 of 15 shared effects passed on: " + CALLS);
            CALLS.clear();
            ServerPlayer stranger = mockPlayer(helper);
            try {
                stranger.teleportTo(standing.x, standing.y, standing.z);
                manager.handle(stranger, new Packets.Action(at, new StationAction.Effect(clatter, "", new CompoundTag())));
                check(helper, CALLS.isEmpty(), "a player who does not work at the station shared an effect: " + CALLS);
            } finally {
                DiegesisMod.onPlayerLogout(stranger);
            }
            CompoundTag big = new CompoundTag();
            big.putString("text", "x".repeat(400));
            check(helper, !new StationAction.Effect(clatter, "", big).isSmall(), "400 bytes of data are too much");
            check(helper, new StationAction.Effect(clatter, "", new CompoundTag()).isSmall(), "empty data is small");

            manager.close(player, false);
            check(helper, CALLS.contains("all:close"), "the closing was heard");
            check(helper, player.getInventory().countItem(Items.STICK) == 1, "the stick came back on close");
        } finally {
            DiegesisMod.onPlayerLogout(player);
        }
        helper.succeed();
    }

    private static void put(StationManager manager, ServerPlayer player, BlockPos at, int slot) {
        manager.handle(player, new Packets.Action(at, new StationAction.Click(new Target.Inventory(0), 0, ClickKind.PICKUP)));
        manager.handle(player, new Packets.Action(at, new StationAction.Click(new Target.Station(SlotRef.of(GRID, slot)), 0,
                ClickKind.PICKUP)));
    }

    private static ItemStack carried(Player player) {
        return player.inventoryMenu.getCarried();
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
