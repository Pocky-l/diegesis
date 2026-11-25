package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.hud.HudSlide;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

import java.util.HashSet;
import java.util.Set;

/** Client wiring on NeoForge; only constructed on clients. */
@Mod(value = Diegesis.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    /**
     * Layers the slide leaves alone: the chat slides by a common mixin ({@code ChatComponentMixin}), the camera overlays (vignette,
     * frost, a pumpkin's view) are not part of the HUD, and the decorations marker draws nothing.
     */
    private static final Set<Identifier> NOT_SLIDING = Set.of(VanillaGuiLayers.CHAT, VanillaGuiLayers.CAMERA_OVERLAYS,
            VanillaGuiLayers.AFTER_CAMERA_DECORATIONS);
    /** The layers this frame begun to slide and not yet ended, and whether what mods draw after the HUD is. */
    private static final Set<Identifier> slid = new HashSet<>();
    private static boolean slidAfter;

    public NeoForgeClient(IEventBus modBus) {
        DiegesisClientRuntime.init();
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, NeoForgeClientPlatform::registerPayloadHandler);
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(SceneLibrary.ID, SceneLibrary.INSTANCE));
        modBus.addListener(ModelEvent.RegisterStandalone.class, NeoForgeClientPlatform::registerSceneModels);
        modBus.addListener(ModelEvent.BakingCompleted.class, NeoForgeClientPlatform::sceneModelsBaked);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(
                Commands.literal("diegesis").then(Commands.literal("debug").executes(context -> {
                    DebugView.toggle();
                    return 1;
                }))));
        bus.addListener(ClientTickEvent.Post.class, event -> DiegesisClientRuntime.tick());
        // A station opens from the last listener of the block use, so a mod that takes the click comes first.
        bus.addListener(EventPriority.LOWEST, PlayerInteractEvent.RightClickBlock.class, event -> {
            if (!(event.getEntity() instanceof LocalPlayer player) || !event.getLevel().isClientSide()
                    || event.getUseBlock() == TriState.FALSE) {
                return;
            }
            InteractionResult result = DiegesisClientRuntime.onUseBlock(player, event.getHand(), event.getHitVec());
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
        registerHud(bus);
        // The scenes are handed to the frame with the level's other geometry, as Fabric's LevelRenderEvents.COLLECT_SUBMITS does.
        bus.addListener(SubmitCustomGeometryEvent.class, event -> {
            CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
            SceneRenderer.render(event.getSubmitNodeCollector(), event.getPoseStack(), camera.viewRotationMatrix, camera.projectionMatrix,
                    Minecraft.getInstance().gameRenderer.mainCamera());
        });
    }

    /**
     * While the player works at a station its interface replaces the HUD (when the scene says so): every layer slides off to its
     * nearest edge, then is not drawn. Other mods' layers slide one by one too. Last of all listeners, so a layer another mod
     * cancels is never begun, and every begun one ends in its Post.
     */
    private static void registerHud(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, RenderGuiEvent.Pre.class, event -> HudSlide.frameStart());
        // First of all listeners: where a layer's pose begins, to take back what others pushed for one this mod then hides.
        bus.addListener(EventPriority.HIGHEST, true, RenderGuiLayerEvent.Pre.class,
                event -> HudSlide.partListeners(event.getGuiGraphics()));
        bus.addListener(EventPriority.LOWEST, RenderGuiLayerEvent.Pre.class, event -> {
            Identifier id = event.getName();
            if (NOT_SLIDING.contains(id)) {
                return;
            }
            FocusController focus = FocusController.get();
            boolean hides = focus.isActive() && focus.options().hideHud();
            // The crosshair is where the player aims, and the player no longer aims.
            if (hides && (HudSlide.gone() || id.equals(VanillaGuiLayers.CROSSHAIR))) {
                event.setCanceled(true);
                HudSlide.hidden(event.getGuiGraphics());
                return;
            }
            slid.add(id);
            HudSlide.begin(id.equals(VanillaGuiLayers.HOTBAR) ? HudSlide.HOTBAR : id.toString(), event.getGuiGraphics());
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
            HudSlide.settle(event.getGuiGraphics());
            slidAfter = true;
            HudSlide.begin("neoforge:after_hud", event.getGuiGraphics());
        });
        bus.addListener(EventPriority.LOWEST, RenderGuiEvent.Post.class, event -> {
            if (slidAfter) {
                slidAfter = false;
                HudSlide.end(event.getGuiGraphics());
            }
        });
    }
}
