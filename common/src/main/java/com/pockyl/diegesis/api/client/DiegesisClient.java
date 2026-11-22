package com.pockyl.diegesis.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.impl.client.ClientRegistry;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.ClientStations;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.fx.LiquidRenderer;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;

import java.util.Optional;

/**
 * The library's entry point for client code. Call the {@code register} methods from your client setup; never from
 * common code (a dedicated server has no client classes).
 */
public final class DiegesisClient {
    private DiegesisClient() {
    }

    /** Client code (animations, sounds, effects) for the stations of a type. Each type has one; a second is an error. */
    public static void registerBehavior(Identifier station, StationBehavior.Factory factory) {
        ClientRegistry.registerBehavior(station, factory);
    }

    /**
     * Client code added to a station type that is not yours, or that already has its own behaviour: sparks on another mod's
     * anvil, a sound when its kettle boils. It runs after the station's own behaviour, as many as are added, in that order.
     * Hooks that take input ({@code onItemClick}, {@code onScroll}, keys, {@code onInventoryShiftClick}) reach it only when no
     * behaviour before it took the input, so it can add gestures but never take one away.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public static void addBehavior(Identifier station, StationBehavior.Factory factory) {
        ClientRegistry.addBehavior(java.util.Objects.requireNonNull(station, "station"), factory);
    }

    /** Like {@link #addBehavior(Identifier, StationBehavior.Factory)}, for every station of every mod. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public static void addBehavior(StationBehavior.Factory factory) {
        ClientRegistry.addBehavior(null, factory);
    }

    /**
     * A scene built in code instead of a JSON file. A JSON file with the same id
     * ({@code assets/<namespace>/diegesis/scenes/<path>.json}) still wins, so resource packs can restyle it.
     */
    public static void registerScene(Identifier id, SceneDefinition scene) {
        ClientRegistry.registerScene(id, scene);
    }

    /**
     * Loads a block model that scenes use but that is not under {@code models/diegesis/} (every model there is loaded
     * by itself). {@code model} is a model id: {@code mymod:block/flask} for {@code assets/mymod/models/block/flask.json}.
     */
    public static void registerModel(Identifier model) {
        ClientRegistry.registerModel(model);
    }

    /**
     * Gives an item a look made from its data: in inventories, hands, frames and on shelves it is the shape {@code shape} makes
     * for the stack, with {@code texture} (a sprite of the block atlas) on it. The shape stands on the middle of the block's floor,
     * in pixels. The item still needs a model file, for its display transforms; shapes are kept per item data.
     */
    public static void registerItemShape(net.minecraft.world.item.Item item, java.util.function.Function<net.minecraft.world.item.ItemStack, MeshShape> shape,
            Identifier texture) {
        com.pockyl.diegesis.impl.client.mesh.ItemShapes.register(item, shape, texture);
    }

    /** A scene particle style, for emitters, effects and {@link StationView#spawnParticles}. */
    public static void registerParticle(Identifier id, ParticleStyle style) {
        ClientRegistry.registerParticle(id, style);
    }

    /** How a liquid looks in tanks (its id is a fluid's or one the mod made up for a reagent). */
    public static void registerLiquid(Identifier id, LiquidLook look) {
        ClientRegistry.registerLiquid(id, look);
    }

    /** The colour a mixture is drawn in (the liquids' looks blended by amount), ARGB: for particles of the same colour. */
    public static int liquidColor(TankContents contents) {
        return LiquidRenderer.look(contents).color();
    }

    /** An effect the stations' logic can play with {@code StationContext.effect(id, ...)}. */
    public static void registerEffect(Identifier id, EffectHandler handler) {
        ClientRegistry.registerEffect(id, handler);
    }

    /**
     * Shows or hides the debug view: the hit box of every node (coloured by role, with its id), the laid-out inventory
     * slots, the work plane and the camera target. Also on while vanilla hit boxes are (F3+B) and with the client
     * command {@code /diegesis debug}.
     */
    public static void setDebugView(boolean on) {
        DebugView.set(on);
    }

    public static boolean isDebugView() {
        return DebugView.isOn();
    }

    /** Whether the local player is working at a station (the camera is at it). */
    public static boolean isFocused() {
        return FocusController.get().isActive();
    }

    /**
     * How far the camera has flown over from one station to a neighbour ({@code camera.neighbors}), eased, 0 to 1; 1 when it is not
     * flying over. Below 1 while the station left hears {@code onClose}: it can hand what floats in the air over to the next one,
     * which is {@link #focusedStation()} from then on, also before the server has let the player in.
     */
    public static float goingOver() {
        return FocusController.get().goingOver();
    }

    /**
     * How far the camera has come from the player's view to the station worked at, eased, 0 to 1: rising while it flies in, falling
     * while it flies back after {@code onClose} ({@link #focusedStation()} is still the station left then), 1 at the station and
     * while going over to a neighbour, 0 when nobody works at one. What comes with a station, such as a book that hangs by it, can
     * appear and go with this, in step with the camera.
     */
    public static float arrival() {
        return FocusController.get().arrival();
    }

    /**
     * Screens that may open over a station without ending it, such as a recipe viewer's page opened by a key over an item: the
     * station, its camera and a menu station's menu wait under it, and the player is back at the station when it returns to the
     * station's screen. Any other screen ends the work at the station, as before.
     */
    public static void registerChildScreen(java.util.function.Predicate<net.minecraft.client.gui.screens.Screen> test) {
        com.pockyl.diegesis.impl.client.ClientRegistry.registerChildScreen(test);
    }

    /**
     * Where a world point is on the screen, in GUI pixels, as the last frame was drawn; empty when it is behind the camera. For laying
     * other mods' overlays round what a station shows.
     */
    public static Optional<org.joml.Vector2f> toScreen(net.minecraft.world.phys.Vec3 world) {
        return Optional.ofNullable(com.pockyl.diegesis.impl.client.focus.Projector.toScreen(world));
    }

    /** The class of a station's screen, for mods that register handlers by screen class (a recipe viewer's overlay). */
    public static Class<? extends net.minecraft.client.gui.screens.Screen> stationScreenClass() {
        return com.pockyl.diegesis.impl.client.focus.FocusScreen.class;
    }

    /** Whether {@code screen} is the screen of a station being worked at: a parent a child screen returns to. */
    public static boolean isStationScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof com.pockyl.diegesis.impl.client.focus.FocusScreen;
    }

    /**
     * The stack under the cursor at the station the local player works at: in an inventory slot, a station slot or an output; empty
     * elsewhere. For recipe viewers' keys over an item.
     */
    public static net.minecraft.world.item.ItemStack hoveredStack() {
        return com.pockyl.diegesis.impl.client.focus.FocusScreen.hoveredStack();
    }

    /**
     * Buffers for an item a {@link NodePainter} draws: the scene's item type, which writes depth, so the item hides what is behind it
     * and its enchantment glint shows, as on the items lying in slots. Pass the painter's buffers.
     */
    public static com.pockyl.diegesis.api.client.SceneBuffers items(com.pockyl.diegesis.api.client.SceneBuffers buffers) {
        return com.pockyl.diegesis.impl.client.render.SceneRenderer.solidItems(buffers);
    }

    /** The station the local player works at. */
    public static Optional<StationView> focusedStation() {
        return Optional.ofNullable(FocusController.get().station());
    }

    /**
     * Buffers for a {@link NodePainter} that multiply an ARGB colour into what is drawn through them; an alpha below 255 fades it. Items
     * and models cannot be tinted otherwise. Block items (a carrot, a mushroom) ask for the block sheet's solid or cutout type, which
     * ignores alpha: through these buffers they get the see-through item type, so they fade like any other icon. Items drawn by
     * their own renderer as entity models (a banner, a shield, a chest) ask for an entity type that ignores alpha too
     * ({@code entity_solid}, {@code entity_cutout}...): they get the see-through item type of the same texture. Other models must be
     * drawn with a see-through render type to fade.
     */
    public static com.pockyl.diegesis.api.client.SceneBuffers tinted(com.pockyl.diegesis.api.client.SceneBuffers buffers, int argb) {
        boolean fading = argb >>> 24 < 255;
        return type -> {
            net.minecraft.client.renderer.rendertype.RenderType drawn = fading ? seeThrough(type) : type;
            if (drawn != type) {
                com.pockyl.diegesis.impl.client.render.KeptTypes.keep(buffers, type);
            }
            return new com.pockyl.diegesis.impl.client.render.TintedConsumer(buffers.getBuffer(drawn), argb);
        };
    }

    /**
     * The see-through twin of an opaque type an item or block model asks for, on the same atlas (block items draw from the block
     * atlas, other icons from the item atlas since Minecraft 26.x); the type itself when it is see-through already.
     */
    private static net.minecraft.client.renderer.rendertype.RenderType seeThrough(net.minecraft.client.renderer.rendertype.RenderType type) {
        if (type == com.pockyl.diegesis.impl.client.render.DiegesisRenderTypes.cutoutBlockSheet()
                || type == com.pockyl.diegesis.impl.client.render.DiegesisRenderTypes.solidBlockSheet()
                || type == net.minecraft.client.renderer.rendertype.RenderTypes.cutoutMovingBlock()
                || type == net.minecraft.client.renderer.rendertype.RenderTypes.solidMovingBlock()
                || type == net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet()) {
            return net.minecraft.client.renderer.Sheets.translucentBlockItemSheet();
        }
        if (type == net.minecraft.client.renderer.Sheets.cutoutItemSheet()) {
            return net.minecraft.client.renderer.Sheets.translucentItemSheet();
        }
        return OPAQUE_ENTITY.computeIfAbsent(type, DiegesisClient::seeThroughEntity);
    }

    /** Entity types that ignore alpha, by name, as a render type prints itself: "RenderType[name:RenderSetup[...]]". */
    private static final java.util.Set<String> OPAQUE_ENTITY_NAMES = java.util.Set.of("entity_solid", "entity_solid_z_offset_forward",
            "entity_cutout", "entity_cutout_cull", "entity_cutout_z_offset");
    private static final java.util.regex.Pattern ENTITY_TEXTURE = java.util.regex.Pattern.compile(
            "Sampler0=TextureBinding\\[location=([a-z0-9_.-]+:[a-z0-9_/.-]+)");
    private static final java.util.Map<net.minecraft.client.renderer.rendertype.RenderType, net.minecraft.client.renderer.rendertype.RenderType>
            OPAQUE_ENTITY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The see-through twin of an opaque entity type (a banner's cloth, a shield, a chest drawn as an item): the see-through item
     * type with the same texture; any other type stays as it is.
     */
    private static net.minecraft.client.renderer.rendertype.RenderType seeThroughEntity(net.minecraft.client.renderer.rendertype.RenderType type) {
        String printed = type.toString();
        int start = printed.indexOf('[');
        int end = printed.indexOf(':', start + 1);
        if (start < 0 || end < 0 || !OPAQUE_ENTITY_NAMES.contains(printed.substring(start + 1, end))) {
            return type;
        }
        java.util.regex.Matcher texture = ENTITY_TEXTURE.matcher(printed);
        return texture.find() ? net.minecraft.client.renderer.rendertype.RenderTypes.itemTranslucent(
                net.minecraft.resources.Identifier.parse(texture.group(1))) : type;
    }

    /** A station shown on this client at {@code pos}. */
    public static Optional<StationView> station(BlockPos pos) {
        return Optional.ofNullable(ClientStations.get().at(pos));
    }

    /**
     * Whether the scene of the station at {@code pos} is drawn in this frame: the station is shown and the camera is near enough.
     * A station is kept a little farther than its scene is drawn, so a block entity renderer that a scene stands in for should
     * stop exactly when this is true, or the block is missing in between. Asked while the level is drawn, before the scenes.
     */
    public static boolean isSceneDrawn(BlockPos pos) {
        ClientStation station = ClientStations.get().at(pos);
        return station != null && SceneRenderer.isDrawn(station, Minecraft.getInstance().gameRenderer.mainCamera().position());
    }
}
