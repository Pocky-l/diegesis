package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.hud.HudSlide;

/**
 * Slides the chat off to the left and hides it while the player works at a station whose scene asks for it. One hook for both
 * loaders: NeoForge draws the chat from a layer, Fabric from the HUD, both through this method.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @Unique
    private boolean diegesis$slides;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void diegesis$hideChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (focus.isActive() && focus.options().hideChat() && HudSlide.gone()) {
            ci.cancel();
            return;
        }
        // A scene that keeps the chat leaves it where it is.
        diegesis$slides = !focus.isActive() || focus.options().hideChat();
        if (diegesis$slides) {
            HudSlide.begin(HudSlide.CHAT, graphics);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void diegesis$chatDrawn(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (diegesis$slides) {
            HudSlide.end(graphics);
        }
    }
}
