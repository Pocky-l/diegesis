package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
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
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client wiring on NeoForge; only constructed on clients. */
@Mod(value = Diegesis.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    /**
     * The key each scene model is baked under, by the id a scene names it with. Kept across resource loads: keys are compared by
     * identity, so a model asked for while a new load bakes is still found in the last one.
     */
    private static final Map<Identifier, StandaloneModelKey<BlockStateModel>> SCENE_MODELS = new ConcurrentHashMap<>();
    /** The scene models the last resource load baked. */
    private static volatile Set<Identifier> loaded = Set.of();
    /** The layers this frame begun to slide and not yet ended, and whether what mods draw after the HUD is. */
    private static final Set<Identifier> slid = new HashSet<>();
    private static boolean slidAfter;

    public NeoForgeClient(IEventBus modBus) {
        DiegesisClientRuntime.init();
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, NeoForgeNetwork::registerClient);
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(SceneLibrary.ID, SceneLibrary.INSTANCE));
        // Fired on each resource load, off the main thread, after the new packs are in place.
        modBus.addListener(ModelEvent.RegisterStandalone.class, event -> {
            Set<Identifier> models = SceneLibrary.modelsToLoad(Minecraft.getInstance().getResourceManager());
            for (Identifier model : models) {
                event.register(SCENE_MODELS.computeIfAbsent(model, id -> new StandaloneModelKey<>(id::toString)),
                        SimpleUnbakedStandaloneModel.blockStateModel(model));
            }
            loaded = Set.copyOf(models);
        });

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
        bus.addListener(SubmitCustomGeometryEvent.class, event -> {
            CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
            SceneRenderer.render(event.getSubmitNodeCollector(), event.getPoseStack(), camera.viewRotationMatrix, camera.projectionMatrix,
                    Minecraft.getInstance().gameRenderer.getMainCamera());
        });
    }

    /** The key a scene model was baked under; null when no scene names it. */
    static StandaloneModelKey<BlockStateModel> sceneModel(Identifier id) {
        return loaded.contains(id) ? SCENE_MODELS.get(id) : null;
    }

    /**
     * While the player works at a station its interface replaces the HUD (when the scene says so): every layer slides off to its
     * nearest edge, then is not drawn. The chat slides by a common mixin ({@code ChatComponentMixin}). Last of all listeners, so a
     * layer another mod cancels is never begun, and every begun one ends in its Post.
     */
    private static void registerHud(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, RenderGuiLayerEvent.Pre.class, event -> {
            Identifier id = event.getName();
            // The chat slides by itself; the next layer only starts a new group of what is drawn.
            if (id.equals(VanillaGuiLayers.CHAT) || id.equals(VanillaGuiLayers.AFTER_CAMERA_DECORATIONS)) {
                return;
            }
            FocusController focus = FocusController.get();
            boolean hides = focus.isActive() && focus.options().hideHud();
            // The crosshair is where the player aims, and the player no longer aims.
            if (hides && (HudSlide.gone() || id.equals(VanillaGuiLayers.CROSSHAIR))) {
                event.setCanceled(true);
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
