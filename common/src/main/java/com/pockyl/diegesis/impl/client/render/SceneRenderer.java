package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.LightCoordsUtil;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.scene.InterfaceOptions;
import com.pockyl.diegesis.api.scene.InventoryLayout;
import com.pockyl.diegesis.api.scene.LightDefinition;
import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlacingPhysics;
import com.pockyl.diegesis.api.scene.PourOptions;
import com.pockyl.diegesis.api.scene.SlotBinding;
import com.pockyl.diegesis.api.scene.TankBinding;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.impl.action.StationAction.Target;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.ClientStations;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.fx.LiquidRenderer;
import com.pockyl.diegesis.impl.client.fx.SceneParticles;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.focus.FocusInput;
import com.pockyl.diegesis.impl.client.focus.Projector;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;
import com.pockyl.diegesis.impl.client.scene.PlacedItem;
import com.pockyl.diegesis.impl.client.scene.PlacedShape;
import com.pockyl.diegesis.impl.client.scene.SceneInstance;
import com.pockyl.diegesis.platform.ClientPlatform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Draws every shown station's scene into the level, after the entities, with the same render types as entities and
 * item frames - so shader packs light and shade them like the rest of the world. Carried things cast a shadow of
 * their own shape onto the work surface. Also captures the frame's camera for the cursor ray.
 */
public final class SceneRenderer {
    /** Stations farther than this (blocks) are not drawn. */
    private static final double RENDER_RANGE = 40.0;
    /**
     * How far (blocks) a station's scene may reach out of its block and still count as in view: a multiblock's other blocks, a
     * spoon or a lid standing up, steam over a pot.
     */
    private static final double VIEW_MARGIN = 2.0;
    /**
     * Stations out of view advance in steps this long (seconds), as in a game at 20 frames a second: what nobody sees needs no
     * smoother motion, and a street of idle stations cost every frame as much as the one at hand.
     */
    private static final float UNSEEN_STEP = 0.05F;
    /**
     * Stations in view but farther than this (blocks) advance in steps of {@link #FAR_STEP} (seconds): there what moves in a scene
     * moves by a pixel or less a step, and a town's pots in view cost more in motion than in drawing.
     */
    private static final double FAR_STEP_FROM = 24.0;
    private static final float FAR_STEP = 1.0F / 30.0F;
    /** Shadows and highlights: block-atlas geometry, see-through, back faces culled (no double layers in a shadow). */
    private static final RenderType OVERLAY = net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
    /**
     * Shadows of item-atlas geometry. Since Minecraft 26.x items have an atlas of their own: drawn into {@link #OVERLAY}, an item's
     * shape was read from the block atlas and its shadow came out as a dark rectangle.
     */
    private static final RenderType ITEM_OVERLAY = net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_ITEMS);
    /** The render types that draw from the item atlas: the game's item sheets and the entity types {@code SceneDraw.sheet} gives. */
    private static final java.util.Set<RenderType> ITEM_ATLAS_TYPES = java.util.Set.of(Sheets.cutoutItemSheet(),
            Sheets.cutoutItemGlintSheet(), Sheets.cutoutItemGlintSpecialSheet(), Sheets.translucentItemSheet(),
            Sheets.translucentItemGlintSheet(), Sheets.translucentItemGlintSpecialSheet(),
            net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TextureAtlas.LOCATION_ITEMS),
            net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_ITEMS));
    /** Everything see-through of the scene goes here, in a buffer of its own: it stays one sorted batch. */
    private static final RenderType CLEAR = DiegesisRenderTypes.clearBlockSheet();
    /** Pixels between an item's right side and its count. */
    private static final float COUNT_GAP = 0.3F;
    /** Opaque items of the scene, drawn after the opaque parts and before CLEAR, with depth: see {@link #solidItems}. */
    private static final RenderType ITEMS = DiegesisRenderTypes.itemSheet();
    /**
     * The game's item types (each atlas, cut out and see-through, with glints): items write depth, so they go in before what is
     * see-through, which would otherwise be drawn over by an item behind it.
     */
    private static final List<RenderType> ITEM_TYPES = List.of(Sheets.cutoutBlockItemSheet(), Sheets.cutoutBlockItemGlintSheet(),
            Sheets.cutoutBlockItemGlintSpecialSheet(), Sheets.translucentBlockItemSheet(), Sheets.translucentBlockItemGlintSheet(),
            Sheets.translucentBlockItemGlintSpecialSheet(), Sheets.cutoutItemSheet(), Sheets.cutoutItemGlintSheet(),
            Sheets.cutoutItemGlintSpecialSheet(), Sheets.translucentItemSheet(), Sheets.translucentItemGlintSheet(),
            Sheets.translucentItemGlintSpecialSheet());
    /**
     * Where the scene's batches go among the game's submits: after the entities' (order 0), one order per batch, so that the
     * game draws them in the library's order. Solid geometry goes to the game's solid pass whatever its order; of what is
     * see-through, the shadows and highlights come first, as they lie on the opaque parts, then the opaque items, then what is
     * see-through (one sorted batch), then the rest (particles, glints), and the counts that stand over everything last.
     */
    private static final int ORDER_OVERLAY = 1000;
    private static final int ORDER_ITEMS = 1001;
    private static final int ORDER_CLEAR = 1002;
    private static final int ORDER_REST = 1003;
    private static final int ORDER_OUTLINES = 1004;
    private static final int ORDER_COUNTS = 1005;
    /** The scene's own buffers this frame (the see-through sheet drawn as CLEAR). */
    private static SceneBuffers sceneSource;
    /** What is see-through: one batch, sorted back to front. */
    private static final RecordedBuffers CLEAR_BUFFERS = new RecordedBuffers();
    /** Everything else of the scene, handed to the game batch by batch in the order above. */
    private static final RecordedBuffers SCENE_BUFFERS = new RecordedBuffers();
    /** The silhouettes of what gets an outline this frame, in the game's outline types. */
    private static final RecordedBuffers OUTLINE_BUFFERS = new RecordedBuffers();
    /**
     * The flat sprite that highlights are tinted from: this library's own off-white, as white concrete was, so that a resource pack
     * that retextures concrete does not show through every highlighted slot.
     */
    private static final Identifier HIGHLIGHT_SPRITE = Identifier.fromNamespaceAndPath("diegesis", "block/white");
    /** Shadow lean per pixel of height: light from above, a little from the front left, like the GUI's light. */
    private static final float SHADOW_SHEAR_X = -0.22F;
    /** How much of its darkness the landing mark under the stack in the hand loses at the full hover height. */
    private static final float LANDING_FADE = 0.45F;
    private static final float SHADOW_SHEAR_Z = 0.3F;
    /** Where the scene's light comes from, for highlights on liquids: the opposite of the shadows' lean. */
    private static final Vector3fc LIGHT_DIRECTION = new Vector3f(-SHADOW_SHEAR_X, 1.0F, -SHADOW_SHEAR_Z).normalize();
    private static final Set<Identifier> WARNED_MODELS = new HashSet<>();
    /** Pools of light: a soft round gradient made in code (no texture file), laid on the surface. */
    private static RenderType pool;
    private static final List<Count> PENDING_COUNTS = new ArrayList<>();
    /** What gets an outline this frame (the usable object under the cursor), drawn once the scene is done. */
    private static final List<Consumer<SceneBuffers>> PENDING_OUTLINES = new ArrayList<>();
    /**
     * Outlines of nodes that show only what is seen of them ({@code NodeView.setOutlineSeenOnly}). Before Minecraft 26.x their
     * silhouette was tested against the frame's depth; the game's outline pass has no such test, so for now they are outlined
     * whole, as they were under a shader pack.
     */
    private static final List<Consumer<SceneBuffers>> PENDING_SEEN_OUTLINES = new ArrayList<>();
    private static int outlineColor = 0xFFFFFF;
    /** Thickness of the board under a laid-out inventory, in pixels. */
    private static final float BOARD_THICKNESS = 1.0F;

    private static long lastFrame = Util.getNanos();
    /** The interface options of the station being drawn. */
    private static InterfaceOptions ui = InterfaceOptions.DEFAULT;
    /** The frame's camera and level pose (camera-relative, not turned), for the debug view's labels. */
    private static Camera debugCamera;
    private static PoseStack debugPose;

    /** The level's view turned back: from the poses of this frame into the world's directions. */
    private static final Matrix4f TO_WORLD = new Matrix4f();
    /** The block of the station being drawn, which a visual lit as its block takes its light from. */
    private static net.minecraft.core.BlockPos drawnPos;
    /** The camera the station is drawn from, to find the world block a model lit as a block stands in. */
    private static Vec3 drawnCamera;
    /** The scene's lights while its nodes are drawn, for the models lit as a block; null outside that. */
    private static List<float[]> drawnLights;
    private static List<com.pockyl.diegesis.api.scene.PlaceSurface> drawnSurfaces = List.of();
    /** From the pose stack's space into the drawn station's pixels, points and normals. */
    private static final Matrix4f TO_STATION = new Matrix4f();
    private static final org.joml.Matrix3f NORMAL_TO_STATION = new org.joml.Matrix3f();

    private SceneRenderer() {
    }

    /**
     * Called by the loader's level-render hook while the game collects what the level draws (after the entities have submitted
     * theirs). The scene is drawn into the library's own buffers, then handed to {@code collector} batch by batch.
     *
     * @param poseStack  the level pose stack: relative to the camera, not turned to it (the view rotation is in the model-view
     *                   matrix the game draws with)
     * @param modelView  the frame's view matrix: the camera's rotation
     * @param projection the frame's projection matrix
     */
    public static void render(SubmitNodeCollector collector, PoseStack poseStack, Matrix4f modelView, Matrix4f projection, Camera camera) {
        try {
            renderStations(collector, poseStack, modelView, projection, camera);
        } finally {
            SCENE_BUFFERS.clear();
            CLEAR_BUFFERS.clear();
            OUTLINE_BUFFERS.clear();
            PENDING_COUNTS.clear();
            PENDING_OUTLINES.clear();
            PENDING_SEEN_OUTLINES.clear();
            // After every station's frame: the blocks their scenes now stand in for are left out of the next frame's chunks.
            com.pockyl.diegesis.impl.client.HiddenBlocks.frame();
            ClientStations.get().syncHiddenBlocks();
        }
    }

    private static void renderStations(SubmitNodeCollector collector, PoseStack poseStack, Matrix4f modelView, Matrix4f projection,
            Camera camera) {
        Projector.capture(projection, new Matrix4f(modelView).mul(poseStack.last().pose()), camera.position());
        // The level pose is not turned to the camera here: turning it back to the world only undoes what it holds.
        TO_WORLD.set(poseStack.last().pose()).invert();
        SmoothPass.begin(projection, modelView);
        long now = Util.getNanos();
        float dt = Math.min((now - lastFrame) / 1.0e9F, 0.1F);
        lastFrame = now;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        if (FocusController.get().isInteractive()) {
            FocusInput.get().frameFromRenderer();
        }
        Vec3 cameraPos = camera.position();
        debugCamera = camera;
        debugPose = new PoseStack();
        debugPose.last().pose().set(poseStack.last().pose());
        debugPose.last().normal().set(poseStack.last().normal());
        List<ClientStation> stations = new ArrayList<>(ClientStations.get().all());
        if (stations.isEmpty()) {
            return;
        }
        RenderType translucent = DiegesisRenderTypes.translucentCullBlockSheet();
        SceneBuffers scene = type -> type != translucent ? SCENE_BUFFERS.getBuffer(type) : CLEAR_BUFFERS.getBuffer(CLEAR);
        sceneSource = scene;
        // The frustum the game culls this frame with: Minecraft 26.x keeps it on the camera, made with a projection for culling
        // (the frame's own has its depth reversed and no far side).
        Frustum view = camera.getCullFrustum();
        for (ClientStation station : stations) {
            // A neighbour in the camera's way at another station is left out with its block (ClearBlocks): its scene would hang
            // in the air before the camera, where the block it stands in for is gone.
            boolean drawn = isDrawn(station, cameraPos) && !ClearBlocks.hides(station.pos());
            // Only what is in view is drawn, and only what is in view and near moves every frame; the station worked at always does.
            // Where the block is seen: on a moving structure its block is kept far away from there.
            Vec3 center = station.center();
            boolean seen = station.isInSession() || drawn && view.isVisible(AABB.ofSize(center, 1.0, 1.0, 1.0).inflate(VIEW_MARGIN));
            if (seen && (station.isInSession() || center.distanceToSqr(cameraPos) < FAR_STEP_FROM * FAR_STEP_FROM)) {
                station.frame(dt);
            } else if (seen) {
                station.frameUnseen(dt, FAR_STEP);
            } else {
                station.frameUnseen(dt, UNSEEN_STEP);
            }
            if (!drawn || !seen) {
                continue;
            }
            // Each station draws on its own copy of the pose stack: a failure halfway can never leave the level's stack
            // unbalanced (the game checks it and crashes).
            PoseStack own = new PoseStack();
            own.last().pose().set(poseStack.last().pose());
            own.last().normal().set(poseStack.last().normal());
            try {
                renderStation(station, own, cameraPos, scene, level);
            } catch (RuntimeException e) {
                DiegesisMod.LOGGER.error("Drawing station {} at {} failed", station.type().id(), station.pos(), e);
            }
        }
        // Opaque parts first (the game's solid pass takes every type without blending, whatever its order): wells, highlights and
        // shadows lie on them and must not hide them from the depth test. Then what is see-through, which nothing drawn later may
        // cross; particles last, so that a thick liquid does not bury them.
        SCENE_BUFFERS.submit(collector.order(ORDER_OVERLAY), OVERLAY);
        SCENE_BUFFERS.submit(collector.order(ORDER_OVERLAY), ITEM_OVERLAY);
        SCENE_BUFFERS.submit(collector.order(ORDER_ITEMS), ITEMS);
        for (RenderType type : ITEM_TYPES) {
            SCENE_BUFFERS.submit(collector.order(ORDER_ITEMS), type);
        }
        CLEAR_BUFFERS.submitAll(collector.order(ORDER_CLEAR));
        SCENE_BUFFERS.submitAll(collector.order(ORDER_REST));
        if (!PENDING_OUTLINES.isEmpty() || !PENDING_SEEN_OUTLINES.isEmpty()) {
            drawOutlines(collector);
        }
        // The counts that stand over everything, last: blocks, items and liquids drawn before them never cover them.
        submitCounts(collector);
    }

    /**
     * The outline round the usable object under the cursor: its silhouette in the game's outline types (the glowing effect's), in the
     * outline colour, which the game turns into a line round its edge and lays over the frame as it does for glowing entities.
     */
    private static void drawOutlines(SubmitNodeCollector collector) {
        if (Minecraft.getInstance().gameRenderer.mainCamera().isPanoramicMode()) {
            return;
        }
        int color = 0xFF000000 | outlineColor;
        // Only the silhouette: every render type is swapped for its outline type; what has none (glints) is dropped.
        SceneBuffers silhouettes = type -> type.outline()
                .<VertexConsumer>map(outline -> new OutlineConsumer(OUTLINE_BUFFERS.getBuffer(outline), color))
                .orElse(NoVertices.INSTANCE);
        for (Consumer<SceneBuffers> draw : PENDING_OUTLINES) {
            draw.accept(silhouettes);
        }
        for (Consumer<SceneBuffers> draw : PENDING_SEEN_OUTLINES) {
            draw.accept(silhouettes);
        }
        OUTLINE_BUFFERS.submitAll(collector.order(ORDER_OUTLINES));
    }

    /**
     * The counts that stand on top of everything (turned to the camera, or all with {@code counts_on_top}): see-through text,
     * submitted after the rest of the scene, so that a chest or glass next to the station never covers them, and neither does
     * the outline round the item under the cursor.
     */
    private static void submitCounts(SubmitNodeCollector collector) {
        if (PENDING_COUNTS.isEmpty()) {
            return;
        }
        RecordedBuffers counts = new RecordedBuffers();
        for (Count count : PENDING_COUNTS) {
            SceneDraw.text(counts, count.text(), count.x(), count.y(), 0xFF3F3F3F, count.shadow(), Font.DisplayMode.SEE_THROUGH,
                    LightCoordsUtil.FULL_BRIGHT);
        }
        for (Count count : PENDING_COUNTS) {
            SceneDraw.text(counts, count.text(), count.x(), count.y(), 0xFFFFFFFF, count.front(), Font.DisplayMode.SEE_THROUGH,
                    LightCoordsUtil.FULL_BRIGHT);
        }
        counts.submitAll(collector.order(ORDER_COUNTS));
    }

    /** Writes the vertices it is given in one colour (the outline's), as the game's outline buffers do. */
    private record OutlineConsumer(VertexConsumer target, int color) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            target.addVertex(x, y, z).setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            target.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }
    }

    /** Whether the player can do something with a node: carry it, operate it, click it, put or take an item. */
    private static boolean isUsable(NodeDefinition definition, ClientStation station) {
        if (definition.control().isPresent()) {
            return !FocusInput.isLockedControl(station, definition);
        }
        if (definition.grabbable().isPresent()) {
            return true;
        }
        if (definition.slot().isPresent()) {
            return !station.state().getItem(definition.slot().get().slot()).isEmpty();
        }
        if (definition.output().isPresent()) {
            return !station.state().getOutput(definition.output().get().output()).isEmpty();
        }
        // A node with a hit box of its own and something to see is there to be clicked.
        return definition.collider().isPresent() && definition.visual().isPresent();
    }

    /** A copy of the pose stack's top, for drawing later. */
    private static PoseStack snapshot(PoseStack poseStack) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(poseStack.last().pose());
        copy.last().normal().set(poseStack.last().normal());
        return copy;
    }

    /** Whether {@code station}'s scene is drawn from a camera at {@code camera}: it has one, and it is near enough. */
    public static boolean isDrawn(ClientStation station, Vec3 camera) {
        return station.scene() != null && station.center().distanceToSqr(camera) <= RENDER_RANGE * RENDER_RANGE;
    }

    private static void renderStation(ClientStation station, PoseStack poseStack, Vec3 camera, SceneBuffers buffers,
            ClientLevel level) {
        drawnPos = station.pos();
        drawnCamera = camera;
        BlockPos pos = station.pos();
        int light = lightAt(level, pos);
        // How far the camera is, for the detail of what is drawn; the station worked at is always drawn in full.
        float distance = FocusController.get().station() == station ? 0.0F : (float) Math.sqrt(station.center().distanceToSqr(camera));
        poseStack.pushPose();
        // The block's place relative to the camera, in doubles: on a moving structure its blocks are kept far away and the pose
        // brings them back, which floats would lose.
        multiply(poseStack, new Matrix4f(station.blockToWorld(new Matrix4d()).translateLocal(-camera.x, -camera.y, -camera.z)));
        multiply(poseStack, station.stationToWorld(new Matrix4f()));

        SceneInstance scene = station.scene();
        ui = scene.definition().ui();
        boolean local = FocusController.get().station() == station && FocusController.get().isInteractive();
        FocusInput input = FocusInput.get();
        FocusInput.Carried carried = local ? input.carried() : null;
        float workPlane = scene.definition().workPlane();
        SceneBuffers shadows = new ShadowBuffers(buffers);
        List<float[]> lights = lightsOf(scene);
        drawnLights = lights.isEmpty() ? null : lights;
        // Surfaces that are moved (a door's shelves) are not where the placing writes them: they hide no light.
        List<com.pockyl.diegesis.api.scene.PlaceSurface> still = new ArrayList<>();
        scene.definition().placing().ifPresent(placing -> {
            for (int i = 0; i < placing.surfaces().size(); i++) {
                if (scene.surfaceMove(i) == null) {
                    still.add(placing.surfaces().get(i));
                }
            }
        });
        drawnSurfaces = still;
        TO_STATION.set(poseStack.last().pose()).invert();
        NORMAL_TO_STATION.set(poseStack.last().normal()).invert();

        for (NodeInstance node : scene.nodes()) {
            if (!node.isVisible()) {
                continue;
            }
            NodeDefinition definition = node.definition();
            int nodeLight = lit(light, node.matrix().getTranslation(new Vector3f()), lights);
            if (node.placed() != null) {
                if (!scene.isPlacingVisible()) {
                    continue;
                }
                renderPlaced(node, local && ui.hoverOutline() && input.isHandFree(), poseStack, buffers, shadows, nodeLight, level);
                continue;
            }
            definition.control().flatMap(control -> control.base()).ifPresent(base -> {
                poseStack.pushPose();
                multiply(poseStack, node.restMatrix());
                renderVisual(base, node.tint(), poseStack, buffers, nodeLight, level);
                poseStack.popPose();
            });
            poseStack.pushPose();
            multiply(poseStack, node.matrix());
            // Only for an empty hand: while something is held, the things it passes over are not what the player uses.
            boolean pointedAt = local && ui.hoverOutline() && input.isHandFree() && node.isHovered() && !node.isCarried()
                    && !node.isOutlineByCode() && isUsable(definition, station);
            if (pointedAt || local && ui.hoverOutline() && (input.pourTarget() == node || node.isOutlined())) {
                outlineColor = ui.outlineColor();
                PoseStack at = snapshot(poseStack);
                List<Consumer<SceneBuffers>> pending = node.isOutlineSeenOnly() ? PENDING_SEEN_OUTLINES : PENDING_OUTLINES;
                node.visual().ifPresent(visual -> pending.add(target -> renderVisual(visual, 0xFFFFFFFF, at, target,
                        LightCoordsUtil.FULL_BRIGHT, level)));
                definition.slot().ifPresent(slot -> pending.add(target -> renderSlotItem(station.state().getItem(slot.slot()),
                        slot.display(), slot.shownItemSize(), slot.slot().index(), station.age(), at, target, LightCoordsUtil.FULL_BRIGHT,
                        level)));
                definition.output().ifPresent(output -> pending.add(target -> renderSlotItem(
                        station.state().getOutput(output.output()), output.display(), output.size(), 0, station.age(), at, target,
                        LightCoordsUtil.FULL_BRIGHT, level)));
            }
            boolean overAll = local && node.isOnTop() && SmoothPass.defer(overEverything(node, level), poseStack, nodeLight);
            if (!overAll) {
                node.visual().ifPresent(visual -> renderVisual(visual, node.tint(), poseStack, buffers, nodeLight, level));
                if (node.shape() != null) {
                    renderShape(node, poseStack, buffers, nodeLight);
                }
            }
            // Over the frame only at the station worked at: another station's painter would show through whatever stands before it.
            if (node.painter() != null && !(local && node.painter().smooth() && SmoothPass.defer(node.painter(), poseStack, nodeLight))) {
                node.painter().paint(poseStack, buffers, nodeLight);
            }
            if (!overAll) {
                definition.tank().ifPresent(binding -> renderTank(node, binding, poseStack, buffers, nodeLight, distance));
            }
            definition.slot().ifPresent(slot -> {
                boolean marked = local && (node.isHovered() || input.isSpreadTarget(new Target.Station(slot.slot())));
                float strength = marked ? node.isHovered() ? 1.0F : 0.66F : local && station.isInUse() ? slot.idleHighlight() : 0.0F;
                if (strength > 0.0F && slot.highlight()) {
                    if (slot.display() == SlotBinding.ItemDisplay.UPRIGHT) {
                        // An item standing up is marked in its own plane, behind it, not on the ground at its foot.
                        renderStandingHighlight(poseStack, buffers, slot.size() + 0.2F, strength);
                    } else {
                        renderHighlight(poseStack, buffers, slot.size() + 0.2F, strength);
                    }
                }
                ItemStack stack = station.state().getItem(slot.slot());
                ItemStack spread = local ? input.spreadPreview(new Target.Station(slot.slot())) : ItemStack.EMPTY;
                if (!spread.isEmpty()) {
                    // What the drag in progress will leave here, as a vanilla screen shows it.
                    stack = spread;
                }
                renderSlotItem(stack, slot.display(), slot.shownItemSize(), slot.slot().index(), station.age(), poseStack, buffers, light,
                        level);
                if (stack.isEmpty() && local && station.isInUse()) {
                    renderGhost(node, slot, station.age(), poseStack, buffers, light, level);
                }
                boolean counted = slot.counts() == SlotBinding.Counts.ALWAYS || slot.counts() == SlotBinding.Counts.HOVER && local && node.isHovered();
                if (slot.display() != SlotBinding.ItemDisplay.HIDDEN && counted) {
                    renderCount(poseStack, buffers, stack, slot.size(), slot.countFacing());
                }
            });
            definition.output().ifPresent(output -> {
                ItemStack stack = station.state().getOutput(output.output());
                renderSlotItem(stack, output.display(), output.size(), 0, station.age(), poseStack, buffers,
                        local && node.isHovered() ? LightCoordsUtil.FULL_BRIGHT : light, level);
                renderCount(poseStack, buffers, stack, output.size(), output.countFacing());
            });
            poseStack.popPose();
            if (node.isCarried() && node.visual().isPresent()) {
                // The carried object's own silhouette falls onto the work surface.
                projectShadow(poseStack, workPlane, node.carryPosition().y() - workPlane, () -> {
                    multiply(poseStack, node.matrix());
                    renderVisual(node.visual().get(), 0xFFFFFFFF, poseStack, shadows, light, level);
                });
            }
        }

        if (local && scene.inventoryLayout() != null && input.inventoryReveal() > 0.0F) {
            renderInventory(scene, input, poseStack, buffers, light, level);
        }
        if (carried != null && carried.inWorld() && carried.preview() != null) {
            renderPreview(carried, scene, scene.definition().placing().orElseThrow(), poseStack, buffers, shadows, light, level);
        } else if (carried != null && carried.inWorld() && !station.isCarriedHidden()) {
            renderCarried(carried.stack(), carried.stationPos(), carried.lean(), carried.size(), poseStack, buffers, shadows, workPlane,
                    light, level);
            poseStack.pushPose();
            Vector3fc at = carried.stationPos();
            poseStack.translate(at.x(), at.y() + 0.4F, at.z());
            int left = input.spreadLeft();
            // A drag over several slots: what stays in the hand once it is let go.
            int count = left >= 0 ? left : carried.stack().getCount();
            if (left >= 0 || count > 1) {
                SlotBinding.CountFacing facing = scene.definition().ui().carryCountFacing();
                renderCount(poseStack, buffers, String.valueOf(count), carried.size() * 1.1F, facing != SlotBinding.CountFacing.UP, -1.0F,
                        facing == SlotBinding.CountFacing.CAMERA);
            }
            poseStack.popPose();
        }
        for (ClientStation.RemoteHolding holding : station.remoteHoldings()) {
            if (!holding.item().isEmpty()) {
                renderCarried(holding.item(), holding.position(), holding.rotation(), 4.5F, poseStack, buffers, shadows, workPlane, light,
                        level);
            }
        }
        drawnLights = null;
        renderLightPools(scene, poseStack, buffers, workPlane);
        if (local) {
            renderPourHints(scene, input, poseStack, buffers);
        }
        if ((!station.particles().isEmpty() || !station.pourStreams().isEmpty()) && debugCamera != null) {
            Matrix4f toStation = new Matrix4f(station.blockToWorld(new Matrix4d()).setTranslation(0.0, 0.0, 0.0))
                    .mul(station.stationToWorld(new Matrix4f())).invert();
            Vector3f right = toStation.transformDirection(new Vector3f(debugCamera.leftVector()).negate()).normalize();
            Vector3f up = toStation.transformDirection(new Vector3f(debugCamera.upVector())).normalize();
            Vector3f view = toStation.transformDirection(new Vector3f(debugCamera.forwardVector())).normalize();
            if (scene.definition().pouring().stream()) {
                float width = scene.definition().pouring().streamWidth();
                boolean deferred = local && station.pourStreams().anyOverAll() && SmoothPass.defer(new NodePainter() {
                    @Override
                    public void paint(PoseStack pose, SceneBuffers target, int packedLight) {
                        station.pourStreams().render(pose, target, view, packedLight, width, true);
                    }

                    @Override
                    public boolean smooth() {
                        return true;
                    }
                }, poseStack, light);
                station.pourStreams().render(poseStack, buffers, view, light, width, false);
                if (!deferred) {
                    station.pourStreams().render(poseStack, buffers, view, light, width, true);
                }
            }
            station.particles().render(poseStack, buffers, right, up, view, light);
        }
        if (DebugView.isOn()) {
            DebugView.render(station, poseStack, buffers, debugCamera, debugPose, camera);
        }
        poseStack.popPose();
    }

    /** An item standing freely: as it is, with an outline under the cursor and its shape falling on what it stands on. */
    private static void renderPlaced(NodeInstance node, boolean outline, PoseStack poseStack, SceneBuffers buffers,
            SceneBuffers shadows, int light, ClientLevel level) {
        PlacedItem item = node.placed();
        ItemStack stack = item.stack();
        PlacedShape shape = item.shape();
        ItemPose pose = item.pose();
        int seed = item.slot().index();
        poseStack.pushPose();
        multiply(poseStack, node.matrix());
        if (outline && node.isHovered()) {
            outlineColor = ui.outlineColor();
            PoseStack at = snapshot(poseStack);
            PENDING_OUTLINES.add(target -> shape.draw(stack, at, target, LightCoordsUtil.FULL_BRIGHT, level, seed));
        }
        shape.draw(stack, poseStack, buffers, light, level, seed);
        poseStack.popPose();
        SlotBinding.Counts counts = item.options().counts();
        if (pose != null && stack.getCount() > 1
                && (counts == SlotBinding.Counts.ALWAYS || counts == SlotBinding.Counts.HOVER && node.isHovered())) {
            // At the foot of the stack, facing the front, wherever it stands and however it is turned.
            Vector3f at = node.matrix().getTranslation(new Vector3f());
            poseStack.pushPose();
            poseStack.translate(at.x(), at.y() - pose.lift(), at.z());
            // Just past the item's right side, where neither its model nor the outline round it covers the number.
            float edge = shape.extents(pose.rotation(), item.options().itemSize() * pose.scale()).x + COUNT_GAP;
            renderCount(poseStack, buffers, String.valueOf(stack.getCount()), Math.max(1.5F, pose.scale() * item.options().itemSize()), true, edge);
            poseStack.popPose();
        }
        if (item.options().shadows() && shape.castsShadow() && pose != null && pose.on() < 0) {
            projectShadow(poseStack, pose.y(), 0.0F, () -> {
                multiply(poseStack, node.matrix());
                shape.draw(stack, poseStack, shadows, light, level, seed);
            });
        }
    }

    /** The stack in the hand, see-through, where it would stand; the place comes from the cursor, the look from the turn it has. */
    private static void renderPreview(FocusInput.Carried carried, SceneInstance scene, PlacingOptions options, PoseStack poseStack,
            SceneBuffers buffers, SceneBuffers shadows, int light, ClientLevel level) {
        ItemPose pose = carried.preview();
        ItemStack stack = carried.stack();
        PlacedShape shape = PlacedShape.of(stack, level, options.blockModels(), options.framePose());
        Vector3fc at = carried.stationPos();
        Matrix4f move = scene.surfaceMoveOf(options, pose);
        if (move != null) {
            // On a moved surface (a door's shelf) the preview is drawn as the surface sees it, with the surface's move.
            poseStack.pushPose();
            multiply(poseStack, move);
            at = new Matrix4f(move).invert().transformPosition(new Vector3f(at));
        }
        Matrix4f matrix = new Matrix4f().translation(at.x(), at.y(), at.z()).rotate(new org.joml.Quaternionf(pose.rotation()))
                .scale(shape.modelScale(pose.scale() * options.itemSize()));
        SceneBuffers ghost = type -> {
            RenderType clear = ghostType(type);
            if (clear == null) {
                return buffers.getBuffer(type);
            }
            if (clear != type) {
                KeptTypes.keep(buffers, type);
            }
            return new TintedConsumer(buffers.getBuffer(clear), options.previewColor());
        };
        poseStack.pushPose();
        multiply(poseStack, matrix);
        shape.draw(stack, poseStack, ghost, light, level, 0);
        poseStack.popPose();
        ItemPose landing = carried.landing();
        if (options.shadows() && shape.castsShadow() && landing != null) {
            // The mark: the shape as it lands, under the grip, not under the swinging body; on the shelf or on the top of what
            // it will stand on. Darker the nearer it is to landing.
            float half = shape.extents(landing.rotation(), landing.scale() * options.itemSize()).y;
            float hover = options.physics().map(PlacingPhysics::hover).orElse(0.0F);
            float gap = Math.max(0.0F, landing.lift() - half);
            float strength = hover <= 0.0F ? 1.0F : 1.0F - LANDING_FADE * Math.min(1.0F, gap / hover);
            Matrix4f landed = new Matrix4f().translation(landing.x(), landing.y() + half, landing.z())
                    .rotate(new org.joml.Quaternionf(landing.rotation())).scale(shape.modelScale(landing.scale() * options.itemSize()));
            projectShadow(poseStack, landing.y(), 0.0F, strength, () -> {
                multiply(poseStack, landed);
                shape.draw(stack, poseStack, shadows, light, level, 0);
            });
        } else if (options.shadows() && shape.castsShadow() && pose.on() < 0) {
            projectShadow(poseStack, pose.y(), 0.0F, () -> {
                multiply(poseStack, matrix);
                shape.draw(stack, poseStack, shadows, light, level, 0);
            });
        }
        if (move != null) {
            poseStack.popPose();
        }
    }

    /**
     * The world block a model lit as a block stands in: the one holding the point it stands on (its anchor, a hair above), so that
     * a multiblock's scene drawn a model a block takes each its own block's light and corner shadows, and a lid set beside the
     * station the light where it stands. A model standing in the station's block, as most do, is lit by that block.
     *
     * <p>An anchor often lies on a side of a block: a furnace door's hinge on the furnace's front, a cupboard door's on the edge
     * beside a wall. Rounded with the camera's position, such a point fell now in one block, now in the next, and where one was
     * solid (no light inside) the model went black and back as the camera moved. Every block within a hair of the point is
     * taken instead, the same way each frame: the station's own block if it holds light, else the first that does.
     */
    private static BlockPos standsIn(PoseStack poseStack, Vector3fc anchor) {
        ClientLevel level = Minecraft.getInstance().level;
        if (drawnCamera == null || level == null) {
            return drawnPos;
        }
        Vector3f point = new Matrix4f(TO_WORLD).mul(poseStack.last().pose())
                .transformPosition(new Vector3f(anchor.x() / 16.0F, anchor.y() / 16.0F + 0.01F, anchor.z() / 16.0F));
        double x = drawnCamera.x + point.x;
        double y = drawnCamera.y + point.y;
        double z = drawnCamera.z + point.z;
        BlockPos chosen = null;
        BlockPos firstSolid = null;
        for (int bx = Mth.floor(x - ON_SIDE); bx <= Mth.floor(x + ON_SIDE); bx++) {
            for (int by = Mth.floor(y - ON_SIDE); by <= Mth.floor(y + ON_SIDE); by++) {
                for (int bz = Mth.floor(z - ON_SIDE); bz <= Mth.floor(z + ON_SIDE); bz++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (level.getBlockState(pos).isSolidRender()) {
                        if (firstSolid == null || pos.equals(drawnPos)) {
                            firstSolid = pos;
                        }
                    } else if (chosen == null || pos.equals(drawnPos)) {
                        chosen = pos;
                    }
                }
            }
        }
        return chosen != null ? chosen : firstSolid;
    }

    /** How near (blocks) a side of a block a point counts as on it, for {@link #standsIn}: far past any rounding, too little to see. */
    private static final double ON_SIDE = 0.005;

    /** The see-through type a preview draws {@code type} as, on the same atlas; null for a type the preview leaves as it is. */
    private static RenderType ghostType(RenderType type) {
        if (type == DiegesisRenderTypes.cutoutBlockSheet() || type == DiegesisRenderTypes.translucentCullBlockSheet()
                || type == DiegesisRenderTypes.solidBlockSheet()) {
            return DiegesisRenderTypes.translucentCullBlockSheet();
        }
        if (type == Sheets.cutoutBlockItemSheet() || type == Sheets.translucentBlockItemSheet()) {
            return Sheets.translucentBlockItemSheet();
        }
        if (type == Sheets.cutoutItemSheet() || type == Sheets.translucentItemSheet()) {
            return Sheets.translucentItemSheet();
        }
        return null;
    }

    private static void renderVisual(Visual visual, int tint, PoseStack poseStack, SceneBuffers buffers, int light, ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        int packedLight = visual.emissive() ? LightCoordsUtil.FULL_BRIGHT : light;
        Vector3fc anchor = visual.anchor();
        if ((tint >>> 24) == 0) {
            return;
        }
        poseStack.pushPose();
        if (visual.item().isPresent()) {
            // The item renderer takes no tint: a tinted item (one offered see-through) is drawn through tinting buffers.
            poseStack.translate(8.0F - anchor.x(), 8.0F - anchor.y(), 8.0F - anchor.z());
            poseStack.scale(16.0F, 16.0F, 16.0F);
            SceneBuffers drawn = tint == Visual.WHITE ? solidItems(buffers)
                    : com.pockyl.diegesis.api.client.DiegesisClient.tinted((tint >>> 24) == 0xFF ? solidItems(buffers) : buffers, tint);
            SceneDraw.item(new ItemStack(visual.item().get()), ItemDisplayContext.FIXED, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, drawn, level, 0);
            poseStack.popPose();
            return;
        }
        if (visual.mesh().isPresent()) {
            renderMesh(visual, tint, poseStack, buffers, packedLight);
            poseStack.popPose();
            return;
        }
        BlockStateModel model;
        BlockState state = visual.block().orElse(null);
        boolean translucent = visual.translucent() || (tint >>> 24) < 0xFF;
        if (state != null) {
            model = SceneDraw.blockModel(state);
            translucent |= SceneDraw.isTranslucent(model);
        } else {
            Identifier id = visual.model().orElseThrow();
            model = ClientPlatform.INSTANCE.getModel(id);
            if (model == SceneDraw.missingModel() && WARNED_MODELS.add(id)) {
                DiegesisMod.LOGGER.warn("Scene model {} is not loaded: put it under models/diegesis/ or register it with"
                        + " DiegesisClient.registerModel", id);
            }
        }
        poseStack.translate(-anchor.x(), -anchor.y(), -anchor.z());
        poseStack.scale(16.0F, 16.0F, 16.0F);
        if (visual.litAsBlock() && !translucent && drawnPos != null && level != null) {
            // The way the chunks draw the block itself, from a chunk render type, so that the copy looks the same.
            VertexConsumer lit = buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.cutoutMovingBlock());
            if (drawnLights != null) {
                // The scene's own lights (a lantern on a shelf) brighten the walls and boards around them.
                lit = new SceneLitConsumer(lit, TO_STATION, NORMAL_TO_STATION, drawnLights, drawnSurfaces);
            }
            BlockLit.draw(model, state != null ? state : level.getBlockState(drawnPos), standsIn(poseStack, anchor), poseStack,
                    tint != Visual.WHITE ? new TintedConsumer(lit, tint) : lit, TO_WORLD, drawnCamera);
            poseStack.popPose();
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(translucent ? DiegesisRenderTypes.translucentCullBlockSheet() : DiegesisRenderTypes.cutoutBlockSheet());
        if (tint != Visual.WHITE) {
            consumer = new TintedConsumer(consumer, tint);
        }
        if (visual.glass() > 0.0F) {
            consumer = new GlassConsumer(consumer, visual.glass());
        }
        SceneDraw.model(poseStack.last(), consumer, model, packedLight, OverlayTexture.NO_OVERLAY);
        if (visual.doubleSided()) {
            // The same quads turned round, in the same render type: an open glass shows its far walls from inside.
            SceneDraw.model(poseStack.last(), new BackFaceConsumer(consumer), model, packedLight, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    /** A shape made by code: like a mesh, its texture from the node. */
    /** The liquid in a node's tank, and what floats on it; the pose stack is at the node. */
    private static void renderTank(NodeInstance node, TankBinding binding, PoseStack poseStack, SceneBuffers buffers, int light,
            float distance) {
        // The world's up (tipped by sloshing) as the node sees it: the liquid's surface stays level.
        Vector3fc slosh = node.liquid().slosh();
        Matrix4f toNode = node.matrix().invert(new Matrix4f());
        Vector3f up = toNode.transformDirection(new Vector3f(slosh.x(), 1.0F, slosh.z()).normalize()).normalize();
        Vector3f sun = toNode.transformDirection(new Vector3f(LIGHT_DIRECTION)).normalize();
        LiquidRenderer.render(binding, node.liquid(), up, sun, poseStack, buffers, light, distance);
        if (node.floating() != null) {
            node.floating().render(binding.inner(), node.liquid(), poseStack, buffers, light, distance);
        }
    }

    /**
     * A node's own look drawn as a smooth painter: over the finished frame, where nothing of the scene or the world covers it. Its
     * tank's liquid comes along, inside its walls: a mug held low before a keg's tap is not cut by the floor.
     */
    private static NodePainter overEverything(NodeInstance node, ClientLevel level) {
        return new NodePainter() {
            @Override
            public void paint(PoseStack poseStack, SceneBuffers buffers, int light) {
                node.visual().ifPresent(visual -> renderVisual(visual, node.tint(), poseStack, buffers, light, level));
                if (node.shape() != null) {
                    renderShape(node, poseStack, buffers, light);
                }
                // Only the station worked at draws over everything: always near, in full.
                node.definition().tank().ifPresent(binding -> renderTank(node, binding, poseStack, buffers, light, 0.0F));
            }

            @Override
            public boolean smooth() {
                return true;
            }
        };
    }

    private static void renderShape(NodeInstance node, PoseStack poseStack, SceneBuffers buffers, int light) {
        var sprite = SceneDraw.sprite(node.shapeTexture());
        VertexConsumer consumer = buffers.getBuffer(SceneDraw.sheet(sprite, (node.tint() >>> 24) < 0xFF));
        if (node.tint() != Visual.WHITE) {
            consumer = new TintedConsumer(consumer, node.tint());
        }
        node.shape().mesh().draw(poseStack.last(), consumer, sprite, node.shapeGlows() ? LightCoordsUtil.FULL_BRIGHT : light);
    }

    /** A polygon model: the same render types and wrappers as a block model, its corners written directly. */
    private static void renderMesh(Visual visual, int tint, PoseStack poseStack, SceneBuffers buffers, int light) {
        Identifier id = visual.mesh().orElseThrow();
        com.pockyl.diegesis.impl.client.mesh.Mesh mesh = com.pockyl.diegesis.impl.client.mesh.MeshLibrary.INSTANCE.mesh(id);
        if (mesh == null) {
            if (WARNED_MODELS.add(id)) {
                DiegesisMod.LOGGER.warn("Scene mesh {} is not loaded: put it at assets/{}/diegesis/meshes/{}.obj", id, id.getNamespace(), id.getPath());
            }
            return;
        }
        Vector3fc anchor = visual.anchor();
        poseStack.translate(-anchor.x(), -anchor.y(), -anchor.z());
        boolean translucent = visual.translucent() || (tint >>> 24) < 0xFF;
        var sprite = SceneDraw.sprite(visual.texture().orElseThrow());
        VertexConsumer consumer = buffers.getBuffer(SceneDraw.sheet(sprite, translucent));
        if (tint != Visual.WHITE) {
            consumer = new TintedConsumer(consumer, tint);
        }
        if (visual.glass() > 0.0F) {
            consumer = new GlassConsumer(consumer, visual.glass());
        }
        mesh.draw(poseStack.last(), consumer, sprite, light);
        if (visual.doubleSided()) {
            mesh.draw(poseStack.last(), new BackFaceConsumer(consumer), sprite, light);
        }
    }

    /**
     * While a vessel with liquid is in the hand, a soft mark on the opening of every tank that would take it, breathing
     * slowly; the one it points at is marked solidly.
     */
    private static void renderPourHints(SceneInstance scene, FocusInput input, PoseStack poseStack, SceneBuffers buffers) {
        PourOptions options = scene.definition().pouring();
        List<NodeInstance> candidates = input.pourCandidates();
        if (!options.hints() || candidates.isEmpty()) {
            return;
        }
        float breath = 0.75F + 0.25F * (float) Math.sin(Util.getMillis() / 1000.0 * 3.0);
        int alpha = options.hintColor() >>> 24;
        for (NodeInstance node : candidates) {
            TankBinding tank = node.definition().tank().get();
            float scale = node.matrix().getScale(new Vector3f()).x;
            float size = Math.min(tank.inner().to().x() - tank.inner().from().x(), tank.inner().to().z() - tank.inner().from().z()) * scale;
            Vector3f mouth = node.matrix().transformPosition(tank.mouth());
            float strength = node == input.pourTarget() ? 1.6F : breath;
            poseStack.pushPose();
            poseStack.translate(mouth.x, mouth.y, mouth.z);
            renderSquare(poseStack, buffers, size, Math.min(255, Math.round(alpha * strength)) << 24 | options.hintColor() & 0xFFFFFF);
            poseStack.popPose();
        }
    }

    /**
     * The player's inventory laid out in the scene: a board, a sunken well per slot, the items lying in them with their
     * counts, and the vanilla highlight on the slot under the cursor.
     */
    private static void renderInventory(SceneInstance scene, FocusInput input, PoseStack poseStack, SceneBuffers buffers,
            int light, ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        InventoryLayout layout = scene.inventoryLayout();
        layout.surface().ifPresent(surface -> {
            poseStack.pushPose();
            multiply(poseStack, layout.matrix());
            float width = layout.boardWidth();
            float depth = layout.boardDepth();
            poseStack.translate(-width / 2.0F, -BOARD_THICKNESS, -depth / 2.0F);
            poseStack.scale(width, BOARD_THICKNESS, depth);
            SceneDraw.model(poseStack.last(), buffers.getBuffer(DiegesisRenderTypes.cutoutBlockSheet()), SceneDraw.blockModel(surface), light,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        });
        float well = layout.spacing() * 0.86F;
        float reveal = input.inventoryReveal();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            // Coming up row by row from the hotbar, each slot growing out of its centre.
            int row = slot < 9 ? 0 : 4 - slot / 9;
            float grow = Mth.clamp(reveal * 1.6F - row * 0.2F, 0.0F, 1.0F);
            if (grow <= 0.0F) {
                continue;
            }
            grow = 1.0F - (1.0F - grow) * (1.0F - grow);
            poseStack.pushPose();
            multiply(poseStack, scene.inventorySlot(slot));
            if (grow < 1.0F) {
                poseStack.scale(grow, grow, grow);
            }
            if ((layout.slotColor() >>> 24) > 0) {
                renderSquare(poseStack, buffers, well, layout.slotColor());
            }
            if (slot == input.hoveredInventorySlot() || input.isSpreadTarget(new Target.Inventory(slot))) {
                renderHighlight(poseStack, buffers, well, slot == input.hoveredInventorySlot() ? 1.0F : 0.66F);
            }
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            renderSlotItem(stack, SlotBinding.ItemDisplay.FLAT, layout.size(), slot, 0.0F, poseStack, buffers, light, level);
            renderCount(poseStack, buffers, stack, layout.spacing());
            poseStack.popPose();
        }
    }

    /**
     * A stack's count lying on the surface in the slot's near right corner (as the player sees it), like the number in
     * a vanilla slot. Nothing for single items.
     */
    private static void renderCount(PoseStack poseStack, SceneBuffers buffers, ItemStack stack, float slotSize) {
        renderCount(poseStack, buffers, stack, slotSize, false);
    }

    private static void renderCount(PoseStack poseStack, SceneBuffers buffers, ItemStack stack, float slotSize, boolean standing) {
        if (stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }
        renderCount(poseStack, buffers, String.valueOf(stack.getCount()), slotSize, standing);
    }

    /** A count facing as a slot or output asks: lying, standing at the front, or turned to the camera. */
    private static void renderCount(PoseStack poseStack, SceneBuffers buffers, ItemStack stack, float slotSize,
            SlotBinding.CountFacing facing) {
        if (stack.isEmpty() || stack.getCount() <= 1) {
            return;
        }
        renderCount(poseStack, buffers, String.valueOf(stack.getCount()), slotSize, facing != SlotBinding.CountFacing.UP, -1.0F,
                facing == SlotBinding.CountFacing.CAMERA);
    }

    private static void renderCount(PoseStack poseStack, SceneBuffers buffers, String text, float slotSize) {
        renderCount(poseStack, buffers, text, slotSize, false);
    }

    /** A count at the slot's near right corner: lying on the slot, or {@code standing} at the item's foot, facing the front. */
    private static void renderCount(PoseStack poseStack, SceneBuffers buffers, String text, float slotSize, boolean standing) {
        renderCount(poseStack, buffers, text, slotSize, standing, -1.0F);
    }

    /**
     * @param outside with {@code standing}, pixels to the player's right of the middle where the number begins: it stands beside the
     *                item, not over it; negative ends it at the slot's right side
     */
    private static void renderCount(PoseStack poseStack, SceneBuffers buffers, String text, float slotSize, boolean standing, float outside) {
        renderCount(poseStack, buffers, text, slotSize, standing, outside, false);
    }

    /** @param camera standing at the item's foot as {@code standing} puts it, but turned to the camera */
    private static void renderCount(PoseStack poseStack, SceneBuffers buffers, String text, float slotSize, boolean standing, float outside,
            boolean camera) {
        if (!ui.showCounts()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        float scale = slotSize * 0.42F / font.lineHeight;
        poseStack.pushPose();
        Matrix4f flat;
        if (standing) {
            // The font's x runs to -x (the player's right), its y (down the page) down, its z towards the front.
            float end = outside < 0.0F ? slotSize / 2.0F : outside + font.width(text) * scale;
            poseStack.translate(-end, 0.12F, -0.35F);
            flat = new Matrix4f(-scale, 0.0F, 0.0F, 0.0F, 0.0F, -scale, 0.0F, 0.0F, 0.0F, 0.0F, -scale, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        } else {
            // The player's right is -x and the near side -z: the font's x runs to -x, its y (down the page) to -z.
            poseStack.translate(-slotSize / 2.0F, 0.12F, -slotSize / 2.0F);
            flat = new Matrix4f(-scale, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -scale, 0.0F, 0.0F, scale, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        }
        Matrix4f matrix = new Matrix4f(poseStack.last().pose()).mul(flat);
        if (camera) {
            // The level's pose is in the world's axes, around the camera: keep where the number stands, drop every turn of the
            // scene and turn it as the camera looks (as a name tag), so the font's x runs to the camera's right and its y down.
            Matrix4f pose = poseStack.last().pose();
            Vector3f at = pose.getTranslation(new Vector3f());
            // The scene's own unit (a pixel of the block) in the camera's: the number keeps the size it has lying or standing.
            float unit = new Vector3f(pose.m00(), pose.m01(), pose.m02()).length();
            // Brought towards the camera by more than half the item, so that the item, turning or floating, never covers it.
            float distance = at.length();
            if (distance > 1.0E-4F) {
                at.mul(Math.max(0.0F, distance - slotSize * 0.75F * unit) / distance);
            }
            matrix = new Matrix4f().translation(at).rotate(Minecraft.getInstance().gameRenderer.mainCamera().rotation())
                    .scale(scale * unit, -scale * unit, scale * unit);
        }
        float x = -font.width(text) + 0.5F;
        float y = -font.lineHeight + 1.5F;
        Matrix4f shadow = new Matrix4f(matrix).translate(1.0F, 1.0F, 0.0F);
        // Lifted a hair above its shadow (the font's z points up here).
        Matrix4f front = new Matrix4f(matrix).translate(0.0F, 0.0F, 0.4F);
        poseStack.popPose();
        if (ui.countsOnTop() || camera) {
            // Drawn after everything else, without the depth test: an item's model can never cover its number. A number turned
            // to the camera always is: it stands beside a floating, turning result, which cut into it whatever its distance.
            PENDING_COUNTS.add(new Count(text, x, y, shadow, front));
            return;
        }
        SceneDraw.text(buffers, text, x, y, 0xFF3F3F3F, shadow, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT);
        SceneDraw.text(buffers, text, x, y, 0xFFFFFFFF, front, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT);
    }

    /** A count waiting to be drawn on top: its text, position in font units and matrices. */
    private record Count(String text, float x, float y, Matrix4f shadow, Matrix4f front) {
    }

    /** An item in a slot: lying, standing or floating, with a small per-slot turn so a full grid looks placed by hand. */
    private static void renderSlotItem(ItemStack stack, SlotBinding.ItemDisplay display, float size, int index, float age,
            PoseStack poseStack, SceneBuffers buffers, int light, ClientLevel level) {
        renderSlotItem(stack, display, size, index, age, poseStack, buffers, light, level, true);
    }

    /** {@code solid}: through {@link #solidItems}, as every real item; a see-through ghost keeps the buffers it is given. */
    private static void renderSlotItem(ItemStack stack, SlotBinding.ItemDisplay display, float size, int index, float age,
            PoseStack poseStack, SceneBuffers buffers, int light, ClientLevel level, boolean solid) {
        if (stack.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean block = isBlockLike(stack, level);
        float jitter = ((index * 7919) % 25 - 12) * 0.7F;
        poseStack.pushPose();
        switch (display) {
            case FLAT -> {
                if (block) {
                    poseStack.translate(0.0F, size * 0.25F, 0.0F);
                    poseStack.rotate(Axis.YP.rotationDegrees(180.0F + jitter));
                } else {
                    poseStack.translate(0.0F, 0.07F, 0.0F);
                    poseStack.rotate(Axis.YP.rotationDegrees(180.0F + jitter));
                    poseStack.rotate(Axis.XP.rotationDegrees(-90.0F));
                }
            }
            case UPRIGHT -> {
                poseStack.translate(0.0F, block ? size * 0.25F : size * 0.5F, 0.0F);
                poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
            }
            case FLOATING -> {
                poseStack.translate(0.0F, size * 0.55F + (float) Math.sin(age * 2.2F) * 0.35F, 0.0F);
                poseStack.rotate(Axis.YP.rotationDegrees(age * 40.0F));
            }
            case HIDDEN -> {
                poseStack.popPose();
                return;
            }
        }
        poseStack.scale(size, size, size);
        SceneDraw.item(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack,
                solid ? solidItems(buffers) : buffers, level, index);
        poseStack.popPose();
    }

    /**
     * What goes into an empty slot, see-through and still: lying (or standing) as a real item would, without a hidden slot's own
     * look, so the player sees where each thing is put.
     */
    private static void renderGhost(NodeInstance node, SlotBinding slot, float age, PoseStack poseStack, SceneBuffers buffers,
            int light, ClientLevel level) {
        if (slot.ghostAlpha() <= 0.0F) {
            return;
        }
        ItemStack ghost = SlotGhosts.shown(node, slot, age);
        if (ghost.isEmpty()) {
            return;
        }
        SlotBinding.ItemDisplay display = slot.display() == SlotBinding.ItemDisplay.UPRIGHT ? SlotBinding.ItemDisplay.UPRIGHT
                : SlotBinding.ItemDisplay.FLAT;
        int argb = Math.round(255.0F * slot.ghostAlpha()) << 24 | 0xFFFFFF;
        // Faded, but with depth as a real item: in the see-through pass (no depth) a beacon's beam drawn later went over it.
        renderSlotItem(ghost, display, slot.shownItemSize(), slot.slot().index(), age, poseStack,
                com.pockyl.diegesis.api.client.DiegesisClient.tinted(solidItems(buffers), argb), light, level, false);
    }

    /** A stack on the cursor (or another player's), leaning as it moves, with its silhouette as a shadow below. */
    private static void renderCarried(ItemStack stack, Vector3fc at, Vector3fc lean, float size, PoseStack poseStack,
            SceneBuffers buffers, SceneBuffers shadows, float workPlane, int light, ClientLevel level) {
        boolean block = isBlockLike(stack, level);
        Runnable place = () -> {
            poseStack.translate(at.x(), at.y(), at.z());
            poseStack.rotate(Axis.XP.rotationDegrees(lean.x()));
            poseStack.rotate(Axis.ZP.rotationDegrees(lean.z()));
            poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
            if (!block) {
                // Tipped towards the camera, so it reads as held above the table rather than lying on it.
                poseStack.rotate(Axis.XP.rotationDegrees(-62.0F));
            }
            float scale = size * 1.08F;
            poseStack.scale(scale, scale, scale);
        };
        poseStack.pushPose();
        place.run();
        SceneDraw.item(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                poseStack, solidItems(buffers), level, 0);
        poseStack.popPose();
        ItemStack plain = new ItemStack(stack.getItem());
        projectShadow(poseStack, workPlane, at.y() - workPlane, () -> {
            place.run();
            SceneDraw.item(plain, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack, shadows, level, 0);
        });
    }

    /**
     * Draws {@code draw} flattened onto the work plane along the light, as a shadow: geometry at height h above the
     * plane lands h * shear to the side. {@code height} is how high the thing is held; the shadow fades with it.
     */
    private static void projectShadow(PoseStack poseStack, float planeY, float height, Runnable draw) {
        projectShadow(poseStack, planeY, height, 1.0F, draw);
    }

    /** As {@link #projectShadow(PoseStack, float, float, Runnable)}, at {@code strength} (0 to 1) of the scene's opacity. */
    private static void projectShadow(PoseStack poseStack, float planeY, float height, float strength, Runnable draw) {
        if (!ui.shadows()) {
            return;
        }
        float alpha = Math.max(0.0F, strength * ui.shadowOpacity() * (1.0F - Math.max(height, 0.0F) * 0.05F));
        if (alpha < 0.02F) {
            return;
        }
        ShadowBuffers.alpha = alpha;
        // Where the light comes from in the drawn faces' space: the pose's normals, before the flattening (which they never take).
        poseStack.last().normal().transform(ShadowBuffers.light.set(LIGHT_DIRECTION)).normalize();
        poseStack.pushPose();
        Matrix4f flatten = new Matrix4f().translate(0.0F, planeY + 0.06F, 0.0F);
        Matrix4f shear = new Matrix4f().m10(SHADOW_SHEAR_X).m11(0.0004F).m12(SHADOW_SHEAR_Z);
        flatten.mul(shear).translate(0.0F, -planeY, 0.0F);
        poseStack.last().pose().mul(flatten);
        draw.run();
        poseStack.popPose();
    }

    /** The highlight on a slot the cursor is on (strength 1) or a drag spreads over (weaker), like vanilla's. */
    private static void renderHighlight(PoseStack poseStack, SceneBuffers buffers, float size, float strength) {
        int color = ui.highlightColor();
        int alpha = Math.round((color >>> 24) * strength);
        renderSquare(poseStack, buffers, size, alpha << 24 | color & 0xFFFFFF);
    }

    /** The highlight of a slot whose item stands up: a square in the item's plane, from its foot up, just behind it. */
    private static void renderStandingHighlight(PoseStack poseStack, SceneBuffers buffers, float size, float strength) {
        int color = ui.highlightColor();
        int argb = Math.round((color >>> 24) * strength) << 24 | color & 0xFFFFFF;
        TextureAtlasSprite sprite = SceneDraw.blockSprite(HIGHLIGHT_SPRITE);
        VertexConsumer consumer = buffers.getBuffer(OVERLAY);
        PoseStack.Pose pose = poseStack.last();
        float h = size / 2.0F;
        float z = size * 0.08F;
        // Both faces: a behaviour may turn the node so that the camera sees it from behind.
        standingVertex(consumer, pose, -h, 0.0F, z, sprite.getU0(), sprite.getV1(), argb);
        standingVertex(consumer, pose, -h, size, z, sprite.getU0(), sprite.getV0(), argb);
        standingVertex(consumer, pose, h, size, z, sprite.getU1(), sprite.getV0(), argb);
        standingVertex(consumer, pose, h, 0.0F, z, sprite.getU1(), sprite.getV1(), argb);
        standingVertex(consumer, pose, h, 0.0F, z, sprite.getU1(), sprite.getV1(), argb);
        standingVertex(consumer, pose, h, size, z, sprite.getU1(), sprite.getV0(), argb);
        standingVertex(consumer, pose, -h, size, z, sprite.getU0(), sprite.getV0(), argb);
        standingVertex(consumer, pose, -h, 0.0F, z, sprite.getU0(), sprite.getV1(), argb);
    }

    private static void standingVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
        Vector3f point = pose.pose().transformPosition(new Vector3f(x, y, z));
        Vector3f normal = pose.normal().transform(new Vector3f(0.0F, 0.0F, -1.0F));
        consumer.addVertex(point.x, point.y, point.z, argb, u, v, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, normal.x, normal.y,
                normal.z);
    }

    /** A flat square of colour on the surface at the slot (wells, highlights). */
    private static void renderSquare(PoseStack poseStack, SceneBuffers buffers, float size, int argb) {
        TextureAtlasSprite sprite = SceneDraw.blockSprite(HIGHLIGHT_SPRITE);
        VertexConsumer consumer = buffers.getBuffer(OVERLAY);
        PoseStack.Pose pose = poseStack.last();
        float h = size / 2.0F;
        float y = 0.04F;
        // Counter-clockwise seen from above, so the culled type keeps the top face.
        vertex(consumer, pose, -h, y, -h, sprite.getU0(), sprite.getV0(), argb);
        vertex(consumer, pose, -h, y, h, sprite.getU0(), sprite.getV1(), argb);
        vertex(consumer, pose, h, y, h, sprite.getU1(), sprite.getV1(), argb);
        vertex(consumer, pose, h, y, -h, sprite.getU1(), sprite.getV0(), argb);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
        Vector3f point = pose.pose().transformPosition(new Vector3f(x, y, z));
        Vector3f normal = pose.normal().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        consumer.addVertex(point.x, point.y, point.z, argb, u, v, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, normal.x, normal.y,
                normal.z);
    }

    /**
     * Buffers for an opaque item drawn into the scene. Before Minecraft 26.x the item renderer asked for the block atlas' see-through
     * type, which the scene draws as CLEAR (no depth, sorted by the middles of the quads), and these sent it into {@link #ITEMS}
     * instead. Items have render types of their own now, which write depth as they should, and these buffers are the scene's as
     * they are; kept for {@code DiegesisClient.items}.
     */
    public static SceneBuffers solidItems(SceneBuffers buffers) {
        return buffers;
    }

    private static boolean isBlockLike(ItemStack stack, ClientLevel level) {
        return SceneDraw.itemState(stack, ItemDisplayContext.FIXED, level, 0).usesBlockLight();
    }

    /**
     * Light falling on the work surface under every light source: brighter and tighter the nearer the source is, wavering
     * with its flicker. Drawn so that it brightens the surface's own colours instead of laying a haze over them.
     */
    private static void renderLightPools(SceneInstance scene, PoseStack poseStack, SceneBuffers buffers, float workPlane) {
        for (NodeInstance node : scene.nodes()) {
            if (node.lightLevel() <= 0 || !node.isVisible() || node.definition().light().isEmpty()) {
                continue;
            }
            LightDefinition light = node.definition().light().get();
            if ((light.color() >>> 24) == 0) {
                continue;
            }
            Vector3f at = node.matrix().transformPosition(new Vector3f(light.offset()));
            PlacingOptions placing = scene.definition().placing().orElse(null);
            if (placing == null) {
                renderPool(node, light, at, workPlane, null, poseStack, buffers);
                continue;
            }
            // The light stays in the room it is in: on the surface under it, not through the board to the one below. A surface the
            // scene moves (a door's shelf) gets its pool where it is now.
            for (int i = 0; i < placing.surfaces().size(); i++) {
                PlaceSurface surface = placing.surfaces().get(i);
                Matrix4f move = scene.surfaceMove(i);
                Vector3f local = move == null ? at : new Matrix4f(move).invert().transformPosition(new Vector3f(at));
                if (local.y < surface.height() || local.y > surface.height() + surface.clearance()) {
                    continue;
                }
                if (move != null) {
                    poseStack.pushPose();
                    multiply(poseStack, move);
                }
                renderPool(node, light, local, surface.height(), surface, poseStack, buffers);
                surface.wall().ifPresent(wall -> renderWallPool(node, light, local, wall, surface, poseStack, buffers));
                if (move != null) {
                    poseStack.popPose();
                }
            }
        }
    }

    /** The same on the wall behind a surface: a warm patch round the source, within the room. */
    private static void renderWallPool(NodeInstance node, LightDefinition light, Vector3f at, float wall, PlaceSurface surface,
            PoseStack poseStack, SceneBuffers buffers) {
        float distance = Math.max(0.0F, wall - at.z) / light.radius();
        if (distance >= 1.0F) {
            return;
        }
        float strength = node.lightLevel() / 15.0F * flicker(node, light) / (1.0F + 6.0F * distance * distance) * (1.0F - distance);
        float size = light.radius() * (0.45F + 0.75F * distance);
        float alpha = (light.color() >>> 24) / 255.0F * strength;
        if (alpha <= 0.01F) {
            return;
        }
        int rgb = light.color();
        int color = 0xFF000000 | Math.round((rgb >> 16 & 0xFF) * alpha) << 16 | Math.round((rgb >> 8 & 0xFF) * alpha) << 8
                | Math.round((rgb & 0xFF) * alpha);
        float x0 = Math.max(at.x - size, surface.from().x());
        float x1 = Math.min(at.x + size, surface.to().x());
        float y0 = Math.max(at.y - size, surface.height());
        float y1 = Math.min(at.y + size, surface.height() + surface.clearance());
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        float u0 = (x0 - at.x + size) / (size * 2.0F);
        float u1 = (x1 - at.x + size) / (size * 2.0F);
        float v0 = (y0 - at.y + size) / (size * 2.0F);
        float v1 = (y1 - at.y + size) / (size * 2.0F);
        VertexConsumer consumer = buffers.getBuffer(poolType());
        PoseStack.Pose pose = poseStack.last();
        float z = wall - 0.05F;
        vertex(consumer, pose, x0, y0, z, u0, v0, color);
        vertex(consumer, pose, x0, y1, z, u0, v1, color);
        vertex(consumer, pose, x1, y1, z, u1, v1, color);
        vertex(consumer, pose, x1, y0, z, u1, v0, color);
    }

    /** One pool of light at the height {@code plane}, cut off at the edges of {@code surface} (null: none). */
    private static void renderPool(NodeInstance node, LightDefinition light, Vector3f at, float plane, PlaceSurface surface,
            PoseStack poseStack, SceneBuffers buffers) {
        float height = Math.max(0.0F, at.y - plane) / light.radius();
        if (height >= 1.0F) {
            return;
        }
        // Inverse-square-like: a source close to the surface lights a small bright spot, a high one a wide faint one.
        float strength = node.lightLevel() / 15.0F * flicker(node, light) / (1.0F + 6.0F * height * height) * (1.0F - height);
        float size = light.radius() * (0.45F + 0.75F * height);
        float alpha = (light.color() >>> 24) / 255.0F * strength;
        if (alpha <= 0.01F) {
            return;
        }
        int rgb = light.color();
        int color = 0xFF000000 | Math.round((rgb >> 16 & 0xFF) * alpha) << 16 | Math.round((rgb >> 8 & 0xFF) * alpha) << 8
                | Math.round((rgb & 0xFF) * alpha);
        float x0 = at.x - size;
        float x1 = at.x + size;
        float z0 = at.z - size;
        float z1 = at.z + size;
        if (surface != null) {
            x0 = Math.max(x0, surface.from().x());
            x1 = Math.min(x1, surface.to().x());
            z0 = Math.max(z0, surface.from().y());
            z1 = Math.min(z1, surface.to().y());
            if (x0 >= x1 || z0 >= z1) {
                return;
            }
        }
        float u0 = (x0 - at.x + size) / (size * 2.0F);
        float u1 = (x1 - at.x + size) / (size * 2.0F);
        float v0 = (z0 - at.z + size) / (size * 2.0F);
        float v1 = (z1 - at.z + size) / (size * 2.0F);
        VertexConsumer consumer = buffers.getBuffer(poolType());
        PoseStack.Pose pose = poseStack.last();
        float y = plane + 0.05F;
        vertex(consumer, pose, x0, y, z0, u0, v0, color);
        vertex(consumer, pose, x0, y, z1, u0, v1, color);
        vertex(consumer, pose, x1, y, z1, u1, v1, color);
        vertex(consumer, pose, x1, y, z0, u1, v0, color);
    }

    /** How strong a light is this frame (1 = steady): a flame's waver, smooth and different for every light. */
    private static float flicker(NodeInstance node, LightDefinition light) {
        if (light.flicker() <= 0.0F) {
            return 1.0F;
        }
        double t = Util.getMillis() / 1000.0;
        double phase = (node.id().hashCode() & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        // A flame breathes: slow waves under one and a half beats a second, never a jump.
        double wave = 0.55 * Math.sin(t * 3.1 + phase) + 0.3 * Math.sin(t * 5.7 + phase * 2.0) + 0.15 * Math.sin(t * 9.3 + phase * 3.0);
        return 1.0F - light.flicker() * (float) (0.5 + 0.5 * wave);
    }

    private static RenderType poolType() {
        if (pool == null) {
            int size = 64;
            NativeImage image = new NativeImage(size, size, false);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    float distance = (float) Math.hypot(x + 0.5 - size / 2.0, y + 0.5 - size / 2.0) / (size / 2.0F);
                    float falloff = Mth.clamp(1.0F - distance, 0.0F, 1.0F);
                    falloff = falloff * falloff * falloff;
                    int value = Math.round(falloff * 255.0F);
                    // The falloff goes into the colour (it multiplies the surface); opaque, so nothing is discarded.
                    image.setPixelABGR(x, y, 0xFF000000 | value << 16 | value << 8 | value);
                }
            }
            Identifier texture = Identifier.fromNamespaceAndPath("diegesis", "dynamic/light_pool");
            Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "Diegesis light pool", image));
            pool = DiegesisRenderTypes.lightPool(texture);
        }
        return pool;
    }

    /** The scene's light sources this frame: x, y, z (station pixels), level (flicker included), radius. */
    private static List<float[]> lightsOf(SceneInstance scene) {
        List<float[]> lights = new ArrayList<>();
        for (NodeInstance node : scene.nodes()) {
            if (node.lightLevel() > 0 && node.isVisible() && node.definition().light().isPresent()) {
                LightDefinition light = node.definition().light().get();
                Vector3f at = node.matrix().transformPosition(new Vector3f(light.offset()));
                lights.add(new float[] {at.x, at.y, at.z, node.lightLevel() * flicker(node, light), light.radius()});
            }
        }
        return lights;
    }

    /** The station's light, brightened by the scene's light sources near {@code at} (they fade out over their radius). */
    private static int lit(int light, Vector3f at, List<float[]> lights) {
        if (lights.isEmpty()) {
            return light;
        }
        // In light map coordinates (16 a level), not whole levels: a flickering flame brightens and dims smoothly, without steps.
        float block = light & 0xFFFF;
        for (float[] source : lights) {
            if (SceneLitConsumer.hidden(source, at, drawnSurfaces)) {
                // A shelf between them: the light stays on its own shelf.
                continue;
            }
            // Soft falloff (bright near the source, a long gentle tail) instead of a straight ramp.
            float near = Math.max(0.0F, 1.0F - at.distance(source[0], source[1], source[2]) / source[4]);
            block = Math.max(block, source[3] * near * (2.0F - near) * 16.0F);
        }
        return Math.min(240, Math.round(block)) | light & 0xFFFF0000;
    }

    /** The light at the station: the brighter of the block itself and the space above it (a table top is lit from above). */
    private static int lightAt(ClientLevel level, BlockPos pos) {
        int inside = LightCoordsUtil.getLightCoords(level, pos);
        int above = LightCoordsUtil.getLightCoords(level, pos.above());
        int block = Math.max(LightCoordsUtil.block(inside), LightCoordsUtil.block(above));
        int sky = Math.max(LightCoordsUtil.sky(inside), LightCoordsUtil.sky(above));
        return LightCoordsUtil.pack(block, sky);
    }

    /** Multiplies a matrix into the pose stack, normals included (PoseStack.mulPoseMatrix leaves them alone). */
    private static void multiply(PoseStack poseStack, Matrix4f matrix) {
        PoseStack.Pose last = poseStack.last();
        last.pose().mul(matrix);
        last.normal().mul(matrix.normalize3x3(new Matrix3f()));
    }

    /** Swallows what is drawn into it. */
    private static final class NoVertices implements VertexConsumer {
        static final NoVertices INSTANCE = new NoVertices();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY,
                float normalZ) {
        }
    }

    /** Sends everything drawn through it into a see-through black layer of its atlas: the shadow of whatever is drawn. */
    private static final class ShadowBuffers implements SceneBuffers {
        private static float alpha = 0.5F;
        /** The direction to the light, in the space of the normals drawn into the shadow. */
        private static final Vector3f light = new Vector3f(LIGHT_DIRECTION);
        private final SceneBuffers buffers;

        ShadowBuffers(SceneBuffers buffers) {
            this.buffers = buffers;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            RenderType shadow = ITEM_ATLAS_TYPES.contains(type) ? ITEM_OVERLAY : OVERLAY;
            if (type != shadow) {
                KeptTypes.keep(buffers, type);
            }
            int a = Math.round(alpha * 255.0F);
            return new LitFaces(new TintedConsumer(buffers.getBuffer(shadow), a << 24), light);
        }
    }
}
