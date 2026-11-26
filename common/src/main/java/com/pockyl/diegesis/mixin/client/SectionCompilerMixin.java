package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * Marks the time the game builds a section's mesh, on the thread that builds it: only then does {@link RenderChunkRegionMixin}
 * read the blocks in the camera's way and a block hidden for its scene as air. A mod that reads the same region after the mesh is
 * built (coloured light, looking for the section's light sources) sees them as they are. Both methods by their full descriptor:
 * the game's own, and NeoForge's overload with the renderers other mods add to a section, which NeoForge's section task calls
 * directly (Fabric has none). By name alone Mixin takes only the first of the two, and the mark would never be set on NeoForge.
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
    private static final String ARGUMENTS = "Lnet/minecraft/core/SectionPos;"
            + "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;"
            + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;";
    private static final String RESULTS = "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";
    private static final String COMPILE = "compile(" + ARGUMENTS + ")" + RESULTS;
    private static final String NEOFORGE_COMPILE = "compile(" + ARGUMENTS + "Ljava/util/List;)" + RESULTS;

    @Inject(method = {COMPILE, NEOFORGE_COMPILE}, at = @At("HEAD"))
    private void diegesis$startMeshing(CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ClearBlocks.meshing(true);
    }

    @Inject(method = {COMPILE, NEOFORGE_COMPILE}, at = @At("RETURN"))
    private void diegesis$endMeshing(CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ClearBlocks.meshing(false);
    }
}
