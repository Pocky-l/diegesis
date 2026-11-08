package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * The scene's field of view while the player works at a station (Fabric has no FOV event; one hook for both loaders), and no
 * first-person hand in the picture: the camera is somewhere else, and with a hidden body it stays undetached.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void diegesis$focusFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        FocusController focus = FocusController.get();
        if (useFovSetting && focus.isActive()) {
            cir.setReturnValue(focus.fov(cir.getReturnValue()));
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void diegesis$noHandAtStation(Camera camera, float partialTick, Matrix4f projection, CallbackInfo ci) {
        if (FocusController.get().isActive()) {
            ci.cancel();
        }
    }
}
