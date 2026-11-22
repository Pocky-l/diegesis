package com.pockyl.diegesis.impl.client.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The frame's view and projection, as the game hands them to {@code LevelRenderer.renderLevel} ({@code LevelRendererMixin}).
 * Minecraft 1.21.11 keeps them nowhere a level-render hook can read: Fabric's context has the view and the culling projection
 * only, and the frame's own projection (with the view bobbing) is a local of {@code GameRenderer.renderLevel}.
 */
public final class FrameView {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f CULL_PROJECTION = new Matrix4f();

    private FrameView() {
    }

    /** Keeps this frame's matrices (render thread). */
    public static void capture(Matrix4f view, Matrix4f projection, Matrix4f cullProjection) {
        VIEW.set(view);
        PROJECTION.set(projection);
        CULL_PROJECTION.set(cullProjection);
    }

    /** The camera's rotation, as the level is drawn with it. */
    public static Matrix4f view() {
        return VIEW;
    }

    /** The frame's projection. */
    public static Matrix4f projection() {
        return PROJECTION;
    }

    /** The frustum the game culls this frame with, placed at the camera. */
    public static Frustum cullFrustum(Vec3 camera) {
        Frustum frustum = new Frustum(VIEW, CULL_PROJECTION);
        frustum.prepare(camera.x, camera.y, camera.z);
        return frustum;
    }
}
