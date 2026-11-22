package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * The first-person hands go down while the camera sets off to a station and come up again as it lands back, the way they do when
 * the held item changes: they no longer vanish in the first frame and pop back in the last. The game moves them each tick and
 * draws them between ticks, so this only holds them as low as the flight wants. (Minecraft 26.3 split this class into
 * {@code FirstPersonHandsAndItems} and its renderer; 26.1.2 still has the one.)
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

    /**
     * No hands at all once they have gone down. {@code GameRendererMixin} stops the game drawing them, but a shader pack under Iris
     * draws them itself, straight through this method, and a sliver of the held item stayed in view at the station.
     */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$noHandsAtStation(float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
            LocalPlayer player, int lightCoords, CallbackInfo ci) {
        if (FocusController.get().isHandGone()) {
            ci.cancel();
        }
    }
}
