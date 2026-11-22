package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * Keeps the vignette, frost, a carved pumpkin's view and the portal's swirl while the player works at a station
 * ({@code hud.keep_vignette}). The game draws them in the same group as the HUD and skips the whole group once the HUD is
 * hidden, so they are drawn here first, under everything else, while the station keeps the HUD hidden. One hook for both
 * loaders.
 */
@Mixin(Gui.class)
abstract class GuiCameraOverlaysMixin {
    @Shadow
    private void renderCameraOverlays(GuiGraphics graphics, DeltaTracker deltaTracker) {
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void diegesis$cameraOverlays(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (FocusController.get().drawsCameraOverlays()) {
            renderCameraOverlays(graphics, deltaTracker);
        }
    }
}
