package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * No first-person hand in the picture while the player works at a station: the camera is somewhere else, and with a hidden body it
 * stays undetached. (The scene's field of view is set where the camera works it out, {@code CameraMixin}.)
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    /** Nor the outline of the block the player looked at: it stayed while the HUD slid away. */
    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void diegesis$noOutlineAtStation(CallbackInfoReturnable<Boolean> cir) {
        if (FocusController.get().isActive()) {
            cir.setReturnValue(false);
        }
    }

    /** Once it has gone down ({@code ItemInHandRendererMixin}). */
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void diegesis$noHandAtStation(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix,
            CallbackInfo ci) {
        if (FocusController.get().isHandGone()) {
            ci.cancel();
        }
    }
}
