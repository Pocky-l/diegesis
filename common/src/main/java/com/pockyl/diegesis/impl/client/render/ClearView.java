package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.ClearViewOptions;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.focus.FocusController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the camera's view of the station free ({@link ClearViewOptions}). A cone runs from the camera to what it looks at; every
 * vertex of what is inside fades by how deep it lies in the cone, so a figure dissolves softly from the middle of the view
 * outwards. Entities are drawn before the scene and, faded, write no depth: the scene comes out whole, wherever they stand.
 * Everything here is in view space (the camera at the origin), where the level's pose stack puts the entities' vertices.
 */
public final class ClearView {
    /** Width of the cone at the camera, in blocks. */
    private static final float NEAR_RADIUS = 0.2F;
    /** How far past what the camera looks at things still fade, in blocks: what stands behind the station hides nothing. */
    private static final float BEHIND = 0.5F;
    private static final Pattern TEXTURE = Pattern.compile("Optional\\[([a-z0-9_.-]+:[a-z0-9_/.-]+)]");
    private static final Map<RenderType, Optional<RenderType>> GHOSTS = new HashMap<>();
    private static final QuadFading PARTICLES = new QuadFading();

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Vector3f AXIS = new Vector3f();
    private static final Vector3f POINT = new Vector3f();
    private static float strength;
    private static float length;
    private static float radius;
    private static float soft;
    private static boolean entities;
    private static boolean particles;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static Entity current;

    private ClearView() {
    }

    /**
     * Called at the start of the level's rendering.
     *
     * @param poseStack the level pose stack: the camera's rotation, no translation
     */
    public static void begin(PoseStack poseStack, Camera camera) {
        strength = 0.0F;
        FocusController focus = FocusController.get();
        ClientStation station = focus.station();
        if (!focus.isActive() || station == null || station.scene() == null) {
            return;
        }
        ClearViewOptions options = station.scene().definition().clearView();
        Vec3 eye = camera.getPosition();
        Vector3fc looked = focus.baseTarget();
        Vec3 target = station.toWorld(looked.x(), looked.y(), looked.z());
        VIEW.set(poseStack.last().pose());
        VIEW.transformDirection(AXIS.set((float) (target.x - eye.x), (float) (target.y - eye.y), (float) (target.z - eye.z)));
        length = AXIS.length();
        if (length < 0.01F) {
            return;
        }
        AXIS.div(length);
        cameraX = eye.x;
        cameraY = eye.y;
        cameraZ = eye.z;
        radius = options.radius() / 16.0F;
        soft = options.soft() / 16.0F;
        entities = options.entities();
        particles = options.particles();
        strength = focus.blend();
    }

    /** How solid a point in view space stays, 0 to 1. */
    private static float fade(float x, float y, float z) {
        float along = x * AXIS.x + y * AXIS.y + z * AXIS.z;
        if (along <= 0.0F || along >= length + BEHIND) {
            return 1.0F;
        }
        float dx = x - AXIS.x * along;
        float dy = y - AXIS.y * along;
        float dz = z - AXIS.z * along;
        float width = NEAR_RADIUS + (radius - NEAR_RADIUS) * Math.min(1.0F, along / length);
        float inside = 1.0F - smooth(((float) Math.sqrt(dx * dx + dy * dy + dz * dz) - width) / soft);
        if (along > length) {
            inside *= 1.0F - smooth((along - length) / BEHIND);
        }
        return 1.0F - strength * inside;
    }

    private static float smooth(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    /** Notes the entity about to be drawn. */
    public static void entity(Entity entity) {
        current = entity;
    }

    /** What the entity noted last draws into: the game's buffers, or fading ones while it may stand in the way. */
    public static MultiBufferSource buffers(MultiBufferSource buffers) {
        if (strength <= 0.0F || !entities || current == null || !mayBeInside(current.getBoundingBox())) {
            return buffers;
        }
        return type -> {
            RenderType ghost = GHOSTS.computeIfAbsent(type, ClearView::ghostOf).orElse(type);
            if (ghost != type) {
                KeptTypes.keep(buffers, type);
            }
            Fading fading = new Fading();
            fading.delegate = buffers.getBuffer(ghost);
            return fading;
        };
    }

    private static boolean mayBeInside(AABB box) {
        Vec3 center = box.getCenter();
        float reach = (float) Math.max(box.getSize(), 1.0) + soft;
        VIEW.transformDirection(POINT.set((float) (center.x - cameraX), (float) (center.y - cameraY), (float) (center.z - cameraZ)));
        float along = POINT.dot(AXIS);
        if (along < -reach || along > length + BEHIND + reach) {
            return false;
        }
        float distance = POINT.fma(-along, AXIS).length();
        return distance < radius + reach;
    }

    /** The translucent, depth-less twin of a textured entity type; empty for what has no texture to show (lines, text, glints). */
    private static Optional<RenderType> ghostOf(RenderType type) {
        if (type.format() != DefaultVertexFormat.NEW_ENTITY) {
            return Optional.empty();
        }
        // The state's texture is out of reach of code that is no render state, but every type prints it.
        Matcher matcher = TEXTURE.matcher(type.toString());
        return matcher.find() ? Optional.of(DiegesisRenderTypes.ghostEntity(new ResourceLocation(matcher.group(1)))) : Optional.empty();
    }

    /** What a particle draws into: its vertices are relative to the camera, not yet turned to it. */
    public static VertexConsumer particles(VertexConsumer consumer) {
        if (strength <= 0.0F || !particles) {
            return consumer;
        }
        PARTICLES.start(consumer);
        return PARTICLES;
    }

    /**
     * What a particle draws, a quad at a time: it fades and shrinks towards its middle by how deep the middle lies in the cone.
     * Shrinking keeps it soft for particles drawn without blending (the smoke and flames of a campfire): those show whole down
     * to a tenth of their alpha, so a fade alone made them pop in at the edge of the cone.
     */
    private static final class QuadFading implements VertexConsumer {
        private VertexConsumer delegate;
        private final float[] position = new float[12];
        private final float[] texture = new float[8];
        private final int[] color = new int[16];
        private final int[] light = new int[8];
        private int count;

        void start(VertexConsumer consumer) {
            delegate = consumer;
            count = 0;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            position[count * 3] = (float) x;
            position[count * 3 + 1] = (float) y;
            position[count * 3 + 2] = (float) z;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            color[count * 4] = red;
            color[count * 4 + 1] = green;
            color[count * 4 + 2] = blue;
            color[count * 4 + 3] = alpha;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            texture[count * 2] = u;
            texture[count * 2 + 1] = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            light[count * 2] = u;
            light[count * 2 + 1] = v;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            if (++count == 4) {
                flush();
            }
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int packedLight,
                float normalX, float normalY, float normalZ) {
            vertex(x, y, z);
            color(Math.round(red * 255.0F), Math.round(green * 255.0F), Math.round(blue * 255.0F), Math.round(alpha * 255.0F));
            uv(u, v);
            uv2(packedLight & 0xFFFF, packedLight >> 16 & 0xFFFF);
            endVertex();
        }

        private void flush() {
            float cx = (position[0] + position[3] + position[6] + position[9]) / 4.0F;
            float cy = (position[1] + position[4] + position[7] + position[10]) / 4.0F;
            float cz = (position[2] + position[5] + position[8] + position[11]) / 4.0F;
            VIEW.transformDirection(POINT.set(cx, cy, cz));
            float fade = ClearView.fade(POINT.x, POINT.y, POINT.z);
            for (int i = 0; i < 4; i++) {
                delegate.vertex(cx + (position[i * 3] - cx) * fade, cy + (position[i * 3 + 1] - cy) * fade, cz + (position[i * 3 + 2] - cz) * fade)
                        .uv(texture[i * 2], texture[i * 2 + 1])
                        .color(color[i * 4], color[i * 4 + 1], color[i * 4 + 2], Math.round(color[i * 4 + 3] * fade))
                        .uv2(light[i * 2], light[i * 2 + 1])
                        .endVertex();
            }
            count = 0;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }

    /** Passes vertices on with their alpha scaled by where they are. */
    private static final class Fading implements VertexConsumer {
        private VertexConsumer delegate;
        private float fade = 1.0F;

        private void look(float x, float y, float z) {
            fade = ClearView.fade(x, y, z);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            look((float) x, (float) y, (float) z);
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, Math.round(alpha * fade));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light,
                float normalX, float normalY, float normalZ) {
            look(x, y, z);
            delegate.vertex(x, y, z, red, green, blue, alpha * fade, u, v, overlay, light, normalX, normalY, normalZ);
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
