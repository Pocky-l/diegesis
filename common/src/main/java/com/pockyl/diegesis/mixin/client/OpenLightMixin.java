package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.OpenLight;

/**
 * A block left out of the picture is lit as open air ({@link OpenLight}) wherever the game lights by it: the chunk meshes of the
 * game, of Fabric's renderer and of Embeddium, fluids, and a scene's copy of a block. No loader has a hook on the light a mesh
 * builder reads.
 */
@Mixin(LevelRenderer.class)
abstract class OpenLightMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void diegesis$lightOpenPlace(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (OpenLight.isOpen(pos)) {
            cir.setReturnValue(OpenLight.color(level, state, pos));
        }
    }
}
