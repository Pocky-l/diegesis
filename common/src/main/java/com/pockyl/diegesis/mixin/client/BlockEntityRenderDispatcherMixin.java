package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * A block left out of the picture for the camera at a station is left out whole: its chunk mesh goes, and so does what its block
 * entity draws (the food on a board, a chest's lid), which would otherwise hang in the air before the camera. The game takes a
 * block entity's render state here before it draws it; without one it draws nothing.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void diegesis$clearBlockEntity(BlockEntity blockEntity, float partialTicks, ModelFeatureRenderer.CrumblingOverlay breakProgress,
            boolean isGloballyRendered, CallbackInfoReturnable<BlockEntityRenderState> callback) {
        if (ClearBlocks.hides(blockEntity.getBlockPos())) {
            callback.setReturnValue(null);
        }
    }
}
