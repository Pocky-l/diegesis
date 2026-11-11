package com.pockyl.diegesis.fabric.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.hud.HudSlide;

/** Boss bars slide up with the rest of the HUD (NeoForge draws them from a layer, which slides there). */
@Mixin(BossHealthOverlay.class)
abstract class BossHealthOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void diegesis$begin(GuiGraphics graphics, CallbackInfo ci) {
        HudSlide.begin("boss", graphics);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void diegesis$end(GuiGraphics graphics, CallbackInfo ci) {
        HudSlide.end(graphics);
    }
}
