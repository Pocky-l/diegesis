package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * The scene's field of view while the player works at a station (Fabric has no FOV event; one hook for both loaders), and no
 * first-person hand in the picture: the camera is somewhere else, and with a hidden body it stays undetached.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    /**
     * The station's field of view, set where {@code renderLevel} stores the one {@code getFov} worked out (its second float, after
     * the partial tick), before the projection and the culling frustum are made from it. A hook on what {@code getFov} returns
     * was not enough: FOV and zoom mods change that value too, and whichever ran last decided, so at a station the player's FOV
     * setting could come back and show the station far away. What the game and those mods worked out is the player's own view,
     * where flights start and end.
     */
    @ModifyVariable(method = "renderLevel", at = @At(value = "STORE", ordinal = 0), ordinal = 1)
    private float diegesis$focusFov(float fov) {
        FocusController focus = FocusController.get();
        return focus.isActive() ? (float) focus.fov(fov) : fov;
    }

    /** Nor the outline of the block the player looked at: it stayed while the HUD slid away. */
    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void diegesis$noOutlineAtStation(CallbackInfoReturnable<Boolean> cir) {
        if (FocusController.get().isActive()) {
            cir.setReturnValue(false);
        }
    }

    /** Once it has gone down ({@code ItemInHandRendererMixin}). */
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void diegesis$noHandAtStation(float partialTick, boolean sleeping, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (FocusController.get().isHandGone()) {
            ci.cancel();
        }
    }
}
