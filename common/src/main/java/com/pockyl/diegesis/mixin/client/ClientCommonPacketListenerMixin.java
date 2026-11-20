package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;

/** Holds back the use packet of a block that opened a station. */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientCommonPacketListenerMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void diegesis$holdStationUse(Packet<?> packet, CallbackInfo ci) {
        if (DiegesisClientRuntime.dropsPacket(packet)) {
            ci.cancel();
        }
    }
}
