package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.OpenLight;

/**
 * A block in the camera's way or a hidden station block is lit as open air ({@link OpenLight}). Every face lighting reads
 * this method: the game's chunk meshes (flat and smooth, through their light cache), Fabric's renderer, Sodium's light cache
 * for a block that is not opaque (both kinds are air to Sodium), and the scenes' copies of blocks. The brightness getter a
 * caller passes (the meshes' cache) is left out for such a place: what it holds there is the darkness inside the block. No
 * loader has a hook on the light they read.
 */
@Mixin(LevelRenderer.class)
abstract class LightColorMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void diegesis$lightOpenPlace(LevelRenderer.BrightnessGetter brightness, BlockAndLightGetter level, BlockState state,
            BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (OpenLight.isOpen(pos) && !state.emissiveRendering(level, pos)) {
            int sky = OpenLight.brightness(level, LightLayer.SKY, pos);
            int block = Math.max(OpenLight.brightness(level, LightLayer.BLOCK, pos), state.getLightEmission());
            cir.setReturnValue(LightCoordsUtil.pack(block, sky));
        }
    }
}
