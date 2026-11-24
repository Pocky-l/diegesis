package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.impl.client.focus.FocusScreen;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * What a {@link NodePainter#smooth() smooth} painter draws: kept from the level render and drawn over the finished frame from
 * the focus screen, four times as fine each way into a target of its own and scaled down, so its edges have no steps
 * (the game draws without anti-aliasing). Only the part of the screen it covers is drawn that fine. Where the fine targets
 * fail (a driver that refuses them), the painters are still drawn over the frame, only as fine as the screen: a block beside
 * the station never covers them.
 */
public final class SmoothPass {
    /** Each way: 4 is sixteen samples a pixel. Two halvings take it down, each an exact average of four. */
    private static final int FINE = 4;
    /** The most pixels the fine target may have; past it a painter is drawn twice as fine only. */
    private static final int MAX_FINE_PIXELS = 8_000_000;
    /** Pixels round the measured outline: the last samples of a slanted edge. */
    private static final int PAD = 2;

    private record Pending(NodePainter painter, Matrix4f pose, Matrix3f normal, int light) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    /** Scratch buffers the painters are measured in, one per render type, kept between frames. */
    private static final Map<RenderType, ByteBufferBuilder> MEASURING = new HashMap<>();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static TextureTarget fine;
    private static TextureTarget half;
    /** The fine targets failed once: painters are drawn straight over the frame from then on. */
    private static boolean failed;
    /** Drawing straight over the frame failed too: painters are drawn with the scene. */
    private static boolean broken;
    private static boolean drawing;
    private static MultiBufferSource.BufferSource buffers;

    private SmoothPass() {
    }

    /** A new level frame: what was kept and not drawn is dropped. {@code view}: the level's model-view matrix, the camera's turn. */
    static void begin(Matrix4f projection, Matrix4f view) {
        PENDING.clear();
        PROJECTION.set(projection);
        VIEW.set(view);
        projection.mul(view, VIEW_PROJECTION);
    }

    /**
     * Keeps a smooth painter for the focus screen to draw, at the pose it has now; false when it cannot be drawn smooth this
     * frame and must be drawn with the scene.
     */
    static boolean defer(NodePainter painter, PoseStack poseStack, int light) {
        if (broken || !(Minecraft.getInstance().screen instanceof FocusScreen)) {
            return false;
        }
        PENDING.add(new Pending(painter, new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), light));
        return true;
    }

    /**
     * Whether the painters are being drawn over the frame now. The frame is composed by then: under Fabulous graphics the
     * level's layer targets (item entities, translucent, particles) were blended into it already, and a draw into one of them
     * is lost. What the painters draw is drawn as Fancy graphics draws it instead, into the pass's own target
     * ({@code ShaderTransparencyMixin}): a slime block on a book page left its cell empty and moved every later draw into the
     * frame, four times as large.
     */
    public static boolean drawingOverTheFrame() {
        return drawing;
    }

    /** Draws what was kept over the frame; called first thing by the focus screen. */
    public static void draw() {
        if (PENDING.isEmpty()) {
            return;
        }
        drawing = true;
        try {
            if (!failed) {
                try {
                    drawPending();
                    return;
                } catch (RuntimeException e) {
                    failed = true;
                    DiegesisMod.LOGGER.error("Smooth drawing failed; smooth painters are drawn over the frame without smoothing from now on", e);
                    Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
                }
            }
            drawStraight();
        } catch (RuntimeException e) {
            broken = true;
            DiegesisMod.LOGGER.error("Drawing over the frame failed; smooth painters are drawn with the scene from now on", e);
        } finally {
            drawing = false;
            PENDING.clear();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    /**
     * The painters straight onto the frame, as fine as the screen: over everything the level drew, with a depth of their own
     * cleared before and after, so neither the level nor the screen drawn after them meets it.
     */
    private static void drawStraight() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        main.bindWrite(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        try {
            withMatrices(PROJECTION, () -> {
                MultiBufferSource.BufferSource source = buffers();
                for (Pending pending : PENDING) {
                    pending.painter().paint(poseOf(pending), source, pending.light());
                }
                source.endBatch();
            });
        } finally {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }

    /** Frees the targets when no station is open. */
    public static void release() {
        if (fine != null) {
            fine.destroyBuffers();
            fine = null;
        }
        if (half != null) {
            half.destroyBuffers();
            half = null;
        }
    }

    private static void drawPending() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        Set<RenderType> measured = new LinkedHashSet<>();
        int[] box = measure(main.width, main.height, measured);
        if (box == null) {
            if (!measured.isEmpty()) {
                // Nothing on the screen; what the painters asked for is still drawn, empty (see KeptTypes), where it would be.
                withMatrices(PROJECTION, () -> keep(measured));
            }
            return;
        }
        int width = box[2] - box[0];
        int height = box[3] - box[1];
        int scale = (long) width * height * FINE * FINE <= MAX_FINE_PIXELS ? FINE : 2;
        fine = sized(fine, width * scale, height * scale, true);
        if (scale == FINE) {
            half = sized(half, width * 2, height * 2, false);
        } else if (half != null) {
            // Dropped without freeing, its texture and framebuffer stayed taken until the game closed.
            half.destroyBuffers();
            half = null;
        }

        // The projection, narrowed to the box: the box fills the fine target.
        float left = 2.0F * box[0] / main.width - 1.0F;
        float right = 2.0F * box[2] / main.width - 1.0F;
        float bottom = 2.0F * box[1] / main.height - 1.0F;
        float top = 2.0F * box[3] / main.height - 1.0F;
        Matrix4f narrowed = new Matrix4f().scale(2.0F / (right - left), 2.0F / (top - bottom), 1.0F)
                .translate(-(left + right) / 2.0F, -(bottom + top) / 2.0F, 0.0F).mul(PROJECTION);

        fine.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        fine.clear(Minecraft.ON_OSX);
        fine.bindWrite(true);
        withMatrices(narrowed, () -> {
            MultiBufferSource source = buffers();
            for (Pending pending : PENDING) {
                pending.painter().paint(poseOf(pending), source, pending.light());
            }
            keep(measured);
        });

        RenderTarget last = fine;
        if (half != null) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fine.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, half.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, fine.width, fine.height, 0, 0, half.width, half.height, GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_LINEAR);
            last = half;
        }
        main.bindWrite(true);
        composite(last, left, right, bottom, top);
    }

    /** Draws with {@code projection}, the view's model-view and the level's light, as the painters were posed for. */
    private static void withMatrices(Matrix4f projection, Runnable draw) {
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(VIEW);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        Lighting.setupLevel();
        RenderSystem.enableDepthTest();
        try {
            draw.run();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            Lighting.setupFor3DItems();
        }
    }

    /**
     * An own source, drawn in the painters' order: shader packs swap the game's for one that holds draws back. Glints have
     * buffers of their own, as in the game's: an enchanted item on a book page threw in a shared one.
     */
    private static MultiBufferSource.BufferSource buffers() {
        if (buffers == null) {
            SequencedMap<RenderType, ByteBufferBuilder> glints = new LinkedHashMap<>();
            DiegesisRenderTypes.keepGlints(glints);
            buffers = MultiBufferSource.immediateWithBuffers(glints, new ByteBufferBuilder(1 << 16));
        }
        return buffers;
    }

    /** Draws the types measuring took from the painters, then everything: see KeptTypes. */
    private static void keep(Set<RenderType> measured) {
        MultiBufferSource.BufferSource source = buffers();
        for (RenderType type : measured) {
            KeptTypes.keep(source, type);
        }
        source.endBatch();
    }

    /** Lays the target over the frame in the box; its colours are premultiplied (drawn over clear black). */
    private static void composite(RenderTarget target, float left, float right, float bottom, float top) {
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        try {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(left, bottom, 0.0F).setUv(0.0F, 0.0F);
            builder.addVertex(right, bottom, 0.0F).setUv(1.0F, 0.0F);
            builder.addVertex(right, top, 0.0F).setUv(1.0F, 1.0F);
            builder.addVertex(left, top, 0.0F).setUv(0.0F, 1.0F);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
    }

    private static PoseStack poseOf(Pending pending) {
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(pending.pose());
        poseStack.last().normal().set(pending.normal());
        return poseStack;
    }

    /** A target of this size, the one given when it fits; linear, so halving it averages four samples. */
    private static TextureTarget sized(TextureTarget target, int width, int height, boolean depth) {
        if (target != null && target.width == width && target.height == height) {
            return target;
        }
        if (target != null) {
            target.resize(width, height, Minecraft.ON_OSX);
        } else {
            target = new TextureTarget(width, height, depth, Minecraft.ON_OSX);
        }
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }

    /**
     * The box on the screen the kept painters cover, in framebuffer pixels from the bottom left: {x0, y0, x1, y1}; null when
     * they draw nothing on it. They draw once into scratch buffers of the game's own kind, read back afterwards: Iris on Sodium
     * writes text straight into the buffer under a vertex consumer and takes no other kind. The types they ask for go into
     * {@code measured}, to be drawn after all.
     */
    private static int[] measure(int screenWidth, int screenHeight, Set<RenderType> measured) {
        Map<RenderType, BufferBuilder> building = new LinkedHashMap<>();
        MultiBufferSource measuring = type -> {
            measured.add(type);
            return building.computeIfAbsent(type, ignored -> new BufferBuilder(
                    MEASURING.computeIfAbsent(type, unused -> new ByteBufferBuilder(256)), type.mode(), type.format()));
        };
        Bounds bounds = new Bounds();
        try {
            for (Pending pending : PENDING) {
                pending.painter().paint(poseOf(pending), measuring, pending.light());
            }
        } finally {
            for (BufferBuilder builder : building.values()) {
                try (MeshData mesh = builder.build()) {
                    if (mesh != null) {
                        bounds.add(mesh);
                    }
                }
            }
        }
        if (bounds.empty()) {
            return null;
        }
        int x0 = Math.max(0, (int) Math.floor((bounds.minX * 0.5F + 0.5F) * screenWidth) - PAD);
        int y0 = Math.max(0, (int) Math.floor((bounds.minY * 0.5F + 0.5F) * screenHeight) - PAD);
        int x1 = Math.min(screenWidth, (int) Math.ceil((bounds.maxX * 0.5F + 0.5F) * screenWidth) + PAD);
        int y1 = Math.min(screenHeight, (int) Math.ceil((bounds.maxY * 0.5F + 0.5F) * screenHeight) + PAD);
        return x1 > x0 && y1 > y0 ? new int[] {x0, y0, x1, y1} : null;
    }

    /** Where on the screen the vertices of a draw land; one behind the camera covers it all. */
    private static final class Bounds {
        private final Vector4f clip = new Vector4f();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        boolean empty() {
            return minX > maxX || minY > maxY || maxX < -1.0F || minX > 1.0F || maxY < -1.0F || minY > 1.0F;
        }

        /** Every vertex format starts with the position, three floats. */
        void add(MeshData mesh) {
            ByteBuffer vertices = mesh.vertexBuffer();
            int size = mesh.drawState().format().getVertexSize();
            int count = mesh.drawState().vertexCount();
            for (int i = 0; i < count; i++) {
                int at = vertices.position() + i * size;
                add(vertices.getFloat(at), vertices.getFloat(at + 4), vertices.getFloat(at + 8));
            }
        }

        private void add(float x, float y, float z) {
            VIEW_PROJECTION.transform(clip.set(x, y, z, 1.0F));
            // Behind the camera, or not a number (a painter given a broken size): the whole screen, so it is still drawn.
            if (!(clip.w > 1.0e-4F) || !Float.isFinite(clip.x) || !Float.isFinite(clip.y)) {
                minX = -1.0F;
                minY = -1.0F;
                maxX = 1.0F;
                maxY = 1.0F;
                return;
            }
            float ndcX = Math.max(-1.0F, Math.min(1.0F, clip.x / clip.w));
            float ndcY = Math.max(-1.0F, Math.min(1.0F, clip.y / clip.w));
            minX = Math.min(minX, ndcX);
            minY = Math.min(minY, ndcY);
            maxX = Math.max(maxX, ndcX);
            maxY = Math.max(maxY, ndcY);
        }
    }
}
