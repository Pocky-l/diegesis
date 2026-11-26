package com.pockyl.diegesis.mixin.client;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.HiddenBlocks;
import com.pockyl.diegesis.impl.client.render.ClearBlocks;

/**
 * Sodium builds chunk meshes from its own copy of the world: the blocks in the camera's way at a station are air there too,
 * and so is a block whose scene draws it instead. As air it hides no face of the blocks around it, and Sodium lights those
 * faces through {@code LevelRenderer.getLightColor} ({@code LightColorMixin}): for a block it takes as opaque, Sodium gives
 * no light and lights a face against it from the face's own block, which left the faces of an opaque block beside a hidden
 * station black. Both only while the mesh is built ({@code SodiumMeshingTaskMixin}): what reads the slice after it (Gleam,
 * looking for light sources) sees the world as it is. Every block its mesh builder reads, by position or by coordinates, goes
 * through this method. Without Sodium the class is missing and nothing is applied. The descriptor names Mojang's classes, as
 * NeoForge and Fabric development runs do; Loom leaves a target outside Minecraft as it is, so a Fabric jar has its own copy
 * with intermediary names.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
abstract class SodiumWorldSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void diegesis$clearBlock(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if ((ClearBlocks.hides(x, y, z) || HiddenBlocks.isHidden(x, y, z)) && ClearBlocks.meshing()) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
