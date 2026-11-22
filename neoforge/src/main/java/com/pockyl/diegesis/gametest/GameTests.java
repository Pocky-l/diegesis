package com.pockyl.diegesis.gametest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.Diegesis;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Every game test of the library, run headless by {@code gradlew :neoforge:runGameTestServer}: a function in the test function
 * registry and a test instance each, registered only when game tests are on. The tests themselves live in the classes of this
 * package; a new one adds a row here. Structures: {@code empty} (no blocks) and {@code box}, in {@code data/diegesis/structure}.
 */
public final class GameTests {
    /** The ticks a test has unless its row names another. */
    private static final int TIMEOUT = 100;
    private static final Identifier BOX = DiegesisMod.id("box");
    private static final Identifier EMPTY = DiegesisMod.id("empty");
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION,
            Diegesis.MOD_ID);
    private static final List<Test> TESTS = new ArrayList<>();

    private record Test(String name, Consumer<GameTestHelper> function, Identifier structure, int timeout) {
    }

    static {
        // FeedbackGameTests
        test("effects_and_particle_styles_parse", FeedbackGameTests::effectsAndParticleStylesParse, EMPTY);
        test("feedback_parses_and_changes_nothing_by_default", FeedbackGameTests::feedbackParsesAndChangesNothingByDefault, EMPTY);
        test("marks_come_from_the_node_and_its_liquid", FeedbackGameTests::marksComeFromTheNodeAndItsLiquid, EMPTY);

        // ListenerGameTests
        test("listeners_refuse_but_never_allow", ListenerGameTests::listenersRefuseButNeverAllow, BOX);

        // MenuGameTests
        test("menu_clicks_craft_a_pickaxe", MenuGameTests::menuClicksCraftAPickaxe, BOX);
        test("menu_clicks_bulk_craft_and_spread", MenuGameTests::menuClicksBulkCraftAndSpread, BOX);
        test("menu_keys_swap_throw_and_put_away", MenuGameTests::menuKeysSwapThrowAndPutAway, BOX);
        test("the_off_hand_moves_through_vanilla_swaps", MenuGameTests::theOffHandMovesThroughVanillaSwaps, BOX);
        test("furnace_shift_clicks_route_as_vanilla", MenuGameTests::furnaceShiftClicksRouteAsVanilla, BOX);
        test("menu_stations_check_their_binding", MenuGameTests::menuStationsCheckTheirBinding, EMPTY);

        // ModGameTests
        test("mod_loads", ModGameTests::modLoads, EMPTY);
        test("clicks_move_stacks_like_vanilla", ModGameTests::clicksMoveStacksLikeVanilla, EMPTY);
        test("a_right_click_puts_the_hand_away", ModGameTests::aRightClickPutsTheHandAway, EMPTY);
        test("bound_slots_are_a_window_on_their_storage", ModGameTests::boundSlotsAreAWindowOnTheirStorage, EMPTY);
        test("clicks_respect_slot_rules", ModGameTests::clicksRespectSlotRules, EMPTY);
        test("shift_double_click_and_spread", ModGameTests::shiftDoubleClickAndSpread, EMPTY);
        test("outputs_come_from_the_logic", ModGameTests::outputsComeFromTheLogic, EMPTY);
        test("controls_stay_in_range", ModGameTests::controlsStayInRange, EMPTY);
        test("multiblocks_open_at_their_main_block", ModGameTests::multiblocksOpenAtTheirMainBlock, BOX);
        test("pouring_keeps_the_volume", ModGameTests::pouringKeepsTheVolume, EMPTY);
        test("the_server_paces_pouring", ModGameTests::theServerPacesPouring, BOX);
        test("placed_items_stand_where_they_are_put", ModGameTests::placedItemsStandWhereTheyArePut, EMPTY);
        test("a_squeezed_thing_remembers_its_size", ModGameTests::aSqueezedThingRemembersItsSize, EMPTY);
        test("strokes_are_for_nodes_without_a_role", ModGameTests::strokesAreForNodesWithoutARole, EMPTY);
        test("state_saves_and_syncs", ModGameTests::stateSavesAndSyncs, EMPTY);
        test("station_data_saves_and_syncs", ModGameTests::stationDataSavesAndSyncs, EMPTY);
        test("players_take_the_nearest_free_seat_and_sit", ModGameTests::playersTakeTheNearestFreeSeatAndSit, BOX);
        test("messages_round_trip", ModGameTests::messagesRoundTrip, EMPTY);
        test("scenes_parse_and_report_errors", ModGameTests::scenesParseAndReportErrors, EMPTY);
        test("tipping_keeps_the_liquid", ModGameTests::tippingKeepsTheLiquid, EMPTY);
        test("node_templates_expand", ModGameTests::nodeTemplatesExpand, EMPTY);
        test("session_returns_items_on_close", ModGameTests::sessionReturnsItemsOnClose, BOX);
        test("walking_away_or_logging_out_gives_items_back", ModGameTests::walkingAwayOrLoggingOutGivesItemsBack, BOX);
        test("many_stations_stay_cheap", ModGameTests::manyStationsStayCheap, BOX, 200);

        // PlacingGameTests
        test("physics_rests_where_things_are_put", PlacingGameTests::physicsRestsWhereThingsArePut, EMPTY);
        test("physics_keeps_a_stack_standing", PlacingGameTests::physicsKeepsAStackStanding, EMPTY);
        test("stacking_goes_to_the_top_of_the_pile", PlacingGameTests::stackingGoesToTheTopOfThePile, EMPTY);
        test("a_low_thing_is_all_top_at_any_item_size", PlacingGameTests::aLowThingIsAllTopAtAnyItemSize, EMPTY);
        test("pick_takes_the_exact_hit_first", PlacingGameTests::pickTakesTheExactHitFirst, EMPTY);
        test("a_thin_thing_has_an_edge_to_hit", PlacingGameTests::aThinThingHasAnEdgeToHit, EMPTY);
        test("stacking_follows_the_cursor_on_a_wide_support", PlacingGameTests::stackingFollowsTheCursorOnAWideSupport, EMPTY);
        test("stacking_snaps_to_the_middle", PlacingGameTests::stackingSnapsToTheMiddle, EMPTY);
        test("a_stacked_thing_keeps_its_middle_on_its_support", PlacingGameTests::aStackedThingKeepsItsMiddleOnItsSupport, EMPTY);
        test("the_grip_offset_fades_with_travel", PlacingGameTests::theGripOffsetFadesWithTravel, EMPTY);
        test("a_pile_rides_its_base", PlacingGameTests::aPileRidesItsBase, EMPTY);
        test("a_pile_falls_when_its_base_tips", PlacingGameTests::aPileFallsWhenItsBaseTips, EMPTY);
        test("taking_from_a_pile_lowers_the_rest", PlacingGameTests::takingFromAPileLowersTheRest, EMPTY);
        test("a_dragged_thing_stays_on_its_supports_top", PlacingGameTests::aDraggedThingStaysOnItsSupportsTop, EMPTY);
        test("a_box_half_over_an_open_edge_sits_flat", PlacingGameTests::aBoxHalfOverAnOpenEdgeSitsFlat, EMPTY);
        test("a_box_past_its_middle_falls_off", PlacingGameTests::aBoxPastItsMiddleFallsOff, EMPTY);
        test("a_falling_pile_knocks_and_then_rests_quietly", PlacingGameTests::aFallingPileKnocksAndThenRestsQuietly, EMPTY);
        test("a_lying_flat_thing_does_not_rock", PlacingGameTests::aLyingFlatThingDoesNotRock, EMPTY);
        test("flat_things_stand_flush", PlacingGameTests::flatThingsStandFlush, EMPTY);
        test("flat_things_lie_on_a_lie_surface", PlacingGameTests::flatThingsLieOnALieSurface, EMPTY);
        test("same_things_shift_clicked_onto_a_lie_surface_pile_up", PlacingGameTests::sameThingsShiftClickedOntoALieSurfacePileUp, EMPTY);
        test("a_middle_surface_fills_from_its_middle", PlacingGameTests::aMiddleSurfaceFillsFromItsMiddle, EMPTY);
        test("stacking_shrinks_at_most_a_quarter", PlacingGameTests::stackingShrinksAtMostAQuarter, EMPTY);
        test("a_put_down_sounds_once_as_it_lands", PlacingGameTests::aPutDownSoundsOnceAsItLands, EMPTY);
        test("held_poses_are_sent_in_steps", PlacingGameTests::heldPosesAreSentInSteps, EMPTY);
        test("a_turn_near_square_settles_on_it", PlacingGameTests::aTurnNearSquareSettlesOnIt, EMPTY);
        test("a_near_miss_lands_flush", PlacingGameTests::aNearMissLandsFlush, EMPTY);

        // PlacingHeapTests
        test("a_heap_of_flat_things_comes_to_rest", PlacingHeapTests::aHeapOfFlatThingsComesToRest, EMPTY, 600);
        test("a_mixed_heap_comes_to_rest", PlacingHeapTests::aMixedHeapComesToRest, EMPTY, 600);
        test("a_crowded_drawer_comes_to_rest", PlacingHeapTests::aCrowdedDrawerComesToRest, EMPTY, 600);
        test("a_heap_on_an_open_shelf_comes_to_rest", PlacingHeapTests::aHeapOnAnOpenShelfComesToRest, EMPTY, 600);
        test("no_thing_spins_while_a_heap_settles", PlacingHeapTests::noThingSpinsWhileAHeapSettles, EMPTY, 600);
        test("putting_one_more_wakes_only_what_it_touches", PlacingHeapTests::puttingOneMoreWakesOnlyWhatItTouches, EMPTY, 600);
        test("waking_a_heap_ten_times_moves_nothing", PlacingHeapTests::wakingAHeapTenTimesMovesNothing, EMPTY, 600);
        test("a_heavy_block_on_a_flat_thing_stands_still", PlacingHeapTests::aHeavyBlockOnAFlatThingStandsStill, EMPTY, 600);
        test("a_block_woken_again_and_again_never_creeps", PlacingHeapTests::aBlockWokenAgainAndAgainNeverCreeps, EMPTY, 600);
        test("flat_things_across_each_other_lie_still", PlacingHeapTests::flatThingsAcrossEachOtherLieStill, EMPTY, 600);
        test("a_thing_put_inside_another_glides_out", PlacingHeapTests::aThingPutInsideAnotherGlidesOut, EMPTY, 600);
        test("a_standing_flat_thing_under_a_load_rests", PlacingHeapTests::aStandingFlatThingUnderALoadRests, EMPTY, 600);
        test("things_fall_as_gravity_says", PlacingHeapTests::thingsFallAsGravitySays, EMPTY, 600);
        test("a_heap_costs_little", PlacingHeapTests::aHeapCostsLittle, EMPTY, 600);

        // PlacingStressTests
        test("a_full_shelf_left_alone_stays_still", PlacingStressTests::aFullShelfLeftAloneStaysStill, EMPTY, 400);
        test("a_shelf_set_out_before_settles_without_flinging", PlacingStressTests::aShelfSetOutBeforeSettlesWithoutFlinging, EMPTY, 400);
        test("a_fast_drag_never_leaves_things_inside_each_other", PlacingStressTests::aFastDragNeverLeavesThingsInsideEachOther, EMPTY, 400);
        test("a_pile_pushed_into_another_does_not_pass_through_it", PlacingStressTests::aPilePushedIntoAnotherDoesNotPassThroughIt, EMPTY, 400);
        test("a_tower_stays_still_while_something_else_is_dragged", PlacingStressTests::aTowerStaysStillWhileSomethingElseIsDragged, EMPTY, 400);
        test("a_tower_built_by_hand_stands_still", PlacingStressTests::aTowerBuiltByHandStandsStill, EMPTY, 400);
        test("a_stack_squeezed_under_the_shelf_above_stays_still", PlacingStressTests::aStackSqueezedUnderTheShelfAboveStaysStill, EMPTY, 400);
        test("a_rider_carried_into_another_rider_pushes_it", PlacingStressTests::aRiderCarriedIntoAnotherRiderPushesIt, EMPTY, 400);
        test("blocks_let_go_of_mid_swing_stack_and_stand_still", PlacingStressTests::blocksLetGoOfMidSwingStackAndStandStill, EMPTY, 400);
        test("a_block_let_go_of_leaning_under_the_shelf_above_stands_still", PlacingStressTests::aBlockLetGoOfLeaningUnderTheShelfAboveStandsStill, EMPTY, 400);
        test("a_drag_follows_the_cursor_diagonally", PlacingStressTests::aDragFollowsTheCursorDiagonally, EMPTY, 400);
        test("a_flat_thing_let_go_of_at_a_lean_rests_as_it_falls", PlacingStressTests::aFlatThingLetGoOfAtALeanRestsAsItFalls, EMPTY, 400);

        // PlacingSyncTests
        test("what_rests_is_what_the_server_has", PlacingSyncTests::whatRestsIsWhatTheServerHas, EMPTY, 600);
        test("taking_a_thing_leaves_the_others_where_they_rest", PlacingSyncTests::takingAThingLeavesTheOthersWhereTheyRest, EMPTY, 600);
        test("a_server_resend_mid_settle_does_not_pull_things_back", PlacingSyncTests::aServerResendMidSettleDoesNotPullThingsBack, EMPTY, 600);
        test("things_at_rest_never_move_by_themselves", PlacingSyncTests::thingsAtRestNeverMoveByThemselves, EMPTY, 800);
    }

    private GameTests() {
    }

    private static void test(String name, Consumer<GameTestHelper> function, Identifier structure) {
        test(name, function, structure, TIMEOUT);
    }

    private static void test(String name, Consumer<GameTestHelper> function, Identifier structure, int timeout) {
        TESTS.add(new Test(name, function, structure, timeout));
    }

    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        for (Test test : TESTS) {
            FUNCTIONS.register(test.name(), test::function);
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(GameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(DiegesisMod.id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test test : TESTS) {
            Identifier id = DiegesisMod.id(test.name());
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, test.structure(), test.timeout(), 0, true,
                    Rotation.NONE, false, 1, 1, false, 0);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }
}
