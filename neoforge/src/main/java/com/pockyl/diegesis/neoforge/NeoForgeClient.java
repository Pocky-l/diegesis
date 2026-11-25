package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
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
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;
import com.pockyl.diegesis.impl.network.DiegesisNetwork;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client wiring on NeoForge; only constructed on clients. The scene is drawn from {@code mixin.LevelRendererSubmitMixin}, with
 * what the level submits for the frame.
 */
@Mod(value = Diegesis.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    /** The layers this frame begun to slide and not yet ended, and whether what mods draw after the HUD is. */
    private static final Set<Identifier> slid = new HashSet<>();
    private static boolean slidAfter;
    /** The scene models the last resource load baked, by the id a scene names them with. */
    private static volatile Map<Identifier, StandaloneModelKey<BlockStateModel>> sceneModels = Map.of();

    public NeoForgeClient(IEventBus modBus) {
        DiegesisClientRuntime.init();
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(SceneLibrary.ID, SceneLibrary.INSTANCE));
        // Asked on every model load, off the main thread, after the resource packs of that load are in place.
        modBus.addListener(ModelEvent.RegisterStandalone.class, event -> {
            Map<Identifier, StandaloneModelKey<BlockStateModel>> keys = new HashMap<>();
            for (Identifier model : SceneLibrary.modelsToLoad(Minecraft.getInstance().getResourceManager())) {
                StandaloneModelKey<BlockStateModel> key = new StandaloneModelKey<>(model::toString);
                event.register(key, SimpleUnbakedStandaloneModel.blockStateModel(model));
                keys.put(model, key);
            }
            sceneModels = Map.copyOf(keys);
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(DiegesisNetwork.ToClient.TYPE,
                (payload, context) -> {
                    if (payload.packet() != null) {
                        DiegesisNetwork.receiveOnClient(payload.packet());
                    }
                }));

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
    }

    /** The key a scene model was baked under; null when no scene names it. */
    static StandaloneModelKey<BlockStateModel> sceneModel(Identifier id) {
        return sceneModels.get(id);
    }

    /**
     * While the player works at a station its interface replaces the HUD (when the scene says so): every layer slides off to its
     * nearest edge, then is not drawn. The chat slides by a common mixin on both loaders ({@code ChatComponentMixin}) when the
     * scene keeps it; its layer is only hidden here.
     */
    private static void registerHud(IEventBus bus) {
        // Last of all listeners, so a layer another mod cancels is never begun, and every begun one ends in its Post.
        bus.addListener(EventPriority.HIGHEST, RenderGuiEvent.Pre.class, event -> HudSlide.frameStart());
        // First of all listeners: where a layer's pose begins, to take back what others pushed for one this mod then hides.
        bus.addListener(EventPriority.HIGHEST, true, RenderGuiLayerEvent.Pre.class,
                event -> HudSlide.partListeners(event.getGuiGraphics()));
        bus.addListener(EventPriority.LOWEST, RenderGuiLayerEvent.Pre.class, event -> {
            FocusController focus = FocusController.get();
            Identifier id = event.getName();
            boolean chat = id.equals(VanillaGuiLayers.CHAT);
            boolean hides = focus.isActive() && focus.options().hideHud() && !chat;
            // The crosshair is where the player aims, and the player no longer aims.
            if (hides && (HudSlide.gone() || id.equals(VanillaGuiLayers.CROSSHAIR))) {
                event.setCanceled(true);
                HudSlide.hidden(event.getGuiGraphics());
                return;
            }
            if (chat) {
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
