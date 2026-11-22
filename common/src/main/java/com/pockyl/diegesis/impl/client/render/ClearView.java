package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
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
 * Everything here is in view space (the camera at the origin); entities and particles arrive relative to the camera but not yet
 * turned to it (the level's view rotation is in the model-view matrix), so their vertices are turned here.
 *
 * <p>Minecraft 26.x: not hooked in yet. Entities and particles are drawn from render states the game extracts and submits, and the
 * hooks this was fed by (an entity's buffers, a particle's own drawing) are gone: until they are fed from the submits, entities and
 * particles in the camera's way do not fade. The blocks in its way still go ({@link ClearBlocks}).
 */
public final class ClearView {
    /** Width of the cone at the camera, in blocks. */
    private static final float NEAR_RADIUS = 0.2F;
    /** How far past what the camera looks at things still fade, in blocks: what stands behind the station hides nothing. */
    private static final float BEHIND = 0.5F;
    private static final Pattern TEXTURE = Pattern.compile("Optional\\[([a-z0-9_.-]+:[a-z0-9_/.-]+)]");
    private static final Map<RenderType, Optional<RenderType>> GHOSTS = new HashMap<>();
    /**
     * Whether the renderer takes vertices only into buffers of its own kind: Sodium up to 0.5.3, Rubidium and Embeddium 0.1
     * write entity shadows, glints and outlines straight into the buffer under a vertex consumer and crash on any other. Their
     * API, like early Embeddium's, has no {@code canUseIntrinsics} yet. Entities in the way stay solid there.
     */
    private static final boolean STRICT_RENDERER = strictRenderer();

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
    private static FadingParticle faded;
    /** This frame's cone for particles, for {@link #particleFadeAt}; null while nothing fades. */
    private static volatile Cone cone;

    /** The clear view's cone as it stands this frame, unchanging: readable from any thread. */
    private record Cone(Matrix4f view, float axisX, float axisY, float axisZ, float length, float radius, float soft, float strength,
                        double cameraX, double cameraY, double cameraZ) {
    }

    private ClearView() {
    }

    /**
     * Called at the start of the level's rendering.
     *
     * @param view the level's view matrix: the camera's rotation, no translation
     */
    public static void begin(Matrix4f view, Camera camera) {
        strength = 0.0F;
        cone = null;
        FocusController focus = FocusController.get();
        ClientStation station = focus.station();
        if (!focus.isActive() || station == null || station.scene() == null) {
            return;
        }
        ClearViewOptions options = station.scene().definition().clearView();
        Vec3 eye = camera.position();
        Vector3fc looked = focus.baseTarget();
        Vec3 target = station.toWorld(looked.x(), looked.y(), looked.z());
        VIEW.set(view);
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
        if (particles && strength > 0.0F) {
            cone = new Cone(new Matrix4f(VIEW), AXIS.x, AXIS.y, AXIS.z, length, radius, soft, strength, cameraX, cameraY, cameraZ);
        }
    }

    /** How solid a point in view space stays, 0 to 1. */
    private static float fade(float x, float y, float z) {
        return fade(AXIS.x, AXIS.y, AXIS.z, length, radius, soft, strength, x, y, z);
    }

    private static float fade(float axisX, float axisY, float axisZ, float length, float radius, float soft, float strength,
                              float x, float y, float z) {
        float along = x * axisX + y * axisY + z * axisZ;
        if (along <= 0.0F || along >= length + BEHIND) {
            return 1.0F;
        }
        float dx = x - axisX * along;
        float dy = y - axisY * along;
        float dz = z - axisZ * along;
        float width = NEAR_RADIUS + (radius - NEAR_RADIUS) * Math.min(1.0F, along / length);
        float inside = 1.0F - smooth(((float) Math.sqrt(dx * dx + dy * dy + dz * dz) - width) / soft);
        if (along > length) {
            inside *= 1.0F - smooth((along - length) / BEHIND);
        }
        return 1.0F - strength * inside;
    }

    /**
     * How solid a particle at {@code x, y, z} in the world stays, 0 to 1, for a renderer that takes particles off the render thread
     * and draws them later (AsyncParticles' GPU particles): it reads this frame's cone only, and changes nothing.
     */
    public static float particleFadeAt(double x, double y, double z) {
        Cone c = cone;
        if (c == null) {
            return 1.0F;
        }
        Vector3f point = c.view().transformDirection(
                new Vector3f((float) (x - c.cameraX()), (float) (y - c.cameraY()), (float) (z - c.cameraZ())));
        return fade(c.axisX(), c.axisY(), c.axisZ(), c.length(), c.radius(), c.soft(), c.strength(), point.x, point.y, point.z);
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
    public static SceneBuffers buffers(SceneBuffers buffers) {
        if (strength <= 0.0F || !entities || STRICT_RENDERER || current == null || ShaderShadows.drawing()
                || !mayBeInside(current.getBoundingBox())) {
            return buffers;
        }
        return type -> {
            Optional<RenderType> ghost = GHOSTS.computeIfAbsent(type, ClearView::ghostOf);
            if (ghost.isEmpty()) {
                // Text, lines and glints stay solid and get the game's own buffer: Iris writes a name label straight into
                // the buffer under its consumer and crashes on any wrapper (Sodium 0.5).
                return buffers.getBuffer(type);
            }
            KeptTypes.keep(buffers, type);
            Fading fading = new Fading();
            fading.delegate = buffers.getBuffer(ghost.get());
            return fading;
        };
    }

    private static boolean strictRenderer() {
        try {
            Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter", false, ClearView.class.getClassLoader())
                    .getMethod("canUseIntrinsics");
            return false;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (NoSuchMethodException e) {
            return true;
        }
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
        if (type.format() != DefaultVertexFormat.ENTITY) {
            return Optional.empty();
        }
        // The state's texture is out of reach of code that is no render state, but every type prints it.
        Matcher matcher = TEXTURE.matcher(type.toString());
        return matcher.find() ? Optional.of(DiegesisRenderTypes.ghostEntity(Identifier.parse(matcher.group(1)))) : Optional.empty();
    }

    /**
     * How solid a particle drawn now stays, 0 to 1, from where its middle lies relative to the camera. Puts back the particle faded
     * before it first: a particle is faded only while it is drawn.
     */
    public static float particleFade(double x, double y, double z) {
        restoreParticle();
        if (strength <= 0.0F || !particles) {
            return 1.0F;
        }
        VIEW.transformDirection(POINT.set((float) x, (float) y, (float) z));
        return fade(POINT.x, POINT.y, POINT.z);
    }

    /** Notes the particle just faded, to be put back once it is drawn. */
    public static void fadedParticle(FadingParticle particle) {
        faded = particle;
    }

    /** Gives the particle faded last its own alpha and size back. */
    public static void restoreParticle() {
        FadingParticle particle = faded;
        if (particle != null) {
            faded = null;
            particle.diegesis$restore();
        }
    }

    /**
     * A world particle that can be faded and shrunk towards its middle while it is drawn. Shrinking keeps it soft for particles drawn
     * without blending (the smoke and flames of a campfire): those show whole down to a tenth of their alpha, so a fade alone made
     * them pop in at the edge of the cone.
     */
    public interface FadingParticle {
        /** Puts back the alpha and size the particle had before it was faded. */
        void diegesis$restore();
    }

    /** Passes vertices on with their alpha scaled by where they are. */
    private static final class Fading implements VertexConsumer {
        private VertexConsumer delegate;
        private float fade = 1.0F;

        private void look(float x, float y, float z) {
            VIEW.transformDirection(POINT.set(x, y, z));
            fade = ClearView.fade(POINT.x, POINT.y, POINT.z);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            look(x, y, z);
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * fade));
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY,
                float normalZ) {
            look(x, y, z);
            int alpha = Math.round((color >>> 24) * fade);
            delegate.addVertex(x, y, z, alpha << 24 | color & 0xFFFFFF, u, v, overlay, light, normalX, normalY, normalZ);
        }
    }
}
