package com.pockyl.diegesis.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

/** Client wiring on Forge; only loaded on clients. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init(IEventBus modBus) {
        DiegesisClientRuntime.init();
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(SceneLibrary.INSTANCE));
        modBus.addListener((ModelEvent.RegisterAdditional event) ->
                SceneLibrary.modelsToLoad(Minecraft.getInstance().getResourceManager()).forEach(event::register));
        modBus.addListener((ModelEvent.ModifyBakingResult event) -> {
            for (com.pockyl.diegesis.api.station.StationType type : com.pockyl.diegesis.impl.StationRegistry.all()) {
                if (type.hidesBlock()) {
                    java.util.stream.Stream.concat(type.blocks().stream(), type.lateBlocks().stream())
                            .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                            .map(net.minecraft.client.renderer.block.BlockModelShaper::stateToModelLocation)
                            .forEach(location -> event.getModels().computeIfPresent(location, (key, model) -> new HideableModel(model)));
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("diegesis").then(Commands.literal("debug").executes(context -> {
                    DebugView.toggle();
                    return 1;
                }))));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                DiegesisClientRuntime.tick();
            }
        });
        // While the player works at a station its interface replaces the HUD (the chat is hidden by a mixin on both loaders).
        MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Pre event) -> {
            FocusController focus = FocusController.get();
            if (focus.isActive() && focus.options().hideHud() && !event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                SceneRenderer.render(event.getPoseStack(), event.getProjectionMatrix(), event.getCamera());
            }
        });
    }
}
