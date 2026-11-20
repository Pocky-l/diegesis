package com.pockyl.diegesis.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.InteractionResult;

import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.hud.HudSlide;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class DiegesisFabricClient implements ClientModInitializer {
    /**
     * The HUD's parts that slide off while a player works at a station, by Fabric's id for each, and the name the slide keeps for
     * it. The chat slides by a common mixin ({@code ChatComponentMixin}); the crosshair is not drawn at all.
     */
    private static final Map<Identifier, String> SLIDING = Map.ofEntries(
            Map.entry(VanillaHudElements.HOTBAR, HudSlide.HOTBAR),
            Map.entry(VanillaHudElements.HEALTH_BAR, "health"),
            Map.entry(VanillaHudElements.ARMOR_BAR, "health"),
            Map.entry(VanillaHudElements.FOOD_BAR, "health"),
            Map.entry(VanillaHudElements.AIR_BAR, "health"),
            Map.entry(VanillaHudElements.MOUNT_HEALTH, "vehicle_health"),
            Map.entry(VanillaHudElements.INFO_BAR, "experience"),
            Map.entry(VanillaHudElements.EXPERIENCE_LEVEL, "experience_level"),
            Map.entry(VanillaHudElements.HELD_ITEM_TOOLTIP, "item_name"),
            Map.entry(VanillaHudElements.MOB_EFFECTS, "effects"),
            Map.entry(VanillaHudElements.SCOREBOARD, "scoreboard"),
            Map.entry(VanillaHudElements.BOSS_BAR, "boss"));
    /** The scene models the last resource load baked, by the id a scene names them with. */
    private static volatile Map<Identifier, ExtraModelKey<BlockStateModel>> sceneModels = Map.of();

    @Override
    public void onInitializeClient() {
        DiegesisClientRuntime.init();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("controlify")) {
            com.pockyl.diegesis.fabric.compat.ControlifyCompat.init();
        }
        FabricNetwork.registerClient();
        ClientTickEvents.END_CLIENT_TICK.register(client -> DiegesisClientRuntime.tick());
        // A station opens in a phase after everybody's block use, so a mod that takes the click comes first.
        Identifier openStation = Identifier.fromNamespaceAndPath("diegesis", "open_station");
        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, openStation);
        UseBlockCallback.EVENT.register(openStation, (player, level, hand, hit) -> {
            if (!level.isClientSide() || !(player instanceof LocalPlayer local)) {
                return InteractionResult.PASS;
            }
            InteractionResult result = DiegesisClientRuntime.onUseBlock(local, hand, hit);
            return result != null ? result : InteractionResult.PASS;
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("diegesis").then(ClientCommands.literal("debug").executes(command -> {
                    DebugView.toggle();
                    return 1;
                }))));
        registerHud();
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            CameraRenderState camera = context.levelState().cameraRenderState;
            SceneRenderer.render(context.submitNodeCollector(), context.poseStack(), camera.viewRotationMatrix, camera.projectionMatrix,
                    Minecraft.getInstance().gameRenderer.mainCamera());
        });
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(SceneLibrary.ID, SceneLibrary.INSTANCE);
        PreparableModelLoadingPlugin.register(
                (PreparableReloadListener.SharedState state, Executor executor) -> CompletableFuture.supplyAsync(
                        () -> SceneLibrary.modelsToLoad(state.resourceManager()), executor),
                (Set<Identifier> models, ModelLoadingPlugin.Context context) -> {
                    Map<Identifier, ExtraModelKey<BlockStateModel>> keys = new HashMap<>();
                    for (Identifier model : models) {
                        ExtraModelKey<BlockStateModel> key = ExtraModelKey.create(model::toString);
                        context.addModel(key, SimpleUnbakedExtraModel.blockStateModel(model));
                        keys.put(model, key);
                    }
                    sceneModels = Map.copyOf(keys);
                });
    }

    /** The key a scene model was baked under; null when no scene names it. */
    static ExtraModelKey<BlockStateModel> sceneModel(Identifier id) {
        return sceneModels.get(id);
    }

    /**
     * While the player works at a station its interface replaces the HUD (when the scene says so): each part slides off to its
     * nearest edge, then is not drawn. The parts other mods add slide only when they are drawn through vanilla's.
     */
    private static void registerHud() {
        SLIDING.forEach((id, key) -> HudElementRegistry.replaceElement(id, element -> sliding(key, element)));
        // The crosshair is where the player aims, and the player no longer aims.
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, element -> (graphics, deltaTracker) -> {
            if (!hides()) {
                element.extractRenderState(graphics, deltaTracker);
            }
        });
    }

    /** A part not drawn once the HUD is off the screen, else drawn where the slide has it. */
    private static HudElement sliding(String key, HudElement element) {
        return (graphics, deltaTracker) -> {
            if (hides() && HudSlide.gone()) {
                return;
            }
            HudSlide.begin(key, graphics);
            try {
                element.extractRenderState(graphics, deltaTracker);
            } finally {
                HudSlide.end(graphics);
            }
        };
    }

    private static boolean hides() {
        FocusController focus = FocusController.get();
        return focus.isActive() && focus.options().hideHud();
    }
}
