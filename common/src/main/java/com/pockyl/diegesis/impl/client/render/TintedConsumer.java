package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Passes vertices on with their colour multiplied by a tint. The block model renderer applies a colour only to tinted
 * quads (grass, leaves) and always writes alpha 1, so tints and fading go through this instead.
 */
public final class TintedConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    public TintedConsumer(VertexConsumer delegate, int argb) {
        this.delegate = delegate;
        this.alpha = (argb >>> 24) / 255.0F;
        this.red = (argb >> 16 & 0xFF) / 255.0F;
        this.green = (argb >> 8 & 0xFF) / 255.0F;
        this.blue = (argb & 0xFF) / 255.0F;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int redValue, int greenValue, int blueValue, int alphaValue) {
        delegate.color(Math.round(redValue * red), Math.round(greenValue * green), Math.round(blueValue * blue), Math.round(alphaValue * alpha));
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
    public void vertex(float x, float y, float z, float redValue, float greenValue, float blueValue, float alphaValue, float u, float v,
            int overlay, int light, float normalX, float normalY, float normalZ) {
        delegate.vertex(x, y, z, redValue * red, greenValue * green, blueValue * blue, alphaValue * alpha, u, v, overlay, light, normalX,
                normalY, normalZ);
    }

    @Override
    public void defaultColor(int redValue, int greenValue, int blueValue, int alphaValue) {
        delegate.defaultColor(Math.round(redValue * red), Math.round(greenValue * green), Math.round(blueValue * blue),
                Math.round(alphaValue * alpha));
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
