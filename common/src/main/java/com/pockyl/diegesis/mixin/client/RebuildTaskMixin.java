package com.pockyl.diegesis.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * Marks the time the game builds a section's mesh, on the thread that builds it: only then does {@link RenderChunkRegionMixin}
 * read the blocks in the camera's way as air. A mod that reads the same region after the mesh is built (a coloured-light mod
 * looking for light sources) sees them as they are.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
abstract class RebuildTaskMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void diegesis$startMeshing(CallbackInfoReturnable<Object> cir) {
        ClearBlocks.meshing(true);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void diegesis$endMeshing(CallbackInfoReturnable<Object> cir) {
        ClearBlocks.meshing(false);
    }
}
