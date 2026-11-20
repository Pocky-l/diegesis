package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;

/**
 * Keeps a use from reaching a station block while the camera is taken, before anything is sent to the server. The station
 * itself opens from the loader's use-block event (its last listener), so mods that take the click there go first; the use
 * packet the game sends after that event is held back by {@link ClientCommonPacketListenerMixin}.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void diegesis$guardStation(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = DiegesisClientRuntime.beforeUseBlock(player, hand, hit);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void diegesis$endUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        DiegesisClientRuntime.afterUseBlock();
    }
}
