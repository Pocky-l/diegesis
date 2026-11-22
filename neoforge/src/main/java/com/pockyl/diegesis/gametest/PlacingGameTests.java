package com.pockyl.diegesis.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlacingPhysics;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.impl.action.StationAction;
import com.pockyl.diegesis.impl.client.scene.PlacedPhysics;
import com.pockyl.diegesis.impl.client.scene.Placing;

import java.util.ArrayList;
import java.util.List;

/**
 * Free placing without a client: the geometry of where things go ({@code Placing}) and the physics core run against a
 * scene stand-in. Only classes free of the client are touched here.
 */
public final class PlacingGameTests {
    private static final float FRAME = 1.0F / 60.0F;
    /** A block's hit box as the shapes measure one: half a block in the model, 0.8 of the item's size along its sides. */
    static final PlacedPhysics.Solid CUBE = new PlacedPhysics.Solid(new Vector3f(0.5F), new Quaternionf(), 0.8F, false);

    private PlacingGameTests() {
    }

    /** A shelf 14 by 11 pixels with room 6 high, walled all round, and items 4 pixels big. */
    static PlacingOptions shelf() {
        return PlacingOptions.builder("items").surface(1.0F, 4.0F, 15.0F, 15.0F, 1.0F, 6.0F, 0.0F).itemSize(4.0F)
                .physics(PlacingPhysics.DEFAULT).build();
    }

    @GameTest(template = "empty")
    public static void physicsRestsWhereThingsArePut(GameTestHelper helper) {
        PlacingOptions options = shelf();
        Scene scene = new Scene(options, 3);
        float half = CUBE.half(options.itemSize()).y;
        for (int i = 0; i < 3; i++) {
            scene.put(i, new ItemStack(Items.STONE), CUBE, new ItemPose(3.0F + i * 4.0F, 1.0F, 8.0F, half, new Quaternionf(), 1.0F, -1));
        }
        List<ItemPose> before = scene.poses();
        scene.run(2.0F);
        for (int i = 0; i < 3; i++) {
            check(helper, near(before.get(i), scene.poses.get(i), 0.01F), "box " + i + " stays where it was put: " + scene.poses.get(i));
        }
        check(helper, scene.sent.isEmpty(), "nothing was sent: " + scene.sent);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void physicsKeepsAStackStanding(GameTestHelper helper) {
        PlacingOptions options = PlacingOptions.builder("items").surface(1.0F, 4.0F, 15.0F, 15.0F, 1.0F, 16.0F, 0.0F).itemSize(4.0F)
                .physics(PlacingPhysics.DEFAULT).build();
        Scene scene = new Scene(options, 3);
        float half = CUBE.half(options.itemSize()).y;
        float y = 1.0F;
        for (int i = 0; i < 3; i++) {
            scene.put(i, new ItemStack(Items.OAK_PLANKS), CUBE, new ItemPose(8.0F, y, 9.0F, half, new Quaternionf(), 1.0F, i - 1));
            y += half * 2.0F;
        }
        List<ItemPose> before = scene.poses();
        scene.run(3.0F);
        for (int i = 0; i < 3; i++) {
            check(helper, near(before.get(i), scene.poses.get(i), 0.01F) && scene.poses.get(i).on() == i - 1,
                    "piece " + i + " of the tower stays as stacked: " + scene.poses.get(i));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stackingGoesToTheTopOfThePile(GameTestHelper helper) {
        // Planks on the shelf, a stone on them, an apple beside the pile: aimed at the planks, the apple in the hand climbs the
        // pile to the stone; the apple standing alone is no part of it.
        List<ItemPose> poses = List.of(new ItemPose(5.0F, 1.0F, 8.0F, 1.6F, new Quaternionf(), 1.0F, -1),
                new ItemPose(5.0F, 4.2F, 8.0F, 1.6F, new Quaternionf(), 1.0F, 0),
                new ItemPose(11.0F, 1.0F, 8.0F, 1.0F, new Quaternionf(), 1.0F, -1));
        java.util.function.IntFunction<float[]> boxes = i -> box(poses.get(i), 1.6F);
        check(helper, Placing.topOfPile(0, 3, poses::get, boxes, null, 1.0F, 1.0F) == 1, "aimed at the planks: on the stone");
        check(helper, Placing.topOfPile(1, 3, poses::get, boxes, null, 1.0F, 1.0F) == 1, "aimed at the stone: on it");
        check(helper, Placing.topOfPile(2, 3, poses::get, boxes, null, 1.0F, 1.0F) == 2, "the apple alone is its own top");
        // Aimed beside the stone on the planks' top, far enough that nothing would touch it, it stays on the planks.
        check(helper, Placing.topOfPile(0, 3, poses::get, boxes, slot -> new float[] {poses.get(slot).x() + 3.0F, poses.get(slot).z()}, 0.5F, 0.5F) == 0,
                "aimed beside what stands on it: on the planks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aLowThingIsAllTopAtAnyItemSize(GameTestHelper helper) {
        PlacingOptions fridge = PlacingOptions.builder("items").surface(1.0F, 3.0F, 15.0F, 15.0F, 1.0F, 4.0F, 3.0F).itemSize(2.0F).build();
        ItemPose apple = new ItemPose(8.0F, 1.0F, 8.0F, 1.0F, new Quaternionf(), 1.0F, -1);
        check(helper, !Placing.isOnTop(fridge, apple, 1.0F + 0.4F), "a 2 px thing at item_size 2 hit at 20 % of its height: in front");
        check(helper, Placing.isOnTop(fridge, apple, 1.0F + 1.4F), "hit at 70 %: on top");
        ItemPose paper = new ItemPose(8.0F, 1.0F, 8.0F, 0.1F, new Quaternionf(), 1.0F, -1);
        check(helper, Placing.isOnTop(fridge, paper, 1.0F), "a lying sheet is all top");
        PlacingOptions table = PlacingOptions.builder("items").surface(0.0F, 0.0F, 32.0F, 16.0F, 12.0F, 16.0F, 0.0F).itemSize(6.0F).build();
        ItemPose book = new ItemPose(8.0F, 12.0F, 8.0F, 1.2F, new Quaternionf(), 1.0F, -1);
        check(helper, Placing.isOnTop(table, book, 12.1F), "a book lying at item_size 6 is all top");
        ItemPose block = new ItemPose(8.0F, 12.0F, 8.0F, 2.4F, new Quaternionf(), 1.0F, -1);
        check(helper, !Placing.isOnTop(table, block, 12.5F), "a block's lower front at item_size 6: in front");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickTakesTheExactHitFirst(GameTestHelper helper) {
        // A sheet standing upright, seen past its thin side, and a block behind: the cursor a hair beside the sheet and right on
        // the block takes the block; with nothing behind it, the near miss takes the sheet.
        Placing.Pickable sheet = pickable(new Vector3f(5.0F, 3.0F, 5.0F), new Vector3f(2.0F, 2.0F, 0.05F), 0.75F, 1.0F);
        Placing.Pickable block = pickable(new Vector3f(12.0F, 3.0F, 5.0F), new Vector3f(1.0F, 1.0F, 1.0F), 0.75F, 1.0F);
        Vector3f origin = new Vector3f(0.0F, 3.0F, 5.2F);
        Vector3f along = new Vector3f(1.0F, 0.0F, 0.0F);
        Placing.Picked both = Placing.pick(List.of(sheet, block), origin, along, Float.MAX_VALUE);
        check(helper, both != null && both.index() == 1, "the block the cursor is really on wins: " + both);
        Placing.Picked alone = Placing.pick(List.of(sheet), origin, along, Float.MAX_VALUE);
        check(helper, alone != null && alone.index() == 0, "alone, the sheet passed a hair away is taken: " + alone);
        Placing.Picked exact = Placing.pick(List.of(sheet), origin, along, 2.0F);
        check(helper, exact == null, "nothing is taken from behind what the ray hits first");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aThinThingHasAnEdgeToHit(GameTestHelper helper) {
        // A sheet lying on a shelf at height 1, a tenth of a pixel thick, seen from the front at its height.
        Placing.Pickable sheet = pickable(new Vector3f(5.0F, 1.05F, 8.0F), new Vector3f(2.0F, 0.05F, 2.0F), 0.75F, 1.0F);
        Vector3f along = new Vector3f(0.0F, 0.0F, 1.0F);
        check(helper, Placing.pick(List.of(sheet), new Vector3f(5.0F, 1.3F, 0.0F), along, Float.MAX_VALUE) != null,
                "its edge is thick enough to hit");
        check(helper, Placing.pick(List.of(sheet), new Vector3f(5.0F, 0.8F, 0.0F), along, Float.MAX_VALUE) == null,
                "never from under the shelf");
        Placing.Pickable exact = pickable(new Vector3f(5.0F, 1.05F, 8.0F), new Vector3f(2.0F, 0.05F, 2.0F), 0.0F, 1.0F);
        check(helper, Placing.pick(List.of(exact), new Vector3f(5.0F, 1.3F, 0.0F), along, Float.MAX_VALUE) == null,
                "pick_min 0 keeps the exact box");
        helper.succeed();
    }

    /** A cake 8 by 8 pixels round (8, 8), its top at 4. */
    private static final float[] CAKE = {4.0F, 12.0F, 1.0F, 4.0F, 4.0F, 12.0F};

    @GameTest(template = "empty")
    public static void stackingFollowsTheCursorOnAWideSupport(GameTestHelper helper) {
        float[] left = Placing.onTopOf(5.5F, 8.0F, CAKE, 1.0F, 1.0F, 0.3F, 0.0F);
        float[] right = Placing.onTopOf(10.5F, 9.0F, CAKE, 1.0F, 1.0F, 0.3F, 0.0F);
        check(helper, left[0] == 5.5F && right[0] == 10.5F, "two apples stand side by side where pointed: " + left[0] + ", " + right[0]);
        check(helper, left[1] == 8.0F && right[1] == 8.0F, "near the middle in depth they line up on it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stackingSnapsToTheMiddle(GameTestHelper helper) {
        float[] near = Placing.onTopOf(9.0F, 7.0F, CAKE, 1.0F, 1.0F, 0.3F, 0.0F);
        check(helper, near[0] == 8.0F && near[1] == 8.0F, "within stack_snap of the middle: on it, " + near[0] + ", " + near[1]);
        float[] old = Placing.onTopOf(11.0F, 5.0F, CAKE, 1.0F, 1.0F, 1.0F, 0.0F);
        check(helper, old[0] == 8.0F && old[1] == 8.0F, "stack_snap 1: always the middle, as before");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aStackedThingKeepsItsMiddleOnItsSupport(GameTestHelper helper) {
        float[] past = Placing.onTopOf(20.0F, -3.0F, CAKE, 1.5F, 1.5F, 0.3F, 0.0F);
        check(helper, past[0] == 12.0F && past[1] == 4.0F, "pointed past the edge: its middle on the edge, " + past[0] + ", " + past[1]);
        float[] in = Placing.onTopOf(20.0F, 8.0F, CAKE, 1.5F, 1.5F, 0.3F, 0.5F);
        check(helper, in[0] == 11.25F, "stack_edge 0.5 keeps half of it over the top: " + in[0]);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theGripOffsetFadesWithTravel(GameTestHelper helper) {
        check(helper, Placing.gripShare(0.0, 12.0F) == 1.0F && Placing.gripShare(2.9, 12.0F) == 1.0F,
                "within three GUI pixels of the take it stays where it stood");
        check(helper, Math.abs(Placing.gripShare(9.0, 12.0F) - 0.5F) < 1.0e-4F, "half way over grip_blend: half of the offset");
        check(helper, Placing.gripShare(15.0, 12.0F) == 0.0F && Placing.gripShare(40.0, 12.0F) == 0.0F, "past it: under the cursor");
        check(helper, Placing.gripShare(3.0, 0.0F) == 0.0F && Placing.gripShare(1.0, 0.0F) == 1.0F,
                "grip_blend 0: under the cursor as soon as it moves off");
        helper.succeed();
    }

    /** Planks, a stone on them and an apple-sized cube on that, in the middle of a tall shelf. */
    static Scene tower() {
        PlacingOptions options = PlacingOptions.builder("items").surface(1.0F, 4.0F, 25.0F, 15.0F, 1.0F, 16.0F, 0.0F).itemSize(4.0F)
                .physics(PlacingPhysics.DEFAULT).build();
        Scene scene = new Scene(options, 3);
        float half = CUBE.half(options.itemSize()).y;
        float y = 1.0F;
        net.minecraft.world.item.Item[] items = {Items.OAK_PLANKS, Items.STONE, Items.MELON};
        for (int i = 0; i < 3; i++) {
            scene.put(i, new ItemStack(items[i]), CUBE, new ItemPose(8.0F, y, 9.0F, half, new Quaternionf(), 1.0F, i - 1));
            y += half * 2.0F;
        }
        scene.run(0.5F);
        return scene;
    }

    @GameTest(template = "empty")
    public static void aPileRidesItsBase(GameTestHelper helper) {
        Scene scene = tower();
        List<ItemPose> before = scene.poses();
        ItemPose base = before.get(0);
        ItemPose moved = new ItemPose(base.x() + 3.0F, base.y(), base.z(), base.lift(), base.rotation(), base.scale(), -1);
        scene.physics.hold(0);
        scene.poses.set(0, moved);
        scene.physics.steer(0, moved);
        scene.run(1.0F);
        scene.physics.release(0);
        scene.run(2.0F);
        check(helper, Math.abs(scene.poses.get(0).x() - base.x() - 3.0F) < 0.1F, "the base went where the hand took it: " + scene.poses.get(0));
        for (int i = 1; i < 3; i++) {
            ItemPose was = before.get(i);
            ItemPose now = scene.poses.get(i);
            check(helper, Math.abs(now.x() - was.x() - 3.0F) < 0.1F && Math.abs(now.y() - was.y()) < 0.05F && Math.abs(now.z() - was.z()) < 0.05F
                    && now.on() == i - 1, "piece " + i + " rode along, standing as it stood: " + now);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPileFallsWhenItsBaseTips(GameTestHelper helper) {
        Scene scene = tower();
        List<ItemPose> before = scene.poses();
        ItemPose base = before.get(0);
        ItemPose tipped = new ItemPose(base.x(), base.y(), base.z(), base.lift(), new Quaternionf().rotateX(0.5F), base.scale(), -1);
        scene.physics.hold(0, true);
        scene.poses.set(0, tipped);
        scene.physics.steer(0, tipped);
        scene.run(1.0F);
        scene.physics.release(0);
        scene.run(2.0F);
        check(helper, !near(before.get(2), scene.poses.get(2), 0.3F), "tipped over, the base lets its pile fall: " + scene.poses.get(2));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingFromAPileLowersTheRest(GameTestHelper helper) {
        Scene scene = tower();
        ItemPose middle = scene.poses.get(1);
        ItemPose top = scene.poses.get(2);
        // Taken out of the middle: the top comes straight down onto the planks, as the server and the client's own take set it.
        scene.stacks.set(1, ItemStack.EMPTY);
        scene.poses.set(1, null);
        ItemPose lowered = new ItemPose(top.x(), top.y() - (top.y() - middle.y()), top.z(), top.lift(), top.rotation(), top.scale(), 0);
        scene.poses.set(2, lowered);
        scene.run(2.0F);
        check(helper, near(lowered, scene.poses.get(2), 0.02F) && scene.poses.get(2).on() == 0,
                "what stood on it came straight down and stays on the planks: " + scene.poses.get(2));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDraggedThingStaysOnItsSupportsTop(GameTestHelper helper) {
        Scene scene = tower();
        ItemPose planks = scene.poses.get(0);
        ItemPose stone = scene.poses.get(1);
        PlaceSurface surface = scene.options.surfaces().get(0);
        float half = stone.lift();
        float ceiling = surface.height() + surface.liftRoom();
        // The stone dragged a pixel across the planks' top, as a drag of the hand does it each frame.
        scene.physics.hold(1);
        float level = stone.y();
        for (int frame = 0; frame < 60; frame++) {
            float x = stone.x() + Math.min(1.0F, frame / 30.0F);
            PlacedPhysics.Ground ground = scene.physics.groundUnder(surface, x - half + 0.05F, x + half - 0.05F, stone.z() - half + 0.05F,
                    stone.z() + half - 0.05F);
            level = Placing.shoveLevel(level, ground.top(), half * 2.0F, ceiling);
            ItemPose steered = new ItemPose(x, level, stone.z(), half, stone.rotation(), 1.0F, -1);
            scene.poses.set(1, steered);
            scene.physics.steer(1, steered);
            scene.physics.update(FRAME);
        }
        check(helper, near(planks, scene.poses.get(0), 0.05F), "the planks stay where they are: " + scene.poses.get(0));
        check(helper, Math.abs(scene.poses.get(1).y() - stone.y()) < 0.05F, "the stone slides on their top: " + scene.poses.get(1));
        check(helper, Math.abs(scene.poses.get(2).x() - stone.x() - 1.0F) < 0.1F, "what stands on it goes along: " + scene.poses.get(2));
        // Past the planks' edge it steps down onto the shelf.
        for (int frame = 0; frame < 60; frame++) {
            float x = stone.x() + 1.0F + Math.min(5.0F, frame / 6.0F);
            PlacedPhysics.Ground ground = scene.physics.groundUnder(surface, x - half + 0.05F, x + half - 0.05F, stone.z() - half + 0.05F,
                    stone.z() + half - 0.05F);
            level = Placing.shoveLevel(level, ground.top(), half * 2.0F, ceiling);
            ItemPose steered = new ItemPose(x, level, stone.z(), half, stone.rotation(), 1.0F, -1);
            scene.poses.set(1, steered);
            scene.physics.steer(1, steered);
            scene.physics.update(FRAME);
        }
        scene.physics.release(1);
        scene.run(2.0F);
        check(helper, Math.abs(scene.poses.get(1).y() - surface.height()) < 0.05F && scene.poses.get(1).on() == -1,
                "past the edge it stands on the shelf: " + scene.poses.get(1));
        check(helper, near(planks, scene.poses.get(0), 0.1F), "the planks are still where they were: " + scene.poses.get(0));
        helper.succeed();
    }

    /** A cabinet shelf open at the front (z 4), a stone block standing with its middle {@code inside} pixels in from that edge. */
    static Scene overTheEdge(float inside) {
        PlacingOptions options = PlacingOptions.builder("items").surface(1.0F, 4.0F, 15.0F, 15.0F, 1.0F, 6.0F, 4.0F).itemSize(4.0F)
                .physics(PlacingPhysics.DEFAULT).build();
        options = new PlacingOptions(options.group(), List.of(new PlaceSurface(new org.joml.Vector2f(1.0F, 4.0F), new org.joml.Vector2f(15.0F, 15.0F),
                1.0F, 6.0F, 4.0F, java.util.Optional.empty(), java.util.Set.of(PlaceSurface.Side.FRONT))), options.itemSize(), options.turnStep(),
                options.scaleRange(), options.scaleStep(), options.turnSpeed(), options.stacking(), options.blockModels(), options.itemLight(),
                options.lightRadius(), options.lightColor(), options.shadows(), options.snap(), options.previewColor(), options.counts(),
                options.physics(), options.framePose(), options.hoverLift(), options.handling());
        Scene scene = new Scene(options, 1);
        float half = CUBE.half(options.itemSize()).y;
        scene.put(0, new ItemStack(Items.STONE), CUBE, new ItemPose(8.0F, 1.0F, 4.0F + inside, half, new Quaternionf(), 1.0F, -1));
        return scene;
    }

    @GameTest(template = "empty")
    public static void aBoxHalfOverAnOpenEdgeSitsFlat(GameTestHelper helper) {
        Scene scene = overTheEdge(0.5F);
        scene.run(2.0F);
        ItemPose pose = scene.poses.get(0);
        Vector3f up = new Quaternionf(pose.rotation()).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        check(helper, pose != null && up.y > Math.cos(Math.toRadians(1.0)), "it sits flat, overhanging: " + pose);
        check(helper, Math.abs(pose.z() - 4.5F) < 0.05F && Math.abs(pose.y() - 1.0F) < 0.05F, "where it was put: " + pose);
        check(helper, scene.sent.stream().noneMatch(action -> action instanceof StationAction.Click), "it did not fall out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBoxPastItsMiddleFallsOff(GameTestHelper helper) {
        Scene scene = overTheEdge(-0.5F);
        scene.run(3.0F);
        check(helper, scene.sent.stream().anyMatch(action -> action instanceof StationAction.Click),
                "past its balance point it tips over the edge and falls out: " + scene.poses.get(0));
        check(helper, scene.told.stream().filter(line -> line.startsWith("fell")).toList().equals(List.of("fell 0")),
                "its fall is told once: " + scene.told);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFallingPileKnocksAndThenRestsQuietly(GameTestHelper helper) {
        Scene scene = tower();
        check(helper, scene.told.stream().noneMatch(line -> line.startsWith("impact")), "a standing pile knocks nothing: " + scene.told);
        ItemPose base = scene.poses.get(0);
        ItemPose tipped = new ItemPose(base.x(), base.y(), base.z(), base.lift(), new Quaternionf().rotateX(0.5F), base.scale(), -1);
        scene.physics.hold(0, true);
        scene.poses.set(0, tipped);
        scene.physics.steer(0, tipped);
        scene.run(1.0F);
        scene.physics.release(0);
        scene.run(2.0F);
        List<String> knocks = scene.told.stream().filter(line -> line.startsWith("impact")).toList();
        check(helper, !knocks.isEmpty() && knocks.size() <= 40, "the falling pile knocks, a few times: " + knocks);
        check(helper, scene.impactSpeeds.stream().allMatch(speed -> speed >= com.pockyl.diegesis.api.scene.fx.FeedbackOptions.DEFAULT_IMPACT_SPEED),
                "only knocks over the threshold: " + scene.impactSpeeds);
        scene.told.clear();
        scene.run(2.0F);
        check(helper, scene.told.isEmpty(), "at rest it knocks no more: " + scene.told);
        helper.succeed();
    }

    /** An icon as the shapes measure one at the item frame's size: 16 by 16 pixels a pixel thick, half a unit wide. */
    static final PlacedPhysics.Solid ICON = new PlacedPhysics.Solid(new Vector3f(0.25F, 0.25F, 0.015625F), new Quaternionf(), 2.0F, true);

    /** A fridge shelf, items 2 pixels big. */
    static PlacingOptions fridge() {
        return PlacingOptions.builder("items").surface(1.0F, 3.0F, 15.0F, 15.0F, 1.0F, 4.0F, 3.0F).itemSize(2.0F)
                .physics(PlacingPhysics.DEFAULT).build();
    }

    @GameTest(template = "empty")
    public static void aLyingFlatThingDoesNotRock(GameTestHelper helper) {
        PlacingOptions options = fridge();
        Scene scene = new Scene(options, 2);
        float thin = PlacedPhysics.flatHalf(PlacingPhysics.DEFAULT);
        Quaternionf lying = new Quaternionf().rotateX((float) Math.PI / 2.0F);
        scene.put(0, new ItemStack(Items.PAPER), ICON, new ItemPose(8.0F, 1.0F, 8.0F, thin, lying, 1.0F, -1));
        // A block dragged into its edge.
        float half = CUBE.half(options.itemSize()).y;
        ItemPose block = new ItemPose(4.0F, 1.0F, 8.0F, half, new Quaternionf(), 1.0F, -1);
        scene.put(1, new ItemStack(Items.STONE), CUBE, block);
        scene.run(0.3F);
        scene.physics.hold(1);
        float most = 0.0F;
        for (int frame = 0; frame < 60; frame++) {
            ItemPose steered = new ItemPose(4.0F + frame * 0.05F, 1.0F, 8.0F, half, new Quaternionf(), 1.0F, -1);
            scene.poses.set(1, steered);
            scene.physics.steer(1, steered);
            scene.physics.update(FRAME);
            Vector3f thinSide = new Quaternionf(scene.poses.get(0).rotation()).transform(new Vector3f(0.0F, 0.0F, 1.0F));
            most = Math.max(most, (float) Math.toDegrees(Math.acos(Math.min(1.0F, Math.abs(thinSide.y)))));
        }
        scene.physics.release(1);
        scene.run(2.0F);
        ItemPose paper = scene.poses.get(0);
        check(helper, paper.x() > 8.5F, "pushed, it slid: " + paper);
        check(helper, most < 5.0F, "it never tipped up as a rocking toy does: " + most + " degrees");
        check(helper, Math.abs(new Quaternionf(paper.rotation()).dot(lying)) > 0.99999F && Math.abs(paper.y() - 1.0F) < 0.01F,
                "it lies dead flat as it was laid: " + paper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flatThingsStandFlush(GameTestHelper helper) {
        PlacingOptions options = fridge();
        Scene scene = new Scene(options, 2);
        float thin = PlacedPhysics.flatHalf(PlacingPhysics.DEFAULT);
        float half = ICON.half(options.itemSize()).y;
        // Two icons standing face to face, put down touching as the hand puts them (each as thick as flatHalf says).
        ItemPose front = new ItemPose(8.0F, 1.0F, 8.0F, half, new Quaternionf(), 1.0F, -1);
        ItemPose back = new ItemPose(8.0F, 1.0F, 8.0F + thin * 2.0F, half, new Quaternionf(), 1.0F, -1);
        scene.put(0, new ItemStack(Items.BREAD), ICON, front);
        scene.put(1, new ItemStack(Items.BREAD), ICON, back);
        scene.run(2.0F);
        check(helper, near(front, scene.poses.get(0), 0.01F) && near(back, scene.poses.get(1), 0.01F),
                "neither moved: " + scene.poses.get(0) + ", " + scene.poses.get(1));
        float gap = scene.poses.get(1).z() - scene.poses.get(0).z() - thin * 2.0F;
        check(helper, Math.abs(gap) < 0.03F, "they stand flush: " + gap);
        check(helper, thin * 2.0F > ICON.half(options.itemSize()).z * 2.0F, "their faces never share a plane");
        helper.succeed();
    }

    /** A thing as the placing's geometry sees it: half its box per pixel of size, and whether it is flat. */
    record Box(Vector3f perSize, boolean flat) implements Placing.Shape {
        @Override
        public Vector3f extents(org.joml.Quaternionfc rotation, float size) {
            org.joml.Matrix3f turn = new org.joml.Matrix3f().set(rotation);
            Vector3f half = new Vector3f(perSize).mul(size);
            return new Vector3f(Math.abs(turn.m00) * half.x + Math.abs(turn.m10) * half.y + Math.abs(turn.m20) * half.z,
                    Math.abs(turn.m01) * half.x + Math.abs(turn.m11) * half.y + Math.abs(turn.m21) * half.z,
                    Math.abs(turn.m02) * half.x + Math.abs(turn.m12) * half.y + Math.abs(turn.m22) * half.z);
        }

        @Override
        public boolean isFlat() {
            return flat;
        }
    }

    /** Paper: half its width and height per pixel of size, a sixteenth of that thick. */
    private static final Box SHEET = new Box(new Vector3f(0.5F, 0.5F, 0.03125F), true);

    /** A kitchen drawer's tray, where flat things lie. */
    static PlacingOptions drawer() {
        PlacingOptions built = PlacingOptions.builder("items").surface(1.0F, -8.0F, 15.0F, 3.0F, 8.0F, 5.0F, 0.0F).itemSize(4.0F)
                .physics(PlacingPhysics.DEFAULT).build();
        PlaceSurface tray = built.surfaces().get(0);
        PlaceSurface lying = new PlaceSurface(tray.from(), tray.to(), tray.height(), tray.clearance(), tray.aimHeight(), tray.wall(), tray.open(),
                tray.headroom(), PlaceSurface.FlatRest.LIE);
        return new PlacingOptions(built.group(), List.of(lying), built.itemSize(), built.turnStep(), built.scaleRange(), built.scaleStep(),
                built.turnSpeed(), built.stacking(), built.blockModels(), built.itemLight(), built.lightRadius(), built.lightColor(), built.shadows(),
                built.snap(), built.previewColor(), built.counts(), built.physics(), built.framePose(), built.hoverLift(), built.handling());
    }

    @GameTest(template = "empty")
    public static void flatThingsLieOnALieSurface(GameTestHelper helper) {
        PlacingOptions options = drawer();
        PlaceSurface tray = options.surfaces().get(0);
        float least = PlacedPhysics.flatHalf(PlacingPhysics.DEFAULT);
        ItemPose spot = Placing.freeSpot(options, SHEET, options.surfaces(), List.of(), least, Float.NaN);
        Vector3f thin = new Quaternionf(spot.rotation()).transform(new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f top = new Quaternionf(spot.rotation()).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        check(helper, Math.abs(thin.y) > 0.999F, "set out in a drawer, a sheet lies: " + spot);
        check(helper, top.z > 0.999F, "its top towards the far side, read from above");
        check(helper, Math.abs(spot.lift() - Math.max(least, SHEET.perSize().z * options.itemSize())) < 1.0e-4F,
                "as thick as it is drawn, at least as the physics has it: " + spot.lift());
        PlaceSurface shelf = fridge().surfaces().get(0);
        check(helper, Placing.baseTurn(shelf, SHEET).equals(new Quaternionf(), 1.0e-6F), "on a shelf it stands");
        check(helper, Placing.baseTurn(tray, new Box(new Vector3f(0.5F), false)).equals(new Quaternionf(), 1.0e-6F), "a block does not lie down");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sameThingsShiftClickedOntoALieSurfacePileUp(GameTestHelper helper) {
        PlacingOptions options = drawer();
        float least = PlacedPhysics.flatHalf(PlacingPhysics.DEFAULT);
        List<float[]> taken = new ArrayList<>();
        List<ItemPose> poses = new ArrayList<>();
        // Six sheets shift-clicked in, one after another: the first is set out, the rest go onto it, the top each time.
        for (int i = 0; i < 6; i++) {
            List<Placing.PileTop> piles = new ArrayList<>();
            for (int j = 0; j < poses.size(); j++) {
                int index = j;
                if (poses.stream().noneMatch(pose -> pose.on() == index)) {
                    piles.add(new Placing.PileTop(j, poses.get(j)));
                }
            }
            ItemPose spot = Placing.freeSpot(options, SHEET, options.surfaces(), taken, least, Float.NaN, piles);
            check(helper, spot != null, "sheet " + i + " has a place");
            poses.add(spot);
            taken.add(Placing.boxOf(options, SHEET, spot, least));
        }
        for (int i = 1; i < 6; i++) {
            ItemPose pose = poses.get(i);
            check(helper, pose.on() == i - 1 && pose.x() == poses.get(0).x() && pose.z() == poses.get(0).z()
                    && Math.abs(pose.y() - poses.get(i - 1).top()) < 1.0e-4F, "sheet " + i + " lies on the one before: " + pose);
        }
        check(helper, poses.get(5).top() <= options.surfaces().get(0).height() + options.surfaces().get(0).clearance(), "under the clearance");
        helper.succeed();
    }

    /** A microwave's plate: things that come without a place go to its middle, then round it. */
    @GameTest(template = "empty")
    public static void aMiddleSurfaceFillsFromItsMiddle(GameTestHelper helper) {
        PlacingOptions built = PlacingOptions.builder("items").surface(7.0F, 6.0F, 13.0F, 12.0F, 2.0F, 6.0F, 2.0F).itemSize(2.5F).build();
        PlaceSurface square = built.surfaces().get(0);
        PlaceSurface plate = new PlaceSurface(square.from(), square.to(), square.height(), square.clearance(), square.aimHeight(),
                square.wall(), square.open(), square.headroom(), square.flatRest(), PlaceSurface.Fill.MIDDLE);
        List<PlaceSurface> surfaces = List.of(plate);
        Box apple = new Box(new Vector3f(0.5F), false);
        List<float[]> taken = new ArrayList<>();
        ItemPose first = Placing.freeSpot(built, apple, surfaces, taken, 0.0F, Float.NaN);
        check(helper, first != null && first.x() == 10.0F && first.z() == 9.0F, "the first stands in the plate's middle: " + first);
        taken.add(Placing.boxOf(built, apple, first, 0.0F));
        ItemPose second = Placing.freeSpot(built, apple, surfaces, taken, 0.0F, Float.NaN);
        check(helper, second != null && Math.hypot(second.x() - 10.0F, second.z() - 9.0F) <= 3.0F,
                "the next stands right beside it: " + second);
        ItemPose shelf = Placing.freeSpot(built, apple, built.surfaces(), List.of(), 0.0F, Float.NaN);
        check(helper, shelf.z() > 10.0F, "a surface without it still fills from the back: " + shelf);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stackingShrinksAtMostAQuarter(GameTestHelper helper) {
        PlacingOptions options = fridge();
        check(helper, Placing.stackFloor(options, 1.0F) == 0.75F, "a thing of size 1 goes on top down to 0.75");
        check(helper, Placing.stackFloor(options, 0.6F) == 0.5F, "never under scale_range");
        // A thing 2 px tall under 1.6 px of room: 0.8 of its size fits, so it goes on top; under 1.2 px only 0.6, so beside.
        Box apple = new Box(new Vector3f(0.5F), false);
        float fits = Placing.fittingScale(options, apple, new Quaternionf(), 1.0F, 1.6F);
        float tight = Placing.fittingScale(options, apple, new Quaternionf(), 1.0F, 1.2F);
        check(helper, fits >= Placing.stackFloor(options, 1.0F) && tight < Placing.stackFloor(options, 1.0F), fits + ", " + tight);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPutDownSoundsOnceAsItLands(GameTestHelper helper) {
        Scene scene = tower();
        scene.landings.clear();
        float half = CUBE.half(scene.options.itemSize()).y;
        Scene fresh = new Scene(scene.options, 2);
        fresh.put(0, new ItemStack(Items.OAK_PLANKS), CUBE, new ItemPose(8.0F, 1.0F, 9.0F, half, new Quaternionf(), 1.0F, -1));
        fresh.run(0.3F);
        // Let go of from the hand a hover over the planks: it falls onto them, sounds once, and is part of their pile then.
        fresh.physics.toss(1, 0.0F, 0.0F);
        ItemPose let = new ItemPose(8.0F, 1.0F + half * 2.0F, 9.0F, half + 1.5F, new Quaternionf(), 1.0F, 0);
        fresh.put(1, new ItemStack(Items.STONE), CUBE, let);
        fresh.run(2.0F);
        check(helper, fresh.landings.equals(List.of(1)), "one landing sound, for the stone: " + fresh.landings);
        check(helper, fresh.told.equals(List.of("settled 1 on 0")), "one landing told, on the planks, and no knock: " + fresh.told);
        ItemPose stone = fresh.poses.get(1);
        check(helper, Math.abs(stone.y() + stone.lift() - (1.0F + half * 3.0F)) < 0.05F && stone.on() == 0, "it came down onto the planks: " + stone);
        check(helper, fresh.physics.rides(1), "come to rest on them, it is part of their pile");
        ItemPose planks = fresh.poses.get(0);
        ItemPose moved = new ItemPose(planks.x() + 2.0F, planks.y(), planks.z(), planks.lift(), planks.rotation(), planks.scale(), -1);
        fresh.physics.hold(0);
        fresh.poses.set(0, moved);
        fresh.physics.steer(0, moved);
        fresh.run(1.0F);
        fresh.physics.release(0);
        fresh.run(1.0F);
        check(helper, Math.abs(fresh.poses.get(1).x() - stone.x() - 2.0F) < 0.1F, "and it rides them: " + fresh.poses.get(1));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heldPosesAreSentInSteps(GameTestHelper helper) {
        Scene scene = tower();
        scene.sent.clear();
        ItemPose base = scene.poses.get(0);
        scene.physics.hold(0);
        // A drag of a second, a little each frame: the others see it every quarter second, the pile on it too.
        for (int frame = 0; frame < 60; frame++) {
            ItemPose steered = new ItemPose(base.x() + frame * 0.05F, base.y(), base.z(), base.lift(), base.rotation(), base.scale(), -1);
            scene.poses.set(0, steered);
            scene.physics.steer(0, steered);
            scene.physics.update(FRAME);
        }
        long held = scene.sent.stream().filter(action -> action instanceof StationAction.Pose pose && pose.slot().index() == 0).count();
        long riders = scene.sent.stream().filter(action -> action instanceof StationAction.Pose pose && pose.slot().index() == 2).count();
        check(helper, held >= 3 && held <= 4, "the held one is sent in steps: " + held);
        check(helper, riders >= 3, "and what rides on it: " + riders);
        ItemPose before = scene.poses.get(0);
        scene.physics.release(0);
        scene.run(2.0F);
        check(helper, Math.abs(scene.poses.get(0).x() - before.x()) < 0.1F, "its own answers never pull it back: " + scene.poses.get(0));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTurnNearSquareSettlesOnIt(GameTestHelper helper) {
        Quaternionf near = new Quaternionf().rotateY((float) Math.toRadians(93.0));
        Quaternionf square = Placing.detent(near, 45.0F, 5.0F);
        check(helper, square.equals(new Quaternionf().rotateY((float) Math.toRadians(90.0)), 1.0e-5F), "3 degrees off square: square");
        Quaternionf diagonal = Placing.detent(new Quaternionf().rotateY((float) Math.toRadians(-41.0)), 45.0F, 5.0F);
        check(helper, diagonal.equals(new Quaternionf().rotateY((float) Math.toRadians(-45.0)), 1.0e-5F), "4 degrees off the diagonal: on it");
        Quaternionf free = new Quaternionf().rotateY((float) Math.toRadians(20.0));
        check(helper, Placing.detent(free, 45.0F, 5.0F).equals(free, 1.0e-6F), "anywhere else it stays");
        Quaternionf lying = new Quaternionf().rotateY((float) Math.toRadians(2.0)).rotateX((float) Math.PI / 2.0F);
        check(helper, Placing.detent(lying, 45.0F, 5.0F).equals(new Quaternionf().rotateX((float) Math.PI / 2.0F), 1.0e-5F),
                "a lying sheet settles square too");
        Quaternionf tilted = new Quaternionf().rotateY((float) Math.toRadians(3.0)).rotateZ(0.5F);
        check(helper, Placing.detent(tilted, 45.0F, 5.0F).equals(tilted, 1.0e-6F), "a thing at a lean is left as it is");
        check(helper, Placing.detent(near, 45.0F, 0.0F).equals(near, 1.0e-6F), "turn_detent 0: off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aNearMissLandsFlush(GameTestHelper helper) {
        PlacingOptions options = shelf();
        Scene scene = new Scene(options, 1);
        float half = CUBE.half(options.itemSize()).y;
        scene.put(0, new ItemStack(Items.STONE), CUBE, new ItemPose(8.0F, 1.0F, 9.0F, half, new Quaternionf(), 1.0F, -1));
        scene.run(0.2F);
        PlaceSurface surface = options.surfaces().get(0);
        // Let go a fifth of a pixel right of the stone: it lands against it.
        Vector3f spot = scene.physics.clearSpot(surface, new Vector3f(8.0F + half * 2.0F + 0.2F, 0.0F, 9.0F), half, half, 1.0F, 1.0F + half * 2.0F);
        check(helper, Math.abs(spot.x - (8.0F + half * 2.0F)) < 0.01F, "a hair beside its neighbour, it lands touching it: " + spot);
        // A hair from the side wall (x 1): against it.
        Vector3f wall = scene.physics.clearSpot(surface, new Vector3f(1.0F + half + 0.25F, 0.0F, 9.0F), half, half, 1.0F, 1.0F + half * 2.0F);
        check(helper, Math.abs(wall.x - (1.0F + half)) < 0.01F, "a hair from the wall, it lands against it: " + wall);
        // Farther than flush, it stays where it was let go.
        Vector3f free = scene.physics.clearSpot(surface, new Vector3f(8.0F + half * 2.0F + 0.6F, 0.0F, 9.0F), half, half, 1.0F, 1.0F + half * 2.0F);
        check(helper, Math.abs(free.x - (8.0F + half * 2.0F + 0.6F)) < 1.0e-4F, "farther off it stays: " + free);
        // Pressed into the stone from above a table (no aim_height), it keeps to the side nearer the cursor, not the front.
        Vector3f behind = scene.physics.clearSpot(surface, new Vector3f(8.0F, 0.0F, 9.0F + half), half, half, 1.0F, 1.0F + half * 2.0F);
        check(helper, behind.z > 9.0F, "seen from above it goes to the nearer side: " + behind);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------

    static Placing.Pickable pickable(Vector3f middle, Vector3f half, float least, float floor) {
        org.joml.Matrix4f toLocal = new org.joml.Matrix4f().translation(middle).invert();
        return new Placing.Pickable(toLocal, half, new Vector3f(half).max(new Vector3f(least * 0.5F)), null, floor);
    }

    /** The box of a cube of half size {@code half} standing at {@code pose}, as {@code Placing.boxOf} gives it. */
    static float[] box(ItemPose pose, float half) {
        float middle = pose.y() + pose.lift();
        return new float[] {pose.x() - half, pose.x() + half, middle - half, middle + half, pose.z() - half, pose.z() + half};
    }

    static boolean near(ItemPose a, ItemPose b, float pixels) {
        return a != null && b != null && Math.abs(a.x() - b.x()) < pixels && Math.abs(a.y() + a.lift() - b.y() - b.lift()) < pixels
                && Math.abs(a.z() - b.z()) < pixels && Math.abs(new Quaternionf(a.rotation()).dot(new Quaternionf(b.rotation()))) > 0.9999F;
    }

    static void check(GameTestHelper helper, boolean condition, String what) {
        if (!condition) {
            helper.fail(what);
        }
    }

    /** A scene for the physics: slots with items and poses, the player at the station, whatever is sent written down. */
    static final class Scene implements PlacedPhysics.Host {
        final PlacingOptions options;
        final List<ItemStack> stacks = new ArrayList<>();
        final List<PlacedPhysics.Solid> solids = new ArrayList<>();
        final List<ItemPose> poses = new ArrayList<>();
        final List<StationAction> sent = new ArrayList<>();
        final List<Integer> landings = new ArrayList<>();
        /** What the physics told: "impact 0>1", "settled 1 on 0", "fell 0", in order, and each impact's speed. */
        final List<String> told = new ArrayList<>();
        final List<Float> impactSpeeds = new ArrayList<>();
        final PlacedPhysics physics;

        Scene(PlacingOptions options, int slots) {
            this.options = options;
            for (int i = 0; i < slots; i++) {
                stacks.add(ItemStack.EMPTY);
                solids.add(null);
                poses.add(null);
            }
            physics = new PlacedPhysics(this, options, options.physics().orElseThrow(), slots);
        }

        void put(int index, ItemStack stack, PlacedPhysics.Solid solid, ItemPose pose) {
            stacks.set(index, stack);
            solids.set(index, solid);
            poses.set(index, pose);
        }

        List<ItemPose> poses() {
            return new ArrayList<>(poses);
        }

        void run(float seconds) {
            for (float time = 0.0F; time < seconds; time += FRAME) {
                physics.update(FRAME);
            }
        }

        @Override
        public ItemStack stack(int index) {
            return stacks.get(index);
        }

        @Override
        public PlacedPhysics.Solid solid(int index) {
            return solids.get(index);
        }

        @Override
        public ItemPose pose(int index) {
            return stacks.get(index).isEmpty() ? null : poses.get(index);
        }

        @Override
        public void setPose(int index, ItemPose pose) {
            poses.set(index, pose);
        }

        @Override
        public SlotRef slot(int index) {
            return SlotRef.of(options.group(), index);
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
            sent.add(action);
            if (action instanceof StationAction.Pose pose) {
                // The server's echo: the client's state takes what was sent.
                poses.set(pose.slot().index(), pose.pose());
            }
        }

        @Override
        public void hideWhileTaken(int index, long millis) {
        }

        @Override
        public void landed(int index, float speed, float mass, boolean flat) {
            landings.add(index);
        }

        @Override
        public void settled(int index, int under, float speed, float mass, boolean flat) {
            told.add("settled " + index + " on " + under);
        }

        @Override
        public void impact(int index, int other, float speed, float mass, org.joml.Vector3fc point) {
            told.add("impact " + index + ">" + other);
            impactSpeeds.add(speed);
        }

        @Override
        public float impactMinSpeed() {
            return com.pockyl.diegesis.api.scene.fx.FeedbackOptions.DEFAULT_IMPACT_SPEED;
        }

        @Override
        public void fellOff(int index) {
            told.add("fell " + index);
        }
    }
}
