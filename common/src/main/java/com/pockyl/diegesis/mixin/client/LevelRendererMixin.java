package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.ClearView;

/** Entities in the way of the camera dissolve while a player works at a station: no loader has a hook on their buffers. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void diegesis$beginClearView(PoseStack poseStack, float partialTick, long finishNanos, boolean renderBlockOutline, Camera camera,
            GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection, CallbackInfo callback) {
        ClearView.begin(poseStack, camera);
    }

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void diegesis$noteEntity(Entity entity, double x, double y, double z, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, CallbackInfo callback) {
        ClearView.entity(entity);
    }

    @ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource diegesis$fadeEntity(MultiBufferSource buffers) {
        return ClearView.buffers(buffers);
    }
}
