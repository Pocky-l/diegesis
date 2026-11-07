package com.pockyl.diegesis.fabric.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * While the player works at a station its interface replaces the HUD (when the scene says so). Fabric has no event that
 * hides HUD parts (Forge's overlay event does it there); the chat is hidden by a common mixin.
 */
@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = {"renderHotbar", "renderCrosshair", "renderEffects", "renderPlayerHealth", "renderVehicleHealth",
            "renderJumpMeter", "renderExperienceBar", "renderSelectedItemName"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$hideHud(CallbackInfo ci) {
        if (FocusController.get().isActive() && FocusController.get().options().hideHud()) {
            ci.cancel();
        }
    }
}
