package com.pockyl.diegesis.fabric.mixin;

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
 * Sodium's copy of the world in a Fabric jar, where Minecraft's classes have intermediary names: the blocks in the camera's way
 * at a station are air there. So is a block whose scene draws it instead: Sodium 0.5 asks a model only for its plain quads, so
 * the wrapper that leaves such a block out ({@code HideableFabricModel}) is never heard without Indium, and the block stood
 * still under the scene's moving copy. Loom remaps no descriptor of a target outside Minecraft, so this one is written as it is at
 * run time; in a development run the common mixin of the same name applies instead.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
abstract class SodiumWorldSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/class_2680;", at = @At("HEAD"), cancellable = true, require = 0)
    private void diegesis$clearBlock(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (ClearBlocks.hides(x, y, z) || HiddenBlocks.isHidden(x, y, z)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
