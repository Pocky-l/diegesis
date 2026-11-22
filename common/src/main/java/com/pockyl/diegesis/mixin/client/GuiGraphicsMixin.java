package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.hud.HudSlide;

/**
 * Where the HUD draws, so that each part of it slides off to the edge nearest to it ({@link HudSlide}). Every method here is
 * one all drawing of its kind goes through: textures and sprites, fills and gradients, text, items. Nothing is changed; nothing
 * is measured outside a slide.
 */
@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsMixin {
    @Inject(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;"
            + "Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V", at = @At("HEAD"), require = 0)
    private void diegesis$blit(RenderPipeline pipeline, GpuTextureView textureView, GpuSampler sampler, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
        HudSlide.include(self().pose(), x0, y0, x1, y1);
    }

    @Inject(method = "innerFill", at = @At("HEAD"), require = 0)
    private void diegesis$fill(RenderPipeline pipeline, TextureSetup textureSetup, int x0, int y0, int x1, int y1, int color1,
            Integer color2, CallbackInfo ci) {
        HudSlide.include(self().pose(), x0, y0, x1, y1);
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"),
            require = 0)
    private void diegesis$text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        int width = font.width(text);
        if (width > 0) {
            HudSlide.include(self().pose(), x, y, x + width, y + font.lineHeight);
        }
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"), require = 0)
    private void diegesis$item(LivingEntity owner, Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        HudSlide.include(self().pose(), x, y, x + 16, y + 16);
    }

    private GuiGraphicsExtractor self() {
        return (GuiGraphicsExtractor) (Object) this;
    }
}
