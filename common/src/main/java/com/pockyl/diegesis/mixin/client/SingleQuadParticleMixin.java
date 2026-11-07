package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.pockyl.diegesis.impl.client.render.ClearView;

/**
 * World particles in the way of the camera dissolve. Ahead of Sodium and Embeddium, which write the quad themselves from a mixin
 * at the same place and leave a consumer they do not know to the vanilla code.
 */
@Mixin(value = SingleQuadParticle.class, priority = 900)
abstract class SingleQuadParticleMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private VertexConsumer diegesis$fade(VertexConsumer consumer) {
        return ClearView.particles(consumer);
    }
}
