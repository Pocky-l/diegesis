package com.pockyl.diegesis.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.hud.HudSlide;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

import java.util.HashSet;
import java.util.Set;

/** Client wiring on Forge; only loaded on clients. */
final class ForgeClient {
    /** The overlays this frame begun to slide and not yet ended, and whether what mods draw after the HUD is. */
    private static final Set<ResourceLocation> slid = new HashSet<>();
    private static boolean slidAfter;

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
        // While the player works at a station its interface replaces the HUD: every overlay slides off to its nearest edge, then
        // is not drawn (the chat is hidden by a mixin on both loaders). Last of all listeners, so an overlay another mod cancels
        // is never begun, and every begun one ends in its Post.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderGuiOverlayEvent.Pre event) -> {
            FocusController focus = FocusController.get();
            ResourceLocation id = event.getOverlay().id();
            boolean chat = id.equals(VanillaGuiOverlay.CHAT_PANEL.id());
            boolean hides = focus.isActive() && focus.options().hideHud() && !chat;
            // The crosshair is where the player aims, and the player no longer aims.
            if (hides && (HudSlide.gone() || id.equals(VanillaGuiOverlay.CROSSHAIR.id()))) {
                event.setCanceled(true);
                return;
            }
            if (chat && focus.isActive() && !focus.options().hideChat()) {
                return;
            }
            slid.add(id);
            HudSlide.begin(chat ? HudSlide.CHAT : id.equals(VanillaGuiOverlay.HOTBAR.id()) ? HudSlide.HOTBAR : id.toString(),
                    event.getGuiGraphics());
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderGuiOverlayEvent.Post event) -> {
            if (slid.remove(event.getOverlay().id())) {
                HudSlide.end(event.getGuiGraphics());
            }
        });
        // What mods draw over the whole HUD at once slides as one part.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (RenderGuiEvent.Post event) -> {
            // An overlay cancelled after this mod began it has no Post: it ends here, so that nothing stays moved.
            for (int i = slid.size(); i > 0; i--) {
                HudSlide.end(event.getGuiGraphics());
            }
            slid.clear();
            slidAfter = true;
            HudSlide.begin("forge:after_hud", event.getGuiGraphics());
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderGuiEvent.Post event) -> {
            if (slidAfter) {
                slidAfter = false;
                HudSlide.end(event.getGuiGraphics());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                SceneRenderer.render(event.getPoseStack(), event.getProjectionMatrix(), event.getCamera());
            }
        });
    }
}
