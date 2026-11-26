package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.HiddenBlocks;
import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * The game's chunk meshes are built without the blocks in the camera's way at a station, and without the blocks a scene stands in
 * for ({@code StationType.Builder.hidesBlock}): to the mesh builder they are air, so the faces of their neighbours are built too
 * (a door on its way shut over a block whose state already says shut no longer shows the caves through the floor under it). The
 * mesh builder reads every block of a section here, so this is also where a block shown again is first built with its own model.
 * No loader has a hook on what the mesh builder reads. Only while the mesh is built ({@code SectionCompilerMixin}): what reads the
 * region after it sees the world as it is.
 */
@Mixin(RenderSectionRegion.class)
abstract class RenderChunkRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void diegesis$clearBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (ClearBlocks.hides(pos) || HiddenBlocks.isHidden(pos)) {
            if (ClearBlocks.meshing()) {
                cir.setReturnValue(Blocks.AIR.defaultBlockState());
            }
        } else {
            HiddenBlocks.built(pos);
        }
    }
}
