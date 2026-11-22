package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.render.FrameView;

/**
 * Minecraft 1.21.11 runs the outline pass (the glowing effect's) only in a frame with a glowing entity in view, decided as the
 * entities are taken for the frame. The outline round the object under the cursor at a station needs that pass too: the frame
 * after a scene asked for it runs it as if something glowed.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererOutlineMixin {
    @Shadow
    protected abstract boolean shouldShowEntityOutlines();

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void diegesis$sceneOutline(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState renderState,
            CallbackInfo ci) {
        if (FrameView.takeOutlineWanted() && shouldShowEntityOutlines()) {
            renderState.haveGlowingEntities = true;
        }
        FrameView.setOutlinePass(renderState.haveGlowingEntities);
    }
}
