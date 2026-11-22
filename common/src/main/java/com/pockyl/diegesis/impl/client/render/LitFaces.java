package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3fc;

/**
 * Passes on only the quads that face the light: what a flattened shadow is drawn from. A closed model's lit faces cover its shadow
 * once; all its faces cover it twice, its front and back a plate's thickness apart, and the see-through shadow came out darker
 * along the way they overlap (stripes and bands on a carried item's shadow). Face culling cannot sort this out: once flattened,
 * the faces lie in one plane and the GPU draws both sides.
 */
final class LitFaces implements VertexConsumer {
    private static final int FLOATS = 8;
    private final VertexConsumer delegate;
    private final Vector3fc light;
    /** The quad being gathered: x y z u v and the normal's x y z per vertex; colour, overlay and light in {@link #packed}. */
    private final float[] vertices = new float[4 * FLOATS];
    private final int[] packed = new int[4 * 3];
    private int count;
    // A vertex given piece by piece: it is complete with its normal, the last element of the entity format, or when the next begins.
    private boolean pending;
    private float x;
    private float y;
    private float z;
    private int color = 0xFFFFFFFF;
    private float u;
    private float v;
    private int overlay;
    private int lightmap;

    LitFaces(VertexConsumer delegate, Vector3fc light) {
        this.delegate = delegate;
        this.light = light;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY,
            float normalZ) {
        finishPending(0.0F, 1.0F, 0.0F);
        int at = count * FLOATS;
        vertices[at] = x;
        vertices[at + 1] = y;
        vertices[at + 2] = z;
        vertices[at + 3] = u;
        vertices[at + 4] = v;
        vertices[at + 5] = normalX;
        vertices[at + 6] = normalY;
        vertices[at + 7] = normalZ;
        packed[count * 3] = color;
        packed[count * 3 + 1] = overlay;
        packed[count * 3 + 2] = light;
        if (++count == 4) {
            flush();
        }
    }

    private void flush() {
        count = 0;
        float facing = 0.0F;
        for (int i = 0; i < 4; i++) {
            int at = i * FLOATS;
            facing += vertices[at + 5] * light.x() + vertices[at + 6] * light.y() + vertices[at + 7] * light.z();
        }
        if (facing <= 0.0F) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            int at = i * FLOATS;
            delegate.addVertex(vertices[at], vertices[at + 1], vertices[at + 2], packed[i * 3], vertices[at + 3], vertices[at + 4],
                    packed[i * 3 + 1], packed[i * 3 + 2], vertices[at + 5], vertices[at + 6], vertices[at + 7]);
        }
    }

    /** Ends a vertex given piece by piece, with {@code normal} when none was given. */
    private void finishPending(float normalX, float normalY, float normalZ) {
        if (pending) {
            pending = false;
            addVertex(x, y, z, color, u, v, overlay, lightmap, normalX, normalY, normalZ);
        }
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        finishPending(0.0F, 1.0F, 0.0F);
        pending = true;
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        color = alpha << 24 | red << 16 | green << 8 | blue;
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        overlay = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        lightmap = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        finishPending(x, y, z);
        return this;
    }
}
