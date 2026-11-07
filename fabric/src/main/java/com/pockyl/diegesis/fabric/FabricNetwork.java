package com.pockyl.diegesis.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.impl.network.DiegesisNetwork;
import com.pockyl.diegesis.impl.network.Packets;

/** The library's channel on Fabric; optional on both sides (a side without it simply cannot send on it). */
final class FabricNetwork {
    private FabricNetwork() {
    }

    static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(DiegesisNetwork.CHANNEL, (server, player, handler, buf, sender) -> {
            Packets.ClientPacket packet;
            try {
                packet = Packets.readClient(buf);
            } catch (RuntimeException e) {
                DiegesisMod.LOGGER.warn("Dropped a malformed Diegesis message from {}", player.getGameProfile().getName(), e);
                return;
            }
            server.execute(() -> DiegesisNetwork.receiveOnServer(player, packet));
        });
    }

    /** Client side; only called on clients. */
    static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(DiegesisNetwork.CHANNEL, (client, handler, buf, sender) -> {
            Packets.ServerPacket packet = Packets.readServer(buf);
            client.execute(() -> DiegesisNetwork.receiveOnClient(packet));
        });
    }

    static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, DiegesisNetwork.CHANNEL);
    }

    static void send(ServerPlayer player, Packets.ServerPacket packet) {
        ServerPlayNetworking.send(player, DiegesisNetwork.CHANNEL, DiegesisNetwork.encode(packet));
    }

    static boolean serverHasChannel() {
        return ClientPlayNetworking.canSend(DiegesisNetwork.CHANNEL);
    }

    static void sendToServer(Packets.ClientPacket packet) {
        ClientPlayNetworking.send(DiegesisNetwork.CHANNEL, DiegesisNetwork.encode(packet));
    }
}
