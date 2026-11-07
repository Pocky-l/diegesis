package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The entity outline effect, run by the library for the outline round the object under the cursor. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("entityEffect")
    PostChain diegesis$entityEffect();
}
