package com.pockyl.diegesis.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.Platform;

public final class ForgePlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean hasChannel(ServerPlayer player) {
        return player.connection != null && ForgeNetwork.present(player.connection.connection);
    }

    @Override
    public void send(ServerPlayer player, Packets.ServerPacket packet) {
        if (hasChannel(player)) {
            ForgeNetwork.send(player, packet);
        }
    }
}
