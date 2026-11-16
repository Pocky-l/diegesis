package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearView;

/**
 * The clear view's fade for AsyncParticles' GPU particles. Those never go through {@code SingleQuadParticle.render}
 * ({@code SingleQuadParticleMixin}): once a tick AsyncParticles reads each one's size and colour as it was and as it is, on its own
 * threads, and the GPU draws between the two every frame. Both are faded here by where the particle was and where it is, so the
 * GPU's own blend fades it in and out smoothly. Above AsyncParticles' priority, which adds these methods; without AsyncParticles
 * there is nothing to change.
 */
@Mixin(value = TextureSheetParticle.class, priority = 1100)
abstract class TextureSheetParticleGpuMixin extends SingleQuadParticle {
    protected TextureSheetParticleGpuMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    /** Called with 0 (the size as it was) and 1 (as it is). */
    @Inject(method = "asyncparticles$getQuadSize", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void diegesis$fadeSize(float partialTick, CallbackInfoReturnable<Float> cir) {
        float fade = partialTick <= 0.0F ? ClearView.particleFadeAt(xo, yo, zo)
                : partialTick >= 1.0F ? ClearView.particleFadeAt(x, y, z)
                : ClearView.particleFadeAt(xo + (x - xo) * partialTick, yo + (y - yo) * partialTick, zo + (z - zo) * partialTick);
        if (fade < 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() * fade);
        }
    }

    /** The colour as it was. */
    @Inject(method = "asyncparticles$getOColor", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void diegesis$fadeOldColor(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(diegesis$faded(cir.getReturnValueI(), ClearView.particleFadeAt(xo, yo, zo)));
    }

    /** The colour as it is. */
    @Inject(method = "asyncparticles$getColor", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void diegesis$fadeColor(int oldColor, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(diegesis$faded(cir.getReturnValueI(), ClearView.particleFadeAt(x, y, z)));
    }

    /** The colour with its alpha (the top byte) times {@code fade}. */
    @Unique
    private static int diegesis$faded(int argb, float fade) {
        if (fade >= 1.0F) {
            return argb;
        }
        int alpha = Math.round((argb >>> 24) * Math.max(0.0F, fade));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
