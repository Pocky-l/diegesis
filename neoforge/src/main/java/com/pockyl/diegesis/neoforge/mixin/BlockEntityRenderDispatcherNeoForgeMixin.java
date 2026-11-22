package com.pockyl.diegesis.neoforge.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * NeoForge's form of the common {@code BlockEntityRenderDispatcherMixin}: NeoForge adds a {@code tryExtractRenderState} that also
 * takes the cull frustum, and the level takes block entities' states through that one; vanilla's, which the common mixin finds
 * first, only hands over to it.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherNeoForgeMixin {
    @Inject(method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;ZLnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"), cancellable = true)
    private void diegesis$clearBlockEntity(BlockEntity blockEntity, float partialTicks, ModelFeatureRenderer.CrumblingOverlay breakProgress,
            boolean isGloballyRendered, Frustum frustum, CallbackInfoReturnable<BlockEntityRenderState> callback) {
        if (ClearBlocks.hides(blockEntity.getBlockPos())) {
            callback.setReturnValue(null);
        }
    }
}
