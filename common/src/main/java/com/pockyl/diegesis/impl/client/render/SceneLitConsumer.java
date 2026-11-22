package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.scene.PlaceSurface;

import java.util.List;

/**
 * Passes the vertices of a model lit as a block on, each brightened by the scene's lights near it: a lantern put on a shelf
 * lights the walls and the boards around it, not only itself. A face turned away from a light takes none of it, and the
 * placing's surfaces stand between a light and what is on their other side, so the light stays on its own shelf.
 *
 * <p>A block model's faces are big (the back wall of a cupboard is one face with four corners), and light given at the corners
 * only would miss the middle: the faces are cut into pieces about {@link #PIECE} pixels wide, so that the light falls as a pool.
 */
final class SceneLitConsumer implements VertexConsumer {
    /** How far (pixels) a point must be past a surface's plane before the surface hides a light from it. */
    private static final float PAST = 0.05F;
    /** The size (pixels) of the pieces a face is cut into, and the most pieces along a side. */
    private static final float PIECE = 1.5F;
    private static final int MOST_PIECES = 10;
    /** The brightest a light map coordinate goes (level 15). */
    private static final int FULL = 240;

    private final VertexConsumer delegate;
    private final Matrix4f toStation;
    private final Matrix3f normalToStation;
    private final List<float[]> lights;
    private final List<PlaceSurface> surfaces;
    private final Vector3f position = new Vector3f();
    private final Vector3f normal = new Vector3f();

    /**
     * @param toStation       from the vertices' space (the pose stack's) into station pixels
     * @param normalToStation the same for normals
     * @param lights          x, y, z (station pixels), level, radius of each light
     */
    SceneLitConsumer(VertexConsumer delegate, Matrix4f toStation, Matrix3f normalToStation, List<float[]> lights, List<PlaceSurface> surfaces) {
        this.delegate = delegate;
        this.toStation = toStation;
        this.normalToStation = normalToStation;
        this.lights = lights;
        this.surfaces = surfaces;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY,
            float normalZ) {
        delegate.addVertex(x, y, z, color, u, v, overlay, lit(x, y, z, light, normalX, normalY, normalZ), normalX, normalY, normalZ);
    }

    /** The light map coordinate of a vertex, its block light raised by the scene's lights near it. */
    private int lit(float x, float y, float z, int light, float normalX, float normalY, float normalZ) {
        toStation.transformPosition(position.set(x, y, z));
        normalToStation.transform(normal.set(normalX, normalY, normalZ));
        if (normal.lengthSquared() > 1.0e-8F) {
            normal.normalize();
        }
        float block = light & 0xFFFF;
        for (float[] source : lights) {
            block = Math.max(block, levelFrom(source) * 16.0F);
        }
        return Math.min(FULL, Math.round(block)) | light & 0xFFFF0000;
    }

    /**
     * A face of a block model, cut into pieces, each corner of each piece lit by the scene's lights. The face comes as the chunks'
     * tesselator lights it: its shade and smooth lighting's corner shadows in each corner's colour, the world's light in its light.
     */
    @Override
    public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        Vector3f normal = pose.transformNormal(quad.direction().getUnitVec3f(), new Vector3f());
        int emission = quad.materialInfo().lightEmission();
        int overlay = instance.overlayCoords();
        float[][] corner = new float[4][];
        for (int k = 0; k < 4; k++) {
            Vector3f position = pose.pose().transformPosition(quad.position(k), new Vector3f());
            int color = instance.getColor(k);
            // A face's own light (emissive faces carry it) counts where it is brighter than the world's.
            int light = instance.getLightCoordsWithEmission(k, emission);
            long uv = quad.packedUV(k);
            corner[k] = new float[] {position.x(), position.y(), position.z(), (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F, UVPair.unpackU(uv), UVPair.unpackV(uv), light & 0xFFFF, light >>> 16 & 0xFFFF,
                    (color >>> 24) / 255.0F};
        }
        int across = pieces(corner[0], corner[1]);
        int down = pieces(corner[0], corner[3]);
        float[] point = new float[11];
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < down; j++) {
                emit(corner, (float) i / across, (float) j / down, point, overlay, normal);
                emit(corner, (float) (i + 1) / across, (float) j / down, point, overlay, normal);
                emit(corner, (float) (i + 1) / across, (float) (j + 1) / down, point, overlay, normal);
                emit(corner, (float) i / across, (float) (j + 1) / down, point, overlay, normal);
            }
        }
    }

    /** How many pieces the side from {@code a} to {@code b} is cut into. */
    private int pieces(float[] a, float[] b) {
        Vector3f from = toStation.transformPosition(new Vector3f(a[0], a[1], a[2]));
        Vector3f to = toStation.transformPosition(new Vector3f(b[0], b[1], b[2]));
        return Math.max(1, Math.min(MOST_PIECES, (int) Math.ceil(from.distance(to) / PIECE)));
    }

    /** One corner of a piece, at {@code s} along the face's first side and {@code t} along its second, mixed from its corners. */
    private void emit(float[][] corner, float s, float t, float[] point, int overlay, Vector3f normal) {
        float w0 = (1.0F - s) * (1.0F - t);
        float w1 = s * (1.0F - t);
        float w2 = s * t;
        float w3 = (1.0F - s) * t;
        for (int n = 0; n < point.length; n++) {
            point[n] = corner[0][n] * w0 + corner[1][n] * w1 + corner[2][n] * w2 + corner[3][n] * w3;
        }
        int light = Math.round(point[8]) | Math.round(point[9]) << 16;
        int color = argb(point[10]) << 24 | argb(point[3]) << 16 | argb(point[4]) << 8 | argb(point[5]);
        addVertex(point[0], point[1], point[2], color, point[6], point[7], overlay, light, normal.x(), normal.y(), normal.z());
    }

    private static int argb(float channel) {
        return Math.max(0, Math.min(255, Math.round(channel * 255.0F)));
    }

    /** The light a source gives the current vertex: soft falloff over its radius, by how squarely the face looks at it. */
    private float levelFrom(float[] source) {
        float dx = source[0] - position.x;
        float dy = source[1] - position.y;
        float dz = source[2] - position.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float near = 1.0F - distance / source[4];
        if (near <= 0.0F) {
            return 0.0F;
        }
        float facing = distance < 1.0e-4F ? 1.0F : (normal.x * dx + normal.y * dy + normal.z * dz) / distance;
        if (facing <= -0.05F || hidden(source, position, surfaces)) {
            return 0.0F;
        }
        return source[3] * near * (2.0F - near) * Math.min(1.0F, 0.45F + Math.max(0.0F, facing));
    }

    /** Whether one of {@code surfaces} lies between the source and {@code position} (station pixels). */
    static boolean hidden(float[] source, Vector3f position, List<PlaceSurface> surfaces) {
        for (PlaceSurface surface : surfaces) {
            float plane = surface.height();
            float fromLight = source[1] - plane;
            float fromVertex = position.y - plane;
            if (fromLight > PAST && fromVertex < -PAST || fromLight < -PAST && fromVertex > PAST) {
                float t = fromLight / (fromLight - fromVertex);
                float x = source[0] + (position.x - source[0]) * t;
                float z = source[2] + (position.z - source[2]) * t;
                if (x >= Math.min(surface.from().x(), surface.to().x()) && x <= Math.max(surface.from().x(), surface.to().x())
                        && z >= Math.min(surface.from().y(), surface.to().y()) && z <= Math.max(surface.from().y(), surface.to().y())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
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
}
