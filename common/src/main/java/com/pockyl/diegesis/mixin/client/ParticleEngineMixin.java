package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.ClearView;

/**
 * Puts back the last particle faded in the clear view once the particles are drawn, and before they tick: a renderer that draws
 * the particle itself and cancels the vanilla method skips anything after it.
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void diegesis$restoreDrawn(CallbackInfo info) {
        ClearView.restoreParticle();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void diegesis$restoreBeforeTick(CallbackInfo info) {
        ClearView.restoreParticle();
    }
}
