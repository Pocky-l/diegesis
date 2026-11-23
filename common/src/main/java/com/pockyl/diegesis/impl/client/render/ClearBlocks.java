package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pockyl.diegesis.api.scene.CameraGlance;
import com.pockyl.diegesis.api.scene.CameraRig;
import com.pockyl.diegesis.api.scene.CameraShot;
import com.pockyl.diegesis.api.scene.ClearViewOptions;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * Leaves blocks in the camera's way out of the picture while a player works at a station ({@link ClearViewOptions#blocks()}): the
 * blocks the camera stands in, from any shot of the scene, and the blocks between it and the station's work surface, such as a
 * wall cabinet over a counter or a low ceiling. A block counts as in the way when it stands before the point the camera looks
 * at, or before a good part of the work surface: a neighbour whose corner only grazes the view past the station's side (a
 * cabinet beside the one worked at, seen from three-quarters) stays. Chunk meshes are built as if those blocks were air (the game's, and Embeddium's or
 * Sodium's through their world slice), so the faces of their neighbours show and nothing is left hollow; light stays the world's,
 * so the station keeps the shade a cabinet gives it. Nobody else sees a change.
 *
 * <p>While the camera flies in or back, the blocks left out are drawn here instead, fading with the flight
 * ({@link ClearViewOptions#fade()}): out over the first share of the flight in, back over the last share of the flight back, and,
 * going over to a neighbour, the new station's fading out while the old one's come back. Each is drawn on its own depth first, so
 * that it turns see-through as a whole thing; what its block entity draws fades with it.
 *
 * <p>The shot itself never moves: what a scene hangs before the camera (a recipe book) stays where it was framed, stations side by
 * side keep their shots, and under a cabinet the camera still looks into a pot, which no lower angle does.
 */
public final class ClearBlocks {
    /** Room round the camera that must be free, so the near plane cuts into no block; more than vanilla's 0.1 for the flight. */
    private static final double ROOM = 0.3;
    /** Seconds between checks: a block placed or broken meanwhile. */
    private static final float RECHECK = 0.5F;
    /** Where the work surface is checked across the block, pixels from its side. */
    private static final float[] GRID = {2.0F, 5.0F, 8.0F, 11.0F, 14.0F};
    /** A block's side, pixels: a placing surface is checked at the same shares of its own width and depth. */
    private static final float BLOCK = 16.0F;
    /**
     * The share of the lines to the work surface a block must stand in before it is left out. Owner, 2026-11-17: a block
     * disappears only when it is in the way of the view; a neighbour that cuts the lines to the far corner of the surface
     * behind the station's own side wall is not.
     */
    private static final float IN_THE_WAY = 1.0F / 3.0F;
    /** How high things lying on the surface stand, pixels: their tops must show too. */
    private static final float RISE = 4.0F;
    private static final LongOpenHashSet NONE = new LongOpenHashSet();
    /**
     * How long a block put back is still drawn whole here, nanoseconds: the game rebuilds its chunk with it a few frames later,
     * off the render thread, and till then nothing would stand there. Drawn over its own block, it looks the same.
     */
    private static final long RETURNING_FOR = 400_000_000L;
    /** Below this a fading block is not drawn at all. */
    private static final float UNSEEN = 1.0F / 255.0F;

    /** The blocks left out; never changed once published, as the chunk builders read it on their own threads. */
    private static volatile LongOpenHashSet hidden = NONE;
    /** Of {@link #hidden}: the blocks in the way of the station worked at, and of the one the camera flies over from. */
    private static LongOpenHashSet ofStation = NONE;
    private static LongOpenHashSet ofLeaving = NONE;
    /** Their scenes' {@link ClearViewOptions#fade()}. */
    private static float stationFade;
    private static float leavingFade;
    /** Blocks just put back, drawn whole until {@link #returningUntil}: see {@link #RETURNING_FOR}. */
    private static final LongOpenHashSet RETURNING = new LongOpenHashSet();
    private static long returningUntil;
    private static float sinceCheck;
    private static ClientStation checkedStation;
    private static ClientStation checkedLeaving;
    /** The frame's level pose (the camera's rotation) and camera, caught after the entities, for {@link #renderFading}. */
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix3f VIEW_NORMAL = new Matrix3f();
    private static Vec3 eye = Vec3.ZERO;
    private static MultiBufferSource.BufferSource buffers;
    /** Whether this frame's blocks get their depth first; not under a shader pack ({@link DiegesisRenderTypes#fadingBlockUnderPack}). */
    private static boolean layered = true;
    /** The see-through twins of the types block entities draw with, made once; empty for text and lines. */
    private static final Map<RenderType, Optional<RenderType>> SEE_THROUGH = new java.util.HashMap<>();

    private ClearBlocks() {
    }

    /** Whether the chunk meshes leave out the block at {@code pos}. */
    public static boolean hides(BlockPos pos) {
        LongOpenHashSet set = hidden;
        return !set.isEmpty() && set.contains(pos.asLong());
    }

    /** Whether the chunk meshes leave out the block at {@code x y z}. */
    public static boolean hides(int x, int y, int z) {
        LongOpenHashSet set = hidden;
        return !set.isEmpty() && set.contains(BlockPos.asLong(x, y, z));
    }

    /**
     * Keeps the way clear for the station worked at, once a frame while the camera is at it or flying to or from it.
     *
     * @param leaving the station the camera flies over from, whose blocks stay out until it arrives; or null
     */
    public static void keep(ClientStation station, ClientStation leaving, float dt) {
        sinceCheck += dt;
        if (station == checkedStation && leaving == checkedLeaving && sinceCheck < RECHECK) {
            return;
        }
        sinceCheck = 0.0F;
        checkedStation = station;
        checkedLeaving = leaving;
        ClientLevel level = Minecraft.getInstance().level;
        LongOpenHashSet forStation = new LongOpenHashSet();
        LongOpenHashSet forLeaving = new LongOpenHashSet();
        if (level != null) {
            collect(level, station, forStation);
            collect(level, leaving, forLeaving);
        }
        boolean faded = stationFade > 0.0F || leavingFade > 0.0F;
        ofStation = forStation;
        ofLeaving = forLeaving;
        stationFade = fadeOf(station);
        leavingFade = fadeOf(leaving);
        LongOpenHashSet found = new LongOpenHashSet(forStation);
        found.addAll(forLeaving);
        publish(level, found, faded);
    }

    /** Puts every block back. */
    public static void clear() {
        checkedStation = null;
        checkedLeaving = null;
        boolean faded = stationFade > 0.0F || leavingFade > 0.0F;
        ofStation = NONE;
        ofLeaving = NONE;
        stationFade = 0.0F;
        leavingFade = 0.0F;
        publish(Minecraft.getInstance().level, NONE, faded);
    }

    private static float fadeOf(ClientStation station) {
        return station == null || station.scene() == null ? 0.0F : station.scene().definition().clearView().fade();
    }

    /** @param faded whether the blocks put back came back fading: they are then drawn whole until their chunk has them */
    private static void publish(ClientLevel level, LongOpenHashSet found, boolean faded) {
        LongOpenHashSet before = hidden;
        if (before.equals(found)) {
            return;
        }
        hidden = found.isEmpty() ? NONE : found;
        if (faded) {
            for (LongIterator it = before.iterator(); it.hasNext(); ) {
                long packed = it.nextLong();
                if (!found.contains(packed)) {
                    RETURNING.add(packed);
                    returningUntil = Util.getNanos() + RETURNING_FOR;
                }
            }
        }
        if (level == null) {
            return;
        }
        // Rebuilt at once where the loader can (Sodium and Embeddium rebuild important sections in the frame).
        rebuildChanged(level, before, found);
        rebuildChanged(level, found, before);
    }

    private static void rebuildChanged(ClientLevel level, LongOpenHashSet from, LongOpenHashSet other) {
        for (LongIterator it = from.iterator(); it.hasNext(); ) {
            long packed = it.nextLong();
            if (!other.contains(packed)) {
                BlockPos pos = BlockPos.of(packed);
                BlockState state = level.getBlockState(pos);
                Minecraft.getInstance().levelRenderer.blockChanged(level, pos, state, state, 8);
            }
        }
    }

    /** Notes the frame's level pose and camera; called after the entities. */
    public static void capture(PoseStack poseStack, Camera camera) {
        VIEW.set(poseStack.last().pose());
        VIEW_NORMAL.set(poseStack.last().normal());
        eye = camera.getPosition();
    }

    /**
     * Draws the blocks left out that are still fading, and those just put back that their chunk does not show yet. Called after
     * the level's translucent blocks and particles: a fading block lies over everything behind it, the station's scene too.
     */
    public static void renderFading() {
        LongOpenHashSet left = hidden;
        if (!RETURNING.isEmpty() && Util.getNanos() > returningUntil) {
            RETURNING.clear();
        }
        if (left.isEmpty() && RETURNING.isEmpty() || ShaderShadows.drawing()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        // By the flight's time, not its eased way: the camera covers most of the way in the first moments.
        float time = FocusController.get().flightProgress();
        float away = stationFade > 0.0F ? 1.0F - smooth(time / stationFade) : 0.0F;
        float back = leavingFade > 0.0F ? 1.0F - smooth((1.0F - time) / leavingFade) : 0.0F;
        if (buffers == null) {
            Map<RenderType, BufferBuilder> kept = new LinkedHashMap<>();
            kept.put(DiegesisRenderTypes.fadingBlockDepth(), new BufferBuilder(1 << 14));
            kept.put(DiegesisRenderTypes.fadingBlock(), new BufferBuilder(1 << 14));
            kept.put(DiegesisRenderTypes.fadingBlockUnderPack(), new BufferBuilder(1 << 14));
            buffers = MultiBufferSource.immediateWithBuffers(kept, new BufferBuilder(1 << 14));
        }
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(VIEW);
        poseStack.last().normal().set(VIEW_NORMAL);
        layered = !ShaderShadows.packInUse();
        boolean drawn = false;
        for (LongIterator it = left.iterator(); it.hasNext(); ) {
            long packed = it.nextLong();
            float opacity = 1.0F;
            boolean counted = false;
            if (ofStation.contains(packed)) {
                opacity = away;
                counted = true;
            }
            if (ofLeaving.contains(packed)) {
                opacity = counted ? Math.min(opacity, back) : back;
                counted = true;
            }
            if (counted && opacity > UNSEEN) {
                draw(minecraft, level, BlockPos.of(packed), opacity, poseStack);
                drawn = true;
            }
        }
        for (LongIterator it = RETURNING.iterator(); it.hasNext(); ) {
            long packed = it.nextLong();
            if (!left.contains(packed)) {
                draw(minecraft, level, BlockPos.of(packed), 1.0F, poseStack);
                drawn = true;
            }
        }
        if (drawn) {
            // Every block's depth before any colour: a block before another hides it as it would, see-through as both are.
            buffers.endBatch(DiegesisRenderTypes.fadingBlockDepth());
            buffers.endBatch(DiegesisRenderTypes.fadingBlock());
            buffers.endBatch(DiegesisRenderTypes.fadingBlockUnderPack());
            buffers.endBatch();
        }
    }

    /** One block, {@code opacity} solid: its model, and what its block entity draws. */
    private static void draw(Minecraft minecraft, ClientLevel level, BlockPos pos, float opacity, PoseStack poseStack) {
        BlockState state = level.getBlockState(pos);
        int alpha = Math.max(1, Math.min(255, Math.round(opacity * 255.0F)));
        poseStack.pushPose();
        poseStack.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
        try {
            if (state.getRenderShape() == RenderShape.MODEL) {
                BlockRenderDispatcher blocks = minecraft.getBlockRenderer();
                // Faces against other blocks are left out, as in the chunk: two blocks fading together fade as one.
                if (layered) {
                    blocks.renderBatched(state, pos, level, poseStack, buffers.getBuffer(DiegesisRenderTypes.fadingBlockDepth()), true,
                            RandomSource.create());
                }
                RenderType colour = layered ? DiegesisRenderTypes.fadingBlock() : DiegesisRenderTypes.fadingBlockUnderPack();
                blocks.renderBatched(state, pos, level, poseStack, new TintedConsumer(buffers.getBuffer(colour), alpha << 24 | 0xFFFFFF),
                        true, RandomSource.create());
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                drawBlockEntity(minecraft, level, blockEntity, alpha, poseStack);
            }
        } catch (RuntimeException e) {
            // A modded model or block entity that cannot be drawn here only stays unseen while it fades.
            DiegesisMod.LOGGER.debug("Fading the block at {} failed", pos, e);
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawBlockEntity(Minecraft minecraft, ClientLevel level, BlockEntity blockEntity, int alpha, PoseStack poseStack) {
        BlockEntityRenderer<BlockEntity> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        if (renderer == null || !blockEntity.getType().isValid(blockEntity.getBlockState()) || !renderer.shouldRender(blockEntity, eye)) {
            return;
        }
        int argb = alpha << 24 | 0xFFFFFF;
        MultiBufferSource faded = type -> {
            // Sorted and writing depth, as the game's translucent entities: drawn after the scene, they hide nothing of it, and a
            // chest's latch stays before its front.
            Optional<RenderType> ghost = SEE_THROUGH.computeIfAbsent(type,
                    key -> ClearView.textureOf(key).map(RenderType::entityTranslucentCull));
            if (ghost.isEmpty()) {
                // Text and lines stay solid and get a buffer of their own: Iris writes glyphs straight into the buffer under a
                // consumer and crashes on any wrapper.
                return buffers.getBuffer(type);
            }
            KeptTypes.keep(buffers, type);
            return new TintedConsumer(buffers.getBuffer(ghost.get()), argb);
        };
        renderer.render(blockEntity, minecraft.getFrameTime(), poseStack, faded, LevelRenderer.getLightColor(level, blockEntity.getBlockPos()),
                OverlayTexture.NO_OVERLAY);
    }

    private static float smooth(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static void collect(ClientLevel level, ClientStation station, LongOpenHashSet found) {
        if (station == null || station.scene() == null) {
            return;
        }
        SceneDefinition definition = station.scene().definition();
        if (!definition.clearView().blocks()) {
            return;
        }
        List<PlaceSurface> surfaces = definition.clearView().surface() == ClearViewOptions.Surface.PLACING
                ? definition.placing().map(PlacingOptions::surfaces).orElse(List.of())
                : List.of();
        CameraRig rig = definition.camera();
        CollisionContext context = CollisionContext.empty();
        shot(level, station, rig.position(), rig.target(), surfaces, context, found);
        for (CameraShot seat : rig.seats().values()) {
            shot(level, station, seat.position(), seat.target(), surfaces, context, found);
        }
        rig.inventory().ifPresent(inventory -> shot(level, station, inventory.position(), inventory.target(), surfaces, context, found));
        for (CameraGlance glance : rig.glances()) {
            shot(level, station, glance.position(), glance.target(), surfaces, context, found);
        }
    }

    /**
     * What is in the way of one shot: blocks round the camera, and between it and the work surface: the block's middle round the
     * target, or each of {@code surfaces} on its own when there are any.
     */
    private static void shot(ClientLevel level, ClientStation station, Vector3fc position, Vector3fc target, List<PlaceSurface> surfaces,
            CollisionContext context, LongOpenHashSet found) {
        Vec3 camera = station.toWorld(position.x(), position.y(), position.z());
        // The station's own blocks are never in its way: its block, and every other block of a multiblock its scene stands in for.
        LongOpenHashSet own = new LongOpenHashSet();
        own.add(station.pos().asLong());
        station.type().hiddenBlocks(level, station.pos()).forEach(block -> own.add(block.asLong()));
        AABB room = new AABB(camera, camera).inflate(ROOM);
        VoxelShape roomShape = Shapes.create(room);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
            if (!own.contains(pos.asLong())) {
                VoxelShape shape = level.getBlockState(pos).getVisualShape(level, pos, context);
                if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()), roomShape, BooleanOp.AND)) {
                    found.add(pos.asLong());
                }
            }
        }
        // What stands before the point looked at is in the way; before the rest of the surface, only what covers a good part of it.
        sight(level, own, camera, station.toWorld(target.x(), target.y(), target.z()), context, found);
        LongOpenHashSet crossing = new LongOpenHashSet();
        if (surfaces.isEmpty()) {
            surface(level, station, own, camera, 0.0F, 0.0F, BLOCK, BLOCK, target.y(), context, found, crossing);
        } else {
            for (PlaceSurface surface : surfaces) {
                surface(level, station, own, camera, Math.min(surface.from().x(), surface.to().x()),
                        Math.min(surface.from().y(), surface.to().y()), Math.abs(surface.to().x() - surface.from().x()),
                        Math.abs(surface.to().y() - surface.from().y()), surface.height(), context, found, crossing);
            }
        }
        // What stands on a block left out and still crosses the view goes with it (a pot on a stove in the way): left, it would hang
        // in the air before the camera. Up a stack, as far as it crosses the view.
        boolean grew = true;
        while (grew) {
            grew = false;
            for (LongIterator it = crossing.iterator(); it.hasNext(); ) {
                long packed = it.nextLong();
                if (!found.contains(packed) && found.contains(BlockPos.offset(packed, 0, -1, 0))) {
                    found.add(packed);
                    grew = true;
                }
            }
        }
    }

    /**
     * The blocks before a good part of one work surface, {@code width} by {@code depth} pixels from its corner at {@code x z}, at
     * {@code height} and up to the tops of things lying on it, go into {@code found}; every block any of its lines crosses into
     * {@code crossing}.
     */
    private static void surface(ClientLevel level, ClientStation station, LongOpenHashSet own, Vec3 camera, float x, float z, float width,
            float depth, float height, CollisionContext context, LongOpenHashSet found, LongOpenHashSet crossing) {
        Long2IntOpenHashMap cut = new Long2IntOpenHashMap();
        LongOpenHashSet line = new LongOpenHashSet();
        int lines = 0;
        for (float rise : new float[] {0.0F, RISE}) {
            for (float across : GRID) {
                for (float along : GRID) {
                    line.clear();
                    sight(level, own, camera, station.toWorld(x + across / BLOCK * width, height + rise, z + along / BLOCK * depth),
                            context, line);
                    for (LongIterator it = line.iterator(); it.hasNext(); ) {
                        cut.addTo(it.nextLong(), 1);
                    }
                    lines++;
                }
            }
        }
        int needed = (int) Math.ceil(lines * IN_THE_WAY);
        cut.long2IntEntrySet().forEach(entry -> {
            if (entry.getIntValue() >= needed) {
                found.add(entry.getLongKey());
            }
        });
        crossing.addAll(cut.keySet());
    }

    /** Every block whose visual shape the line from {@code from} to {@code to} passes through. */
    private static void sight(ClientLevel level, LongOpenHashSet own, Vec3 from, Vec3 to, CollisionContext context, LongOpenHashSet found) {
        BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> {
            if (!own.contains(pos.asLong())) {
                VoxelShape shape = level.getBlockState(pos).getVisualShape(level, pos, ctx);
                if (!shape.isEmpty() && shape.clip(from, to, pos) != null) {
                    found.add(pos.asLong());
                }
            }
            return null;
        }, ctx -> null);
    }
}
