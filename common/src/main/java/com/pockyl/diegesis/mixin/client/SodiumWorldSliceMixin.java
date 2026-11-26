package com.pockyl.diegesis.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;
import com.pockyl.diegesis.impl.client.render.OpenLight;

/**
 * Embeddium (Forge) and Sodium (Fabric) build chunk meshes from their own copy of the world: the blocks in the camera's way at a
 * station are air there too, while the mesh is built ({@code SodiumMeshingTaskMixin}); what reads the slice after it sees the
 * world as it is. Every block their mesh builder reads, by position or by coordinates, goes through this method.
 * Without either mod the class is missing and nothing is applied. The descriptor names Mojang's classes, as Forge and Fabric
 * development runs do; Loom leaves a target outside Minecraft as it is, so a Fabric jar has its own copy with intermediary names.
 * Sodium reads the light from here too: such a block is lit as open air ({@link OpenLight}). Embeddium asks
 * {@code LevelRenderer.getLightColor} instead, where {@code OpenLightMixin} does the same.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
abstract class SodiumWorldSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void diegesis$clearBlock(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (ClearBlocks.hides(x, y, z) && ClearBlocks.meshing()) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void diegesis$lightOpenPlace(LightLayer layer, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (OpenLight.isOpen(pos)) {
            cir.setReturnValue(OpenLight.brightness((BlockAndTintGetter) (Object) this, layer, pos));
        }
    }
}
