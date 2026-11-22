package com.pockyl.diegesis.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.FrameView;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;

/**
 * The scene joins what the level submits this frame, as entities do; its batches are ordered after the entities' own. NeoForge
 * has no event with the frame's submit collector (Fabric's is {@code WorldRenderEvents.BEFORE_ENTITIES}, fired at the same spot):
 * the hook is where the level submits its entities.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void diegesis$submitScene(PoseStack poseStack, LevelRenderState renderState, SubmitNodeCollector collector, CallbackInfo ci) {
        SceneRenderer.render(collector, poseStack, FrameView.view(), FrameView.projection(),
                Minecraft.getInstance().gameRenderer.getMainCamera());
    }
}
