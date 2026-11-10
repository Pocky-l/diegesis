package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * For buffers that draw what is asked of one render type with another (a see-through twin, a shadow, an outline) or not at all:
 * the type asked for is drawn anyway, with an empty quad. Some mods hang their own drawing on a render type being drawn and put
 * an empty quad into it to make sure it is; Armourer's Workshop draws its skins so, under the block sheet types. When that quad
 * went into a twin, the skins waited for a draw that never came (nothing else draws the solid block sheet), and every skin of
 * the kind stayed invisible until a restart.
 */
public final class KeptTypes {
    private KeptTypes() {
    }

    /** Makes {@code buffers} draw {@code type}, which a caller asked for and gets drawn as something else. Call it first. */
    public static void keep(MultiBufferSource buffers, RenderType type) {
        VertexConsumer consumer = buffers.getBuffer(type);
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(0.0F, 0.0F, 0.0F, 0, 0.0F, 0.0F, OverlayTexture.NO_OVERLAY, 0, 0.0F, 1.0F, 0.0F);
        }
    }
}
