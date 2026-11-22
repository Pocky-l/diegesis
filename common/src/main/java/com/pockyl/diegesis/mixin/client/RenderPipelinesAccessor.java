package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The entity pipelines' shared parts, private in plain Minecraft: the library's pipelines are the game's with a change or two. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("ENTITY_SNIPPET")
    static RenderPipeline.Snippet diegesis$entitySnippet() {
        throw new AssertionError();
    }

    @Accessor("ENTITY_EMISSIVE_SNIPPET")
    static RenderPipeline.Snippet diegesis$entityEmissiveSnippet() {
        throw new AssertionError();
    }
}
