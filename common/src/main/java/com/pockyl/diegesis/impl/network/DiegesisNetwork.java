package com.pockyl.diegesis.impl.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.impl.server.StationManager;

import java.util.function.Consumer;

/**
 * Entry and exit of every message: the loaders move bytes on {@link #CHANNEL} and call {@link #receiveOnServer} /
 * {@link #receiveOnClient} on the main thread.
 */
public final class DiegesisNetwork {
    public static final ResourceLocation CHANNEL = DiegesisMod.id("main");
    public static final String PROTOCOL_VERSION = "2";

    /** Set by the client part of the library; a dedicated server never loads client classes through this. */
    private static volatile Consumer<Packets.ServerPacket> clientHandler = packet -> {
    };

    private DiegesisNetwork() {
    }

    public static void setClientHandler(Consumer<Packets.ServerPacket> handler) {
        clientHandler = handler;
    }

    public static void receiveOnServer(ServerPlayer player, Packets.ClientPacket packet) {
        StationManager.get(player.server).handle(player, packet);
    }

    public static void receiveOnClient(Packets.ServerPacket packet) {
        clientHandler.accept(packet);
    }

    public static FriendlyByteBuf encode(Packets.Packet packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        return buf;
    }

    /** Encodes into a fresh byte array, e.g. a state shared by several messages. */
    public static byte[] toBytes(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return bytes;
    }

    public static FriendlyByteBuf wrap(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }
}
