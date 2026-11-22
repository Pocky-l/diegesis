package com.pockyl.diegesis.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.pockyl.diegesis.impl.network.DiegesisNetwork;
import com.pockyl.diegesis.impl.network.Packets;

/**
 * The library's payloads on NeoForge, optional on both sides: a vanilla client or server simply lacks them, and blocks keep
 * their vanilla behaviour there. NeoForge runs the handlers on the main thread. What only clients do is in
 * {@link NeoForgeClientPlatform}, so that a dedicated server never loads client classes through this one.
 */
final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    /** Both sides: the payload types and the server's handler. */
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DiegesisNetwork.PROTOCOL_VERSION).optional();
        registrar.playToServer(DiegesisNetwork.ToServer.TYPE, DiegesisNetwork.ToServer.STREAM_CODEC, (payload, context) -> {
            if (payload.packet() != null && context.player() instanceof ServerPlayer player) {
                DiegesisNetwork.receiveOnServer(player, payload.packet());
            }
        });
        // The client's handler is registered from client code: NeoForgeClient.
        registrar.playToClient(DiegesisNetwork.ToClient.TYPE, DiegesisNetwork.ToClient.STREAM_CODEC);
    }

    /** Whether the player's client has the channel; false for connections without a network channel (game test players). */
    static boolean canSend(ServerPlayer player) {
        return player.connection != null && player.connection.getConnection().channel() != null
                && player.connection.hasChannel(DiegesisNetwork.ToClient.TYPE);
    }

    static void send(ServerPlayer player, Packets.ServerPacket packet) {
        PacketDistributor.sendToPlayer(player, new DiegesisNetwork.ToClient(packet));
    }
}
