package com.pockyl.diegesis.impl.client.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The frame's view and projection, and whether it runs the outline pass. The matrices are those the game hands to
 * {@code LevelRenderer.renderLevel} ({@code LevelRendererMixin}). Minecraft 1.21.11 keeps them nowhere a level-render hook
 * can read: Fabric's context has the view and the culling projection only, and the frame's own projection (with the view
 * bobbing) is a local of {@code GameRenderer.renderLevel}.
 */
public final class FrameView {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f CULL_PROJECTION = new Matrix4f();
    /** Whether a scene drew an outline in the last frame, so that the next one runs the game's outline pass. */
    private static boolean outlineWanted;
    /** Whether the frame being drawn runs the game's outline pass. */
    private static boolean outlinePass;

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

    /**
     * Whether a scene's outline can be drawn in this frame; asks for the outline pass of the next. Minecraft 1.21.11 runs the
     * pass, and gives the outline types their target, only in frames with a glowing entity in view; without it the outline types
     * draw into the frame itself, and the hovered object comes out as a white silhouette ({@code LevelRendererOutlineMixin}).
     */
    public static boolean drawsOutlines() {
        outlineWanted = true;
        return outlinePass;
    }

    /** Called as the level's entities are taken for a frame: whether that frame should run the outline pass for a scene. */
    public static boolean takeOutlineWanted() {
        boolean wanted = outlineWanted;
        outlineWanted = false;
        return wanted;
    }

    /** Called as the level's entities are taken for a frame: whether that frame runs the outline pass. */
    public static void setOutlinePass(boolean on) {
        outlinePass = on;
    }

    /** The frustum the game culls this frame with, placed at the camera. */
    public static Frustum cullFrustum(Vec3 camera) {
        Frustum frustum = new Frustum(VIEW, CULL_PROJECTION);
        frustum.prepare(camera.x, camera.y, camera.z);
        return frustum;
    }
}
