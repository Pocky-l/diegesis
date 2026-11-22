package com.pockyl.diegesis.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlacingPhysics;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.impl.client.scene.PlacedPhysics;
import com.pockyl.diegesis.impl.client.scene.Placing;

import java.util.List;

import static com.pockyl.diegesis.gametest.PlacingGameTests.CUBE;
import static com.pockyl.diegesis.gametest.PlacingGameTests.ICON;
import static com.pockyl.diegesis.gametest.PlacingGameTests.check;

/**
 * The physics left alone and worked hard: full shelves stay still, piles stay piles, drags never leave things inside each
 * other. Each test is a case the owner saw go wrong in a playtest.
 */
public final class PlacingStressTests {
    private static final float FRAME = 1.0F / 60.0F;

    private PlacingStressTests() {
    }

    /** A cabinet shelf open at the front, 14 by 11, room 8 high, items 2 pixels big. */
    static PlacingOptions cabinet() {
        return PlacingOptions.builder("items").surface(1.0F, 4.0F, 15.0F, 15.0F, 1.0F, 8.0F, 0.0F).itemSize(2.0F)
                .physics(PlacingPhysics.DEFAULT).build();
    }

    /**
     * A full shelf as players leave it: a row of icons standing face to face as put down before (drawn thickness apart), a grid
     * of blocks, and a tower of four blocks; {@code count} slots.
     */
    static PlacingGameTests.Scene fullShelf(int count, boolean oldRow) {
        PlacingOptions options = cabinet();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, count);
        float icon = ICON.half(options.itemSize()).y;
        float thin = oldRow ? ICON.half(options.itemSize()).z : com.pockyl.diegesis.impl.client.scene.PlacedPhysics.flatHalf(PlacingPhysics.DEFAULT);
        int slot = 0;
        for (int i = 0; i < 10; i++) {
            scene.put(slot++, new ItemStack(Items.BREAD), ICON, new ItemPose(3.0F, 1.0F, 5.0F + i * thin * 2.0F, icon, new Quaternionf(), 1.0F, -1));
        }
        float half = CUBE.half(options.itemSize()).y;
        Item[] blocks = {Items.STONE, Items.OAK_PLANKS, Items.IRON_BLOCK, Items.MELON, Items.COBBLESTONE};
        for (int i = 0; i < 12; i++) {
            float x = 6.0F + (i % 4) * (half * 2.0F + 0.3F);
            float z = 5.0F + (i / 4) * (half * 2.0F + 0.3F);
            scene.put(slot++, new ItemStack(blocks[i % blocks.length]), CUBE, new ItemPose(x, 1.0F, z, half, new Quaternionf(), 1.0F, -1));
        }
        float y = 1.0F;
        for (int i = 0; i < 4; i++) {
            scene.put(slot, new ItemStack(blocks[i]), CUBE, new ItemPose(12.0F, y, 12.0F, half, new Quaternionf(), 1.0F, i == 0 ? -1 : slot - 1));
            slot++;
            y += half * 2.0F;
        }
        return scene;
    }

    /** How far the furthest of the scene's items is from where {@code before} had it, pixels. */
    static float moved(PlacingGameTests.Scene scene, List<ItemPose> before) {
        float most = 0.0F;
        for (int i = 0; i < before.size(); i++) {
            ItemPose a = before.get(i);
            ItemPose b = scene.poses.get(i);
            if (a == null || b == null) {
                continue;
            }
            float d = (float) Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() + a.lift() - b.y() - b.lift()) * (a.y() + a.lift() - b.y() - b.lift())
                    + (a.z() - b.z()) * (a.z() - b.z()));
            most = Math.max(most, d);
        }
        return most;
    }

    static String report(PlacingGameTests.Scene scene, List<ItemPose> before) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < before.size(); i++) {
            ItemPose a = before.get(i);
            ItemPose b = scene.poses.get(i);
            if (a == null || b == null) {
                continue;
            }
            float d = (float) Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() + a.lift() - b.y() - b.lift()) * (a.y() + a.lift() - b.y() - b.lift())
                    + (a.z() - b.z()) * (a.z() - b.z()));
            if (d > 0.02F) {
                text.append(i).append(':').append(String.format("%.3f", d)).append(scene.physics.moving(i) ? "m " : " ");
            }
        }
        return text.toString();
    }

    public static void aFullShelfLeftAloneStaysStill(GameTestHelper helper) {
        shelfStill(helper, false);
    }

    public static void aShelfSetOutBeforeSettlesWithoutFlinging(GameTestHelper helper) {
        shelfStill(helper, true);
    }

    static void shelfStill(GameTestHelper helper, boolean oldRow) {
        PlacingGameTests.Scene scene = fullShelf(27, oldRow);
        List<ItemPose> start = scene.poses();
        scene.run(2.0F);
        float settle = moved(scene, start);
        String first = report(scene, start);
        List<ItemPose> rested = scene.poses();
        // Something changes at the station (another item put down): everything is woken once.
        scene.put(26, new ItemStack(Items.APPLE), CUBE, new ItemPose(3.0F, 1.0F, 12.0F, CUBE.half(2.0F).y, new Quaternionf(), 1.0F, -1));
        scene.run(10.0F);
        float later = moved(scene, rested);
        // Set out a hair inside each other, the row spreads by those hairs (nine of them, 0.1 each), no more.
        check(helper, settle < (oldRow ? 1.0F : 0.15F) && later < 0.03F, "settling " + settle + " [" + first + "], then " + later + " [" + report(scene, rested) + "]");
        helper.succeed();
    }

    /** Drags the item of slot {@code index} by hand along {@code path} (x, z points), {@code frames} frames from point to point. */
    static void drag(PlacingGameTests.Scene scene, int index, float[][] path, int frames) {
        ItemPose start = scene.poses.get(index);
        scene.physics.hold(index);
        for (int leg = 0; leg + 1 < path.length; leg++) {
            for (int frame = 1; frame <= frames; frame++) {
                float k = (float) frame / frames;
                float x = path[leg][0] + (path[leg + 1][0] - path[leg][0]) * k;
                float z = path[leg][1] + (path[leg + 1][1] - path[leg][1]) * k;
                ItemPose steered = new ItemPose(x, start.y(), z, start.lift(), start.rotation(), start.scale(), -1);
                scene.poses.set(index, steered);
                scene.physics.steer(index, steered);
                scene.physics.update(FRAME);
            }
        }
        scene.physics.release(index);
    }

    public static void aFastDragNeverLeavesThingsInsideEachOther(GameTestHelper helper) {
        PlacingGameTests.Scene scene = fullShelf(27, false);
        scene.run(1.0F);
        // A stone block swept fast to and fro, diagonally, through the row of icons and the other blocks.
        drag(scene, 10, new float[][] {{6.0F, 5.0F}, {2.0F, 12.0F}, {13.0F, 6.0F}, {3.0F, 7.0F}, {12.0F, 13.0F}}, 2);
        scene.run(3.0F);
        float deepest = scene.physics.deepestOverlap();
        check(helper, deepest < 0.05F, "nothing is left inside anything: " + deepest);
        helper.succeed();
    }

    public static void aPilePushedIntoAnotherDoesNotPassThroughIt(GameTestHelper helper) {
        PlacingOptions options = cabinet();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 6);
        float half = CUBE.half(options.itemSize()).y;
        for (int pile = 0; pile < 2; pile++) {
            float y = 1.0F;
            for (int i = 0; i < 3; i++) {
                int slot = pile * 3 + i;
                scene.put(slot, new ItemStack(Items.STONE), CUBE, new ItemPose(5.0F + pile * 4.0F, y, 9.0F, half, new Quaternionf(), 1.0F,
                        i == 0 ? -1 : slot - 1));
                y += half * 2.0F;
            }
        }
        scene.run(0.5F);
        // The first pile's base dragged right into the second pile, and on.
        drag(scene, 0, new float[][] {{5.0F, 9.0F}, {12.0F, 9.0F}}, 40);
        scene.run(3.0F);
        float deepest = scene.physics.deepestOverlap();
        check(helper, deepest < 0.05F, "no piece of one pile is inside the other: " + deepest);
        helper.succeed();
    }

    public static void aTowerStaysStillWhileSomethingElseIsDragged(GameTestHelper helper) {
        PlacingGameTests.Scene scene = fullShelf(27, false);
        scene.run(2.0F);
        List<ItemPose> rested = scene.poses();
        // An icon dragged slowly about for two seconds: its pose goes out every quarter second meanwhile.
        drag(scene, 0, new float[][] {{3.0F, 5.0F}, {3.0F, 13.0F}, {4.0F, 5.0F}}, 60);
        scene.run(2.0F);
        float tower = 0.0F;
        for (int i = 22; i < 26; i++) {
            ItemPose a = rested.get(i);
            ItemPose b = scene.poses.get(i);
            tower = Math.max(tower, Math.abs(a.x() - b.x()) + Math.abs(a.y() + a.lift() - b.y() - b.lift()) + Math.abs(a.z() - b.z()));
        }
        check(helper, tower < 0.03F, "the tower did not stir: " + tower + " [" + report(scene, rested) + "]");
        helper.succeed();
    }

    public static void aTowerBuiltByHandStandsStill(GameTestHelper helper) {
        PlacingOptions options = cabinet();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 4);
        float half = CUBE.half(options.itemSize()).y;
        Item[] blocks = {Items.OAK_PLANKS, Items.IRON_BLOCK, Items.MELON, Items.STONE};
        float top = 1.0F;
        for (int i = 0; i < 4; i++) {
            // Let go of from the hand a hover over the top of the tower, a little off its middle and turned a little.
            scene.physics.toss(i, 0.0F, 0.0F);
            Quaternionf turn = new Quaternionf().rotateY(0.05F * i);
            scene.put(i, new ItemStack(blocks[i]), CUBE, new ItemPose(8.0F + 0.2F * i, top, 9.0F - 0.1F * i, half + 1.5F, turn, 1.0F, i - 1));
            scene.run(1.0F);
            top = scene.poses.get(i).top();
        }
        scene.run(1.0F);
        List<ItemPose> built = scene.poses();
        scene.put(0, scene.stacks.get(0), CUBE, scene.poses.get(0));
        scene.run(10.0F);
        float moved = moved(scene, built);
        check(helper, moved < 0.03F && scene.physics.deepestOverlap() < 0.05F,
                "the tower stands still: " + moved + " [" + report(scene, built) + "], overlap " + scene.physics.deepestOverlap());
        helper.succeed();
    }

    public static void aStackSqueezedUnderTheShelfAboveStaysStill(GameTestHelper helper) {
        // A kitchen cabinet's shelf: room 6 high, things 4 big. A block, and one on it made as small as it must be to fit.
        PlacingOptions options = PlacingOptions.builder("items").surface(1.0F, 4.0F, 15.0F, 15.0F, 1.0F, 6.0F, 4.0F).itemSize(4.0F)
                .physics(PlacingPhysics.DEFAULT).build();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 3);
        float half = CUBE.half(options.itemSize()).y;
        scene.put(0, new ItemStack(Items.OAK_PLANKS), CUBE, new ItemPose(8.0F, 1.0F, 9.0F, half, new Quaternionf(), 1.0F, -1));
        float room = 6.0F - half * 2.0F;
        float scale = room / (half * 2.0F);
        scene.put(1, new ItemStack(Items.STONE), CUBE, new ItemPose(8.0F, 1.0F + half * 2.0F, 9.0F, room / 2.0F, new Quaternionf(), scale, 0));
        scene.run(1.0F);
        List<ItemPose> rested = scene.poses();
        // Something else is put down and dragged about: everything wakes meanwhile.
        scene.put(2, new ItemStack(Items.APPLE), CUBE, new ItemPose(3.0F, 1.0F, 6.0F, half, new Quaternionf(), 1.0F, -1));
        scene.run(0.5F);
        drag(scene, 0, new float[][] {{8.0F, 9.0F}, {10.0F, 10.0F}}, 40);
        scene.run(5.0F);
        ItemPose base = scene.poses.get(0);
        ItemPose top = scene.poses.get(1);
        check(helper, Math.abs(top.x() - base.x()) < 0.05F && Math.abs(top.z() - base.z()) < 0.05F && top.on() == 0,
                "the squeezed one rode along on its base: " + top + " on " + base);
        check(helper, scene.physics.deepestOverlap() < 0.05F && !scene.physics.moving(0) && !scene.physics.moving(1),
                "both at rest, nothing inside anything: " + scene.physics.deepestOverlap() + " [" + report(scene, rested) + "]");
        helper.succeed();
    }

    public static void aRiderCarriedIntoAnotherRiderPushesIt(GameTestHelper helper) {
        // A low pile (planks, a stone on them overhanging to the right) dragged towards a tall pile against the right wall: the
        // overhanging stone meets the tall pile's upper block before the bases meet.
        PlacingOptions options = cabinet();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 4);
        float half = CUBE.half(options.itemSize()).y;
        scene.put(0, new ItemStack(Items.OAK_PLANKS), new PlacedPhysics.Solid(new org.joml.Vector3f(0.5F, 0.5F, 0.5F), new Quaternionf(), 0.8F, false),
                new ItemPose(5.0F, 1.0F, 9.0F, half, new Quaternionf(), 1.0F, -1));
        scene.put(1, new ItemStack(Items.STONE), CUBE, new ItemPose(5.0F + half, 1.0F + half * 2.0F, 9.0F, half, new Quaternionf(), 1.0F, 0));
        scene.put(2, new ItemStack(Items.IRON_BLOCK), CUBE, new ItemPose(15.0F - half, 1.0F, 9.0F, half / 2.0F, new Quaternionf(), 0.5F, -1));
        scene.put(3, new ItemStack(Items.STONE), CUBE, new ItemPose(15.0F - half, 1.0F + half, 9.0F, half, new Quaternionf(), 1.0F, 2));
        scene.run(0.5F);
        drag(scene, 0, new float[][] {{5.0F, 9.0F}, {12.5F, 9.0F}}, 30);
        scene.run(3.0F);
        float deepest = scene.physics.deepestOverlap();
        check(helper, deepest < 0.05F, "the two piles' upper blocks are not inside each other: " + deepest + " " + scene.poses);
        helper.succeed();
    }

    public static void blocksLetGoOfMidSwingStackAndStandStill(GameTestHelper helper) {
        PlacingOptions options = cabinet();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 4);
        float half = CUBE.half(options.itemSize()).y;
        Item[] blocks = {Items.OAK_PLANKS, Items.IRON_BLOCK, Items.STONE, Items.MELON};
        float top = 1.0F;
        for (int i = 0; i < 4; i++) {
            // Let go of while the hand still moved: it comes down leaning (the swing's tilt), and rocks back onto its base.
            scene.physics.toss(i, 0.0F, 0.0F);
            Quaternionf lean = new Quaternionf().rotateZ(i % 2 == 0 ? 0.25F : -0.25F).rotateX(0.1F);
            float lift = Math.abs(CUBE.half(options.itemSize()).x * (float) Math.sin(0.35F)) + half + 1.5F;
            scene.put(i, new ItemStack(blocks[i]), CUBE, new ItemPose(8.0F + 0.15F * i, top, 9.0F, lift, lean, 1.0F, i - 1));
            scene.run(1.5F);
            top = scene.poses.get(i).top();
        }
        List<ItemPose> built = scene.poses();
        scene.run(4.0F);
        float moved = moved(scene, built);
        boolean resting = true;
        for (int i = 0; i < 4; i++) {
            resting &= !scene.physics.moving(i);
        }
        check(helper, moved < 0.03F && resting && scene.physics.deepestOverlap() < 0.05F,
                "the stack stands still: " + moved + " [" + report(scene, built) + "] " + scene.poses);
        helper.succeed();
    }

    public static void aBlockLetGoOfLeaningUnderTheShelfAboveStandsStill(GameTestHelper helper) {
        PlacingOptions options = PlacingOptions.builder("items").surface(1.0F, 4.0F, 15.0F, 15.0F, 1.0F, 6.0F, 4.0F).itemSize(4.0F)
                .physics(PlacingPhysics.DEFAULT).build();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 2);
        float half = CUBE.half(options.itemSize()).y;
        scene.put(0, new ItemStack(Items.OAK_PLANKS), CUBE, new ItemPose(8.0F, 1.0F, 9.0F, half, new Quaternionf(), 1.0F, -1));
        scene.run(0.5F);
        // Made as small as it must be to fit on the planks under the shelf above, let go of leaning a little.
        float room = 6.0F - half * 2.0F - 0.05F;
        float scale = room / (half * 2.0F);
        scene.physics.toss(1, 0.0F, 0.0F);
        Quaternionf lean = new Quaternionf().rotateZ(0.08F);
        scene.put(1, new ItemStack(Items.STONE), CUBE, new ItemPose(8.0F, 1.0F + half * 2.0F, 9.0F, room / 2.0F + 0.1F, lean, scale, 0));
        scene.run(2.0F);
        List<ItemPose> rested = scene.poses();
        scene.run(3.0F);
        check(helper, moved(scene, rested) < 0.03F && !scene.physics.moving(1) && scene.physics.deepestOverlap() < 0.05F,
                "it settles and stays: " + moved(scene, rested) + " " + scene.poses.get(1));
        helper.succeed();
    }

    public static void aDragFollowsTheCursorDiagonally(GameTestHelper helper) {
        PlacingOptions options = cabinet();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 1);
        float half = CUBE.half(options.itemSize()).y;
        scene.put(0, new ItemStack(Items.STONE), CUBE, new ItemPose(4.0F, 1.0F, 6.0F, half, new Quaternionf(), 1.0F, -1));
        scene.run(0.3F);
        // Grabbed 3 pixels behind its middle (a shelf's slope aims behind what the cursor is on), then dragged diagonally: four GUI
        // pixels of the cursor to a pixel of the shelf.
        float offsetX = 0.5F;
        float offsetZ = -3.0F;
        float blend = options.handling().gripBlend();
        scene.physics.hold(0);
        float most = 0.0F;
        for (int frame = 0; frame <= 60; frame++) {
            float aimX = 3.5F + frame * 0.1F;
            float aimZ = 9.0F + frame * 0.08F;
            double travel = Math.hypot(frame * 0.1F, frame * 0.08F) * 4.0;
            float share = Placing.gripShare(travel, blend);
            ItemPose steered = new ItemPose(aimX + offsetX * share, 1.0F, aimZ + offsetZ * share, half, new Quaternionf(), 1.0F, -1);
            scene.poses.set(0, steered);
            scene.physics.steer(0, steered);
            scene.physics.update(FRAME);
            if (frame > 30) {
                most = Math.max(most, Math.abs(scene.poses.get(0).z() - aimZ) + Math.abs(scene.poses.get(0).x() - aimX));
            }
        }
        scene.physics.release(0);
        scene.run(1.0F);
        ItemPose end = scene.poses.get(0);
        check(helper, Math.abs(end.x() - 9.5F) < 0.3F && Math.abs(end.z() - 13.8F) < 0.3F, "it went where the cursor went: " + end);
        check(helper, most < 0.6F, "following the cursor in both ways, a little behind it: " + most);
        helper.succeed();
    }

    public static void aFlatThingLetGoOfAtALeanRestsAsItFalls(GameTestHelper helper) {
        PlacingOptions options = cabinet();
        PlacingGameTests.Scene scene = new PlacingGameTests.Scene(options, 2);
        float half = ICON.half(options.itemSize()).y;
        // One stood up straight, one let go of at a 40 degree lean, both on their own.
        scene.put(0, new ItemStack(Items.PAPER), ICON, new ItemPose(4.0F, 1.0F, 8.0F, half, new Quaternionf(), 1.0F, -1));
        Quaternionf lean = new Quaternionf().rotateX(0.7F);
        scene.put(1, new ItemStack(Items.BREAD), ICON, new ItemPose(10.0F, 1.0F, 8.0F, half, lean, 1.0F, -1));
        scene.run(4.0F);
        org.joml.Vector3f standing = new Quaternionf(scene.poses.get(0).rotation()).transform(new org.joml.Vector3f(0.0F, 0.0F, 1.0F));
        org.joml.Vector3f fallen = new Quaternionf(scene.poses.get(1).rotation()).transform(new org.joml.Vector3f(0.0F, 0.0F, 1.0F));
        check(helper, Math.abs(standing.y) < 0.05F, "stood up straight, it stays standing: " + scene.poses.get(0));
        check(helper, Math.abs(fallen.y) > 0.99F && scene.poses.get(1).y() + scene.poses.get(1).lift() < 1.3F,
                "at a lean it fell flat, not frozen at the lean: " + scene.poses.get(1));
        check(helper, !scene.physics.moving(0) && !scene.physics.moving(1), "both at rest");
        helper.succeed();
    }
}
