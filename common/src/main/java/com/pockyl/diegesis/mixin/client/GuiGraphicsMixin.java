package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.hud.HudSlide;

/**
 * Where the HUD draws, so that each part of it slides off to the edge nearest to it ({@link HudSlide}). Every method here is
 * one all drawing of its kind goes through: textures, fills, gradients, text, items. Nothing is changed; nothing is measured
 * outside a slide.
 */
@Mixin(GuiGraphics.class)
abstract class GuiGraphicsMixin {
    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("HEAD"), require = 0)
    private void diegesis$blit(ResourceLocation texture, int x1, int x2, int y1, int y2, int z, float u0, float u1, float v0, float v1,
            CallbackInfo ci) {
        HudSlide.include(self().pose().last().pose(), x1, y1, x2, y2);
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V", at = @At("HEAD"), require = 0)
    private void diegesis$tintedBlit(ResourceLocation texture, int x1, int x2, int y1, int y2, int z, float u0, float u1, float v0, float v1,
            float red, float green, float blue, float alpha, CallbackInfo ci) {
        HudSlide.include(self().pose().last().pose(), x1, y1, x2, y2);
    }

    @Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"), require = 0)
    private void diegesis$fill(RenderType type, int x1, int y1, int x2, int y2, int z, int color, CallbackInfo ci) {
        HudSlide.include(self().pose().last().pose(), x1, y1, x2, y2);
    }

    @Inject(method = "fillGradient(Lcom/mojang/blaze3d/vertex/VertexConsumer;IIIIIII)V", at = @At("HEAD"), require = 0)
    private void diegesis$gradient(VertexConsumer consumer, int x1, int y1, int x2, int y2, int z, int from, int to, CallbackInfo ci) {
        HudSlide.include(self().pose().last().pose(), x1, y1, x2, y2);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("RETURN"), require = 0)
    private void diegesis$text(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() > x) {
            HudSlide.include(self().pose().last().pose(), x, y, cir.getReturnValue(), y + font.lineHeight);
        }
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("RETURN"),
            require = 0)
    private void diegesis$sequence(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow,
            CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() > x) {
            HudSlide.include(self().pose().last().pose(), x, y, cir.getReturnValue(), y + font.lineHeight);
        }
    }

    // NeoForge draws text at float positions, and its int methods pass on to these; mods call them directly. Not in vanilla.
    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", at = @At("RETURN"), require = 0)
    private void diegesis$floatText(Font font, String text, float x, float y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() > x) {
            HudSlide.include(self().pose().last().pose(), x, y, cir.getReturnValue(), y + font.lineHeight);
        }
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I", at = @At("RETURN"),
            require = 0)
    private void diegesis$floatSequence(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow,
            CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() > x) {
            HudSlide.include(self().pose().last().pose(), x, y, cir.getReturnValue(), y + font.lineHeight);
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At("HEAD"), require = 0)
    private void diegesis$item(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed, int guiOffset, CallbackInfo ci) {
        HudSlide.include(self().pose().last().pose(), x, y, x + 16, y + 16);
    }

    private GuiGraphics self() {
        return (GuiGraphics) (Object) this;
    }
}
