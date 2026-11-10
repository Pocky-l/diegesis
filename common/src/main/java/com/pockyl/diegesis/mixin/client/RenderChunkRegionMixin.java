package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * The game's chunk meshes are built without the blocks in the camera's way at a station: to the mesh builder they are air, so
 * the faces of their neighbours are built too. No loader has a hook on what the mesh builder reads.
 */
@Mixin(RenderChunkRegion.class)
abstract class RenderChunkRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void diegesis$clearBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (ClearBlocks.hides(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
