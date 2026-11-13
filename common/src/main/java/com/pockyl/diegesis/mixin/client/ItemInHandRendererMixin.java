package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * The first-person hands go down while the camera sets off to a station and come up again as it lands back, the way they do when
 * the held item changes: they no longer vanish in the first frame and pop back in the last. The game moves them each tick and
 * draws them between ticks, so this only holds them as low as the flight wants.
 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Shadow
    private float mainHandHeight;
    @Shadow
    private float offHandHeight;

    @Inject(method = "tick", at = @At("TAIL"))
    private void diegesis$handsAwayAtStation(CallbackInfo ci) {
        float shown = 1.0F - FocusController.get().handAway();
        mainHandHeight = Math.min(mainHandHeight, shown);
        offHandHeight = Math.min(offHandHeight, shown);
    }
}
