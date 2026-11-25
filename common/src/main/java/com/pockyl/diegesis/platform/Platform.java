package com.pockyl.diegesis.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.diegesis.impl.network.Packets;

import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * What the common code needs from the mod loader, on both sides. Each loader project provides one implementation,
 * listed in its {@code META-INF/services}.
 */
public interface Platform {
    Platform INSTANCE = load(Platform.class);

    boolean isModLoaded(String modId);

    /** Whether the player's client has the library's channel. */
    boolean hasChannel(ServerPlayer player);

    void send(ServerPlayer player, Packets.ServerPacket packet);

    /**
     * Runs {@code encoder}, which writes part of a message to {@code player} ahead of the packet, as that player's
     * connection would run it: on Fabric, mods that change items per connection (Polymer) read the packet context.
     */
    default <T> T encodeFor(ServerPlayer player, Supplier<T> encoder) {
        return encoder.get();
    }

    /**
     * Whether the loader's block-use event lets the player use the block at {@code pos}, as a right-click on it would: claim and
     * protection mods deny it there. Opening a station replaces that right-click, so it asks the same question first.
     */
    boolean mayUseBlock(ServerPlayer player, BlockPos pos);

    static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, type.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type.getName() + " for this mod loader"));
    }
}
