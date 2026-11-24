package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.SmoothPass;

/**
 * Smooth painters are drawn as Fancy draws, under Fabulous too ({@link SmoothPass#drawsFancy}): the item renderer then picks the
 * types that draw into the target bound, and the render states that would bind a layer of the level's frame bind nothing. No
 * loader has a hook on either.
 */
@Mixin(Minecraft.class)
abstract class ShaderTransparencyMixin {
    @Inject(method = "useShaderTransparency", at = @At("HEAD"), cancellable = true)
    private static void diegesis$fancyForSmoothPainters(CallbackInfoReturnable<Boolean> cir) {
        if (SmoothPass.drawsFancy()) {
            cir.setReturnValue(false);
        }
    }
}
