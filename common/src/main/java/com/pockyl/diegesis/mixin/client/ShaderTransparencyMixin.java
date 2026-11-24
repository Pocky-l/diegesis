package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.SmoothPass;

/**
 * No Fabulous layers while smooth painters are drawn over the finished frame ({@link SmoothPass#drawingOverTheFrame()}): the
 * item renderer then picks the types Fancy graphics draws with, and the render states that would bind one of the level's
 * layer targets (item entities, translucent, particles, weather, clouds) bind nothing, so everything lands in the pass's own
 * target. Every one of them asks this method, and no loader lets a caller choose for them.
 */
@Mixin(Minecraft.class)
abstract class ShaderTransparencyMixin {
    @Inject(method = "useShaderTransparency", at = @At("HEAD"), cancellable = true)
    private static void diegesis$noLayersOverTheFrame(CallbackInfoReturnable<Boolean> cir) {
        if (SmoothPass.drawingOverTheFrame() && RenderSystem.isOnRenderThread()) {
            cir.setReturnValue(false);
        }
    }
}
