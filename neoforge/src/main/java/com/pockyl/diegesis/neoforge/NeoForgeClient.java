package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.diegesis.impl.StationRegistry;
import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

import java.util.stream.Stream;

/** Client wiring on NeoForge; only constructed on clients. */
@Mod(value = Diegesis.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus modBus) {
        DiegesisClientRuntime.init();
        modBus.addListener(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(SceneLibrary.INSTANCE));
        modBus.addListener(ModelEvent.RegisterAdditional.class, event -> SceneLibrary.modelsToLoad(Minecraft.getInstance()
                .getResourceManager()).forEach(id -> event.register(ModelResourceLocation.standalone(id))));
        modBus.addListener(ModelEvent.ModifyBakingResult.class, event -> {
            for (StationType type : StationRegistry.all()) {
                if (type.hidesBlock()) {
                    Stream.concat(type.blocks().stream(), type.lateBlocks().stream())
                            .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                            .map(BlockModelShaper::stateToModelLocation)
                            .forEach(location -> event.getModels().computeIfPresent(location, (key, model) -> new HideableModel(model)));
                }
            }
        });

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(
                Commands.literal("diegesis").then(Commands.literal("debug").executes(context -> {
                    DebugView.toggle();
                    return 1;
                }))));
        bus.addListener(ClientTickEvent.Post.class, event -> DiegesisClientRuntime.tick());
        // While the player works at a station its interface replaces the HUD (the chat is hidden by a mixin on both loaders).
        bus.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            FocusController focus = FocusController.get();
            if (focus.isActive() && focus.options().hideHud() && !event.getName().equals(VanillaGuiLayers.CHAT)) {
                event.setCanceled(true);
            }
        });
        bus.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                SceneRenderer.render(event.getPoseStack(), event.getModelViewMatrix(), event.getProjectionMatrix(), event.getCamera());
            }
        });
    }
}
