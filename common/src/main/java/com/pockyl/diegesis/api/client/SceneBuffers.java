package com.pockyl.diegesis.api.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.pockyl.diegesis.impl.client.render.BufferSubmits;

/**
 * Where a scene is drawn: a vertex consumer per render type, as {@code MultiBufferSource} is, and the same type on every
 * Minecraft line the library is on (26.x has no {@code MultiBufferSource}). What is
 * written here is kept and handed to the game's renderer when the scene is done, in the order the library draws a scene in
 * (solid first, then what is see-through). A lambda that hands out other consumers (tinting, lighting) is one too.
 *
 * <p>What the game draws only by submitting it, an item ({@code ItemStackRenderState.submit}), text, a model part, goes through
 * {@link #submits()}: those land in these buffers like everything else, and wrapping the buffers wraps them too.
 */
@FunctionalInterface
public interface SceneBuffers {
    VertexConsumer getBuffer(RenderType type);

    /** A submit collector that draws what it is given into these buffers, at once; outlines and shadows are left out. */
    default SubmitNodeCollector submits() {
        return new BufferSubmits(this);
    }
}
