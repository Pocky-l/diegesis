package com.pockyl.diegesis.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlacingPhysics;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.diegesis.impl.action.ActionHandler;
import com.pockyl.diegesis.impl.action.StationAction;
import com.pockyl.diegesis.impl.client.scene.PlacedPhysics;
import com.pockyl.diegesis.impl.client.scene.Placing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.pockyl.diegesis.gametest.PlacingGameTests.CUBE;
import static com.pockyl.diegesis.gametest.PlacingGameTests.ICON;
import static com.pockyl.diegesis.gametest.PlacingGameTests.check;

/**
 * Both sides of free placing at once: the server's state takes the actions as the server does, its changes go to the client's
 * state through the same writing and reading as the network, and the client's physics works on the client's state. What
 * the player saw at rest must stay where it is, whatever the server sends.
 */
public final class PlacingSyncTests {
    private static final String ITEMS = "items";
    private static final float FRAME = 1.0F / 60.0F;
    static final StationSchema SCHEMA = StationSchema.builder().slots(ITEMS, 16, SlotRules.DEFAULT.maxStack(1).placed()).build();
    private static StationType type;

    private PlacingSyncTests() {
    }

    static synchronized StationType type() {
        if (type == null) {
            type = Diegesis.station(DiegesisMod.id("placing_sync")).blocks(Blocks.SMOOTH_STONE).schema(SCHEMA).register();
        }
        return type;
    }

    /** One side's station: its state, at a place in the level. */
    static final class Side implements StationContext {
        final Level level;
        final StationState state = new StationState(SCHEMA);

        Side(Level level) {
            this.level = level;
        }

        @Override
        public StationType type() {
            return PlacingSyncTests.type();
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return BlockPos.ZERO;
        }

        @Override
        public BlockState blockState() {
            return Blocks.SMOOTH_STONE.defaultBlockState();
        }

        @Override
        public Direction facing() {
            return Direction.NORTH;
        }

        @Override
        public StationState state() {
            return state;
        }

        @Override
        public Collection<? extends Player> users() {
            return List.of();
        }

        @Override
        public void effect(Identifier effect, String node, CompoundTag data) {
        }
    }

    /**
     * The client's physics over the client's state, its actions shown at once (as the client guesses them) and applied by the
     * server, whose changes come back as they would over the network; an action the server turns down sends everything.
     */
    static final class RoundTrip implements PlacedPhysics.Host {
        final PlacingOptions options;
        final Side server;
        final Side client;
        final Player serverPlayer;
        final Player clientPlayer;
        final PlacedPhysics physics;
        int turnedDown;
        /** The time as the physics sees it: it goes on with each frame run, never with the test's own speed. */
        long clock = 1_000_000L;

        RoundTrip(GameTestHelper helper, PlacingOptions options) {
            this.options = options;
            this.server = new Side(helper.getLevel());
            this.client = new Side(helper.getLevel());
            this.serverPlayer = helper.makeMockPlayer(GameType.CREATIVE);
            this.clientPlayer = helper.makeMockPlayer(GameType.CREATIVE);
            this.physics = new PlacedPhysics(this, options, options.physics().orElseThrow(), SCHEMA.slotGroups().get(ITEMS).size());
        }

        SlotRef ref(int index) {
            return SlotRef.of(ITEMS, index);
        }

        /** The player puts {@code item} down at {@code pose} from the hand, as the hand does: through the action. */
        void putDown(int index, Item item, ItemPose pose) {
            serverPlayer.containerMenu.setCarried(new ItemStack(item));
            clientPlayer.containerMenu.setCarried(new ItemStack(item));
            physics.toss(index, 0.0F, 0.0F);
            perform(new StationAction.Place(ref(index), pose));
        }

        /** The player takes the item of a slot into the hand. */
        void take(int index) {
            perform(new StationAction.Click(new StationAction.Target.Station(ref(index)), 0, StationAction.ClickKind.PICKUP));
            serverPlayer.containerMenu.setCarried(ItemStack.EMPTY);
            clientPlayer.containerMenu.setCarried(ItemStack.EMPTY);
        }

        /** The server sends all it has again (an action of this player it turned down, a newcomer's snapshot). */
        void resendAll() {
            server.state.markAllDirty();
            flush();
        }

        void flush() {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.level.registryAccess());
            server.state.writeChanges(buf, false);
            client.state.readChanges(buf);
        }

        void run(float seconds) {
            for (float time = 0.0F; time < seconds; time += FRAME) {
                clock += (long) (FRAME * 1000.0F);
                physics.update(FRAME);
                flush();
            }
        }

        /** Where the client shows each item. */
        List<ItemPose> shown() {
            List<ItemPose> poses = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                poses.add(pose(i));
            }
            return poses;
        }

        @Override
        public ItemStack stack(int index) {
            return client.state.getItem(ref(index));
        }

        @Override
        public PlacedPhysics.Solid solid(int index) {
            ItemStack stack = stack(index);
            if (stack.isEmpty()) {
                return null;
            }
            return stack.is(Items.BREAD) || stack.is(Items.PAPER) ? ICON : CUBE;
        }

        @Override
        public ItemPose pose(int index) {
            return stack(index).isEmpty() ? null : client.state.getPose(ref(index));
        }

        @Override
        public void setPose(int index, ItemPose pose) {
            client.state.setPose(ref(index), pose);
        }

        @Override
        public SlotRef slot(int index) {
            return ref(index);
        }

        @Override
        public PlaceSurface surfaceOf(ItemPose pose) {
            return Placing.surfaceOf(options, pose, this::pose);
        }

        @Override
        public boolean surfaceMoved(int index) {
            return false;
        }

        @Override
        public boolean focused() {
            return true;
        }

        @Override
        public void perform(StationAction action) {
            ActionHandler.apply(client, StationLogic.NONE, clientPlayer, action);
            if (!ActionHandler.apply(server, StationLogic.NONE, serverPlayer, action)) {
                turnedDown++;
                server.state.markAllDirty();
            }
            flush();
        }

        @Override
        public void hideWhileTaken(int index, long millis) {
        }

        @Override
        public long millis() {
            return clock;
        }
    }

    /** A kitchen cabinet's shelf: room 6 high, things 2 big. */
    static PlacingOptions shelf() {
        return PlacingOptions.builder(ITEMS).surface(1.0F, 4.0F, 15.0F, 15.0F, 1.0F, 6.0F, 0.0F).itemSize(2.0F)
                .physics(PlacingPhysics.DEFAULT).build();
    }

    /** Ten things and a tower of three put down from the hand, each let go of a hover over where it lands, and left to rest. */
    static RoundTrip filled(GameTestHelper helper) {
        RoundTrip trip = new RoundTrip(helper, shelf());
        float half = CUBE.half(2.0F).y;
        float icon = ICON.half(2.0F).y;
        float hover = PlacingPhysics.DEFAULT.hover();
        Item[] things = {Items.STONE, Items.BREAD, Items.OAK_PLANKS, Items.APPLE, Items.PAPER, Items.IRON_BLOCK, Items.MELON, Items.BREAD,
                Items.COBBLESTONE, Items.APPLE};
        for (int i = 0; i < things.length; i++) {
            boolean flat = things[i] == Items.BREAD || things[i] == Items.PAPER;
            float x = 3.0F + (i % 5) * 2.3F;
            float z = 6.0F + (i / 5) * 3.0F;
            // Let go of mid-swing: a little leaning, a little off where it was aimed.
            Quaternionf lean = new Quaternionf().rotateZ(i % 2 == 0 ? 0.08F : -0.06F);
            trip.putDown(i, things[i], new ItemPose(x, 1.0F, z, (flat ? icon : half) + hover, lean, 1.0F, -1));
            trip.run(0.2F);
        }
        float top = 1.0F;
        Item[] tower = {Items.OAK_PLANKS, Items.STONE, Items.IRON_BLOCK};
        for (int i = 0; i < 3; i++) {
            trip.putDown(10 + i, tower[i], new ItemPose(12.0F + 0.1F * i, top, 12.0F, half + hover, new Quaternionf().rotateY(0.05F * i), 1.0F,
                    i == 0 ? -1 : 9 + i));
            trip.run(1.0F);
            top = trip.pose(10 + i).top();
        }
        trip.run(3.0F);
        return trip;
    }

    static float apart(ItemPose a, ItemPose b) {
        if (a == null || b == null) {
            return a == b ? 0.0F : Float.MAX_VALUE;
        }
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() + a.lift() - b.y() - b.lift()) + Math.abs(a.z() - b.z())
                + (1.0F - Math.abs(new Quaternionf(a.rotation()).dot(new Quaternionf(b.rotation())))) * 100.0F;
    }

    public static void whatRestsIsWhatTheServerHas(GameTestHelper helper) {
        RoundTrip trip = filled(helper);
        float worst = 0.0F;
        StringBuilder off = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            float d = apart(trip.pose(i), trip.server.state.getPose(trip.ref(i)));
            worst = Math.max(worst, d);
            if (d > 0.01F) {
                off.append(i).append(' ');
            }
        }
        check(helper, trip.turnedDown == 0, "the server took every action: " + trip.turnedDown + " turned down");
        check(helper, worst < 0.01F, "every thing at rest is where the server has it: " + worst + " [" + off + "]");
        helper.succeed();
    }

    public static void takingAThingLeavesTheOthersWhereTheyRest(GameTestHelper helper) {
        RoundTrip trip = filled(helper);
        // Long after it all came to rest, past any answer of the server still expected.
        trip.run(6.0F);
        List<ItemPose> rested = trip.shown();
        // An apple is taken into the hand; then, as after an action the server turned down, it sends everything again.
        trip.take(3);
        trip.run(1.0F);
        trip.resendAll();
        trip.run(2.0F);
        float worst = 0.0F;
        StringBuilder moved = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            if (i == 3) {
                continue;
            }
            float d = apart(rested.get(i), trip.pose(i));
            worst = Math.max(worst, d);
            if (d > 0.01F) {
                moved.append(i).append(':').append(String.format("%.3f", d)).append(' ');
            }
        }
        check(helper, trip.pose(3) == null, "the apple is in the hand");
        check(helper, worst < 0.01F, "nothing else moved, nothing went back: " + worst + " [" + moved + "]");
        helper.succeed();
    }

    public static void aServerResendMidSettleDoesNotPullThingsBack(GameTestHelper helper) {
        RoundTrip trip = new RoundTrip(helper, shelf());
        float half = CUBE.half(2.0F).y;
        // A stone let go of a hover over the shelf; while it is still coming down, the server sends all it has.
        trip.putDown(0, Items.STONE, new ItemPose(6.0F, 1.0F, 8.0F, half + 1.5F, new Quaternionf(), 1.0F, -1));
        trip.run(0.08F);
        float midway = trip.pose(0).y() + trip.pose(0).lift();
        trip.resendAll();
        trip.physics.update(FRAME);
        float after = trip.pose(0).y() + trip.pose(0).lift();
        check(helper, after <= midway + 0.01F, "it does not jump back up to where it was let go: " + midway + " -> " + after);
        trip.run(2.0F);
        check(helper, apart(trip.pose(0), trip.server.state.getPose(trip.ref(0))) < 0.01F
                && Math.abs(trip.pose(0).y() + trip.pose(0).lift() - 1.0F - half) < 0.02F, "it lands, and the server has it there");
        helper.succeed();
    }

    public static void thingsAtRestNeverMoveByThemselves(GameTestHelper helper) {
        RoundTrip trip = filled(helper);
        trip.run(6.0F);
        List<ItemPose> rested = trip.shown();
        // The apple taken and put back elsewhere five times, a while apart; everything wakes each time.
        float half = CUBE.half(2.0F).y;
        for (int round = 0; round < 5; round++) {
            trip.take(3);
            trip.run(0.5F);
            trip.putDown(3, Items.APPLE, new ItemPose(4.0F + round * 1.5F, 1.0F, 13.5F, half + 1.5F, new Quaternionf(), 1.0F, -1));
            trip.run(2.0F);
        }
        trip.resendAll();
        trip.run(6.0F);
        float worst = 0.0F;
        float server = 0.0F;
        StringBuilder moved = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            if (i == 3) {
                continue;
            }
            float d = apart(rested.get(i), trip.pose(i));
            worst = Math.max(worst, d);
            server = Math.max(server, apart(trip.pose(i), trip.server.state.getPose(trip.ref(i))));
            if (d > 0.01F) {
                moved.append(i).append(':').append(String.format("%.3f", d)).append(' ');
            }
        }
        check(helper, worst < 0.01F, "nothing it did not touch moved: " + worst + " [" + moved + "]");
        check(helper, server < 0.01F, "and the server has all of it where it is shown: " + server);
        helper.succeed();
    }
}
