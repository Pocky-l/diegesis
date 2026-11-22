package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.FrameView;

/** Keeps the frame's view and projection for the scene pass ({@link FrameView}): one hook for both loaders. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void diegesis$frameView(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
            Matrix4f frustumMatrix, Matrix4f projectionMatrix, Matrix4f cullingProjectionMatrix, GpuBufferSlice shaderFog, Vector4f fogColor,
            boolean renderSky, CallbackInfo ci) {
        FrameView.capture(frustumMatrix, projectionMatrix, cullingProjectionMatrix);
    }
}
