package com.pockyl.diegesis.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * Marks the time Embeddium (Forge) or Sodium (Fabric) builds a section's mesh, on the thread that builds it: only then does
 * {@code SodiumWorldSliceMixin} read the blocks in the camera's way as air. The mark ends where the task makes its output, before
 * it returns: a mod that reads the slice when the task returns (Gleam, looking for the section's light sources) sees the blocks as
 * they are, whatever order the two mixins run in. The descriptor names only the mod's own classes, the same in every jar.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
abstract class SodiumMeshingTaskMixin {
    private static final String EXECUTE = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
            + "Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)"
            + "Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;";

    @Inject(method = EXECUTE, at = @At("HEAD"), require = 0)
    private void diegesis$startMeshing(CallbackInfoReturnable<Object> cir) {
        ClearBlocks.meshing(true);
    }

    @Inject(method = EXECUTE, at = @At(value = "NEW", target = "me/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput"),
            require = 0)
    private void diegesis$meshBuilt(CallbackInfoReturnable<Object> cir) {
        ClearBlocks.meshing(false);
    }

    /** A task cancelled half way returns without an output. */
    @Inject(method = EXECUTE, at = @At("RETURN"), require = 0)
    private void diegesis$endMeshing(CallbackInfoReturnable<Object> cir) {
        ClearBlocks.meshing(false);
    }
}
