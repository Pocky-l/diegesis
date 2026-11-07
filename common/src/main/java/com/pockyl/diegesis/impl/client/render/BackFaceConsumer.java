package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Passes quads on turned to face the other way: the four vertices in reverse order (the winding decides which side is
 * culled) with their normals flipped. Drawing a model once normally and once through this shows both sides of every
 * face in the same render type, so the back faces of glass stay sorted together with the liquid inside it.
 */
final class BackFaceConsumer implements VertexConsumer {
    // Per vertex: x, y, z, red, green, blue, alpha, u, v, normal x, y, z.
    private static final int FLOATS = 12;

    private final VertexConsumer delegate;
    private final float[][] quad = new float[4][FLOATS];
    private final int[][] packed = new int[4][2];
    private int count;

    /** A vertex built through the chained calls ({@code vertex().color()...endVertex()}). */
    private final float[] building = new float[FLOATS];
    private final int[] buildingPacked = new int[2];
    private final int[] defaultColor = new int[4];
    private boolean hasDefaultColor;

    BackFaceConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light,
            float normalX, float normalY, float normalZ) {
        float[] vertex = quad[count];
        vertex[0] = x;
        vertex[1] = y;
        vertex[2] = z;
        vertex[3] = red;
        vertex[4] = green;
        vertex[5] = blue;
        vertex[6] = alpha;
        vertex[7] = u;
        vertex[8] = v;
        vertex[9] = -normalX;
        vertex[10] = -normalY;
        vertex[11] = -normalZ;
        packed[count][0] = overlay;
        packed[count][1] = light;
        if (++count < 4) {
            return;
        }
        count = 0;
        for (int i = 3; i >= 0; i--) {
            float[] out = quad[i];
            delegate.vertex(out[0], out[1], out[2], out[3], out[4], out[5], out[6], out[7], out[8], packed[i][0], packed[i][1], out[9],
                    out[10], out[11]);
        }
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        building[0] = (float) x;
        building[1] = (float) y;
        building[2] = (float) z;
        if (hasDefaultColor) {
            color(defaultColor[0], defaultColor[1], defaultColor[2], defaultColor[3]);
        }
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        building[3] = red / 255.0F;
        building[4] = green / 255.0F;
        building[5] = blue / 255.0F;
        building[6] = alpha / 255.0F;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        building[7] = u;
        building[8] = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        buildingPacked[0] = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        buildingPacked[1] = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        building[9] = x;
        building[10] = y;
        building[11] = z;
        return this;
    }

    @Override
    public void endVertex() {
        vertex(building[0], building[1], building[2], building[3], building[4], building[5], building[6], building[7], building[8],
                buildingPacked[0], buildingPacked[1], building[9], building[10], building[11]);
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        defaultColor[0] = red;
        defaultColor[1] = green;
        defaultColor[2] = blue;
        defaultColor[3] = alpha;
        hasDefaultColor = true;
    }

    @Override
    public void unsetDefaultColor() {
        hasDefaultColor = false;
    }
}
