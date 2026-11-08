package com.pockyl.diegesis.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.Platform;

public final class NeoForgePlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean hasChannel(ServerPlayer player) {
        return NeoForgeNetwork.canSend(player);
    }

    @Override
    public void send(ServerPlayer player, Packets.ServerPacket packet) {
        if (hasChannel(player)) {
            NeoForgeNetwork.send(player, packet);
        }
    }
}
