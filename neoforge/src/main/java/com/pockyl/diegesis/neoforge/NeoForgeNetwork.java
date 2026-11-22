package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.pockyl.diegesis.impl.network.DiegesisNetwork;
import com.pockyl.diegesis.impl.network.Packets;

/**
 * The library's payloads on NeoForge, optional on both sides: a vanilla client or server simply lacks them, and blocks keep
 * their vanilla behaviour there. NeoForge runs the handlers on the main thread; it takes the client's handler only from the
 * client side ({@link #registerClient}).
 */
final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DiegesisNetwork.PROTOCOL_VERSION).optional();
        registrar.playToServer(DiegesisNetwork.ToServer.TYPE, DiegesisNetwork.ToServer.STREAM_CODEC, (payload, context) -> {
            if (payload.packet() != null && context.player() instanceof ServerPlayer player) {
                DiegesisNetwork.receiveOnServer(player, payload.packet());
            }
        });
        registrar.playToClient(DiegesisNetwork.ToClient.TYPE, DiegesisNetwork.ToClient.STREAM_CODEC);
    }

    /** Client side; only called on clients. */
    static void registerClient(RegisterClientPayloadHandlersEvent event) {
        event.register(DiegesisNetwork.ToClient.TYPE, (payload, context) -> {
            if (payload.packet() != null) {
                DiegesisNetwork.receiveOnClient(payload.packet());
            }
        });
    }

    /** Whether the player's client has the channel; false for connections without a network channel (game test players). */
    static boolean canSend(ServerPlayer player) {
        return player.connection != null && player.connection.getConnection().channel() != null
                && player.connection.hasChannel(DiegesisNetwork.ToClient.TYPE);
    }

    static void send(ServerPlayer player, Packets.ServerPacket packet) {
        PacketDistributor.sendToPlayer(player, new DiegesisNetwork.ToClient(packet));
    }

    /** Client side; only called on clients. */
    static boolean serverHasChannel() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(DiegesisNetwork.ToServer.TYPE);
    }

    /** Client side; only called on clients. */
    static void sendToServer(Packets.ClientPacket packet) {
        ClientPacketDistributor.sendToServer(new DiegesisNetwork.ToServer(packet));
    }
}
