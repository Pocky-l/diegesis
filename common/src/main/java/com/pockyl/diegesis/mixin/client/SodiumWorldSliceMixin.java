package com.pockyl.diegesis.mixin.client;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * Embeddium (Forge) and Sodium (Fabric) build chunk meshes from their own copy of the world: the blocks in the camera's way at a
 * station are air there too. Every block their mesh builder reads, by position or by coordinates, goes through this method.
 * Without either mod the class is missing and nothing is applied. The descriptor names Mojang's classes, as Forge and Fabric
 * development runs do; Loom leaves a target outside Minecraft as it is, so a Fabric jar has its own copy with intermediary names.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
abstract class SodiumWorldSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void diegesis$clearBlock(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (ClearBlocks.hides(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
