package com.pockyl.diegesis.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Draws something by code at a node ({@link NodeView#setPainter}), with the scene and in it: a vanilla entity model, text on a page, items
 * lying on a table. It sorts with the scene and turns with the node.
 */
@FunctionalInterface
public interface NodePainter {
    /**
     * Called every frame while the node is visible.
     *
     * @param poseStack in the node's pixels
     * @param buffers   the scene's: what is opaque is drawn first, see-through block sheets after it, sorted
     * @param light     packed light at the node
     */
    void paint(PoseStack poseStack, MultiBufferSource buffers, int light);
}
