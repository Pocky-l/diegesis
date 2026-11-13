package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.ClearView;

/**
 * World particles in the way of the camera dissolve: the particle itself is faded and shrunk towards its middle while it is
 * drawn, and put back afterwards ({@link ClearView#restoreParticle()}). Its vertices are left alone, because Sodium,
 * Embeddium and Rubidium write a particle's quad themselves: the older builds (Sodium up to 0.5.11, Rubidium, Embeddium
 * 0.1) crash on a vertex consumer that is not theirs, the newer ones fall back to a slow path and log it. Ahead of their
 * own injection at the same place, so their quad comes out faded too.
 */
@Mixin(value = SingleQuadParticle.class, priority = 900)
abstract class SingleQuadParticleMixin extends Particle implements ClearView.FadingParticle {
    @Shadow
    protected float quadSize;

    @Unique
    private float diegesis$alpha;
    @Unique
    private float diegesis$quadSize;

    protected SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void diegesis$fade(VertexConsumer consumer, Camera camera, float partialTicks, CallbackInfo info) {
        Vec3 eye = camera.getPosition();
        float fade = ClearView.particleFade(Mth.lerp(partialTicks, xo, x) - eye.x, Mth.lerp(partialTicks, yo, y) - eye.y,
                Mth.lerp(partialTicks, zo, z) - eye.z);
        if (fade < 1.0F) {
            diegesis$alpha = alpha;
            diegesis$quadSize = quadSize;
            alpha *= fade;
            quadSize *= fade;
            ClearView.fadedParticle(this);
        }
    }

    @Override
    public void diegesis$restore() {
        alpha = diegesis$alpha;
        quadSize = diegesis$quadSize;
    }
}
