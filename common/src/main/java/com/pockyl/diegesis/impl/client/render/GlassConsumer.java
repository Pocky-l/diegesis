package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Passes vertices on with their alpha set by the angle they are seen at, as glass behaves: nearly nothing where a face
 * looks straight at the camera, thick at a grazing angle. Colour stays as it is, so a pale glass texture no longer
 * washes out what is behind it. Positions and normals reach this already in view space, the eye at the origin.
 */
final class GlassConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float clarity;

    GlassConsumer(VertexConsumer delegate, float clarity) {
        this.delegate = delegate;
        this.clarity = clarity;
    }

    private float keep(float px, float py, float pz, float nx, float ny, float nz) {
        float length = (float) Math.sqrt(px * px + py * py + pz * pz);
        float facing = length < 1.0e-5F ? 1.0F : Math.abs(px * nx + py * ny + pz * nz) / length;
        float grazing = 1.0F - Math.min(1.0F, facing);
        return 1.0F - clarity * (1.0F - grazing * grazing);
    }

    @Override
    public void vertex(float px, float py, float pz, float red, float green, float blue, float alphaValue, float u, float v, int overlay,
            int light, float nx, float ny, float nz) {
        delegate.vertex(px, py, pz, red, green, blue, alphaValue * keep(px, py, pz, nx, ny, nz), u, v, overlay, light, nx, ny, nz);
    }

    @Override
    public VertexConsumer vertex(double px, double py, double pz) {
        delegate.vertex(px, py, pz);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alphaValue) {
        // Built piece by piece the normal is not known yet: the clear value.
        delegate.color(red, green, blue, Math.round(alphaValue * (1.0F - clarity)));
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
    public VertexConsumer normal(float nx, float ny, float nz) {
        delegate.normal(nx, ny, nz);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alphaValue) {
        delegate.defaultColor(red, green, blue, alphaValue);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
