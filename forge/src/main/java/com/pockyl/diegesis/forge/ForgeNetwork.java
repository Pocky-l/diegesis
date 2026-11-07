package com.pockyl.diegesis.forge;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.pockyl.diegesis.impl.network.DiegesisNetwork;
import com.pockyl.diegesis.impl.network.Packets;

import java.util.function.Supplier;

/**
 * The library's channel on Forge, optional on both sides: a vanilla client or server simply lacks it, and blocks keep
 * their vanilla behaviour there.
 */
final class ForgeNetwork {
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(DiegesisNetwork.CHANNEL, () -> DiegesisNetwork.PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(DiegesisNetwork.PROTOCOL_VERSION), NetworkRegistry.acceptMissingOr(DiegesisNetwork.PROTOCOL_VERSION));

    private ForgeNetwork() {
    }

    static void register() {
        CHANNEL.messageBuilder(ToServer.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buf) -> message.packet().write(buf))
                .decoder(buf -> new ToServer(Packets.readClient(buf)))
                .consumerMainThread(ForgeNetwork::handleServer)
                .add();
        CHANNEL.messageBuilder(ToClient.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> message.packet().write(buf))
                .decoder(buf -> new ToClient(Packets.readServer(buf)))
                .consumerMainThread(ForgeNetwork::handleClient)
                .add();
    }

    /** Whether the other side has the channel; false for connections without a network channel (game test players). */
    static boolean present(Connection connection) {
        return connection != null && connection.channel() != null && CHANNEL.isRemotePresent(connection);
    }

    static void send(ServerPlayer player, Packets.ServerPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ToClient(packet));
    }

    static void sendToServer(Packets.ClientPacket packet) {
        CHANNEL.sendToServer(new ToServer(packet));
    }

    private static void handleServer(ToServer message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            DiegesisNetwork.receiveOnServer(sender, message.packet());
        }
    }

    private static void handleClient(ToClient message, Supplier<NetworkEvent.Context> context) {
        DiegesisNetwork.receiveOnClient(message.packet());
    }

    record ToServer(Packets.ClientPacket packet) {
    }

    record ToClient(Packets.ServerPacket packet) {
    }
}
