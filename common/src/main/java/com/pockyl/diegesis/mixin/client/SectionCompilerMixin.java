package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * Marks the time the game builds a section's mesh, on the thread that builds it: only then does {@link RenderChunkRegionMixin}
 * read the blocks in the camera's way as air. A mod that reads the same region after the mesh is built (Gleam, looking for the
 * section's light sources) sees them as they are. Matched by name: NeoForge adds an overload with the renderers other mods add to
 * a section, which the plain one calls.
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void diegesis$startMeshing(CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ClearBlocks.meshing(true);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void diegesis$endMeshing(CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ClearBlocks.meshing(false);
    }
}
