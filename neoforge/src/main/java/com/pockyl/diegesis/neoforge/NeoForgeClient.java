package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
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
import com.pockyl.diegesis.impl.client.hud.HudSlide;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/** Client wiring on NeoForge; only constructed on clients. */
@Mod(value = Diegesis.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    /** The layers this frame begun to slide and not yet ended, and whether what mods draw after the HUD is. */
    private static final Set<ResourceLocation> slid = new HashSet<>();
    private static boolean slidAfter;

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
        // While the player works at a station its interface replaces the HUD: every layer slides off to its nearest edge, then is
        // not drawn (the chat is hidden by a mixin on both loaders). Last of all listeners, so a layer another mod cancels is never
        // begun, and every begun one ends in its Post.
        bus.addListener(EventPriority.LOWEST, RenderGuiLayerEvent.Pre.class, event -> {
            FocusController focus = FocusController.get();
            ResourceLocation id = event.getName();
            boolean chat = id.equals(VanillaGuiLayers.CHAT);
            boolean hides = focus.isActive() && focus.options().hideHud() && !chat;
            // The crosshair is where the player aims, and the player no longer aims.
            if (hides && (HudSlide.gone() || id.equals(VanillaGuiLayers.CROSSHAIR))) {
                event.setCanceled(true);
                return;
            }
            if (chat && focus.isActive() && !focus.options().hideChat()) {
                return;
            }
            slid.add(id);
            HudSlide.begin(chat ? HudSlide.CHAT : id.equals(VanillaGuiLayers.HOTBAR) ? HudSlide.HOTBAR : id.toString(), event.getGuiGraphics());
        });
        bus.addListener(EventPriority.LOWEST, RenderGuiLayerEvent.Post.class, event -> {
            if (slid.remove(event.getName())) {
                HudSlide.end(event.getGuiGraphics());
            }
        });
        // What mods draw over the whole HUD at once slides as one part.
        bus.addListener(EventPriority.HIGHEST, RenderGuiEvent.Post.class, event -> {
            // A layer cancelled after this mod began it has no Post: it ends here, so that nothing stays moved.
            for (int i = slid.size(); i > 0; i--) {
                HudSlide.end(event.getGuiGraphics());
            }
            slid.clear();
            slidAfter = true;
            HudSlide.begin("neoforge:after_hud", event.getGuiGraphics());
        });
        bus.addListener(EventPriority.LOWEST, RenderGuiEvent.Post.class, event -> {
            if (slidAfter) {
                slidAfter = false;
                HudSlide.end(event.getGuiGraphics());
            }
        });
        bus.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                SceneRenderer.render(event.getPoseStack(), event.getModelViewMatrix(), event.getProjectionMatrix(), event.getCamera());
            }
        });
    }
}
