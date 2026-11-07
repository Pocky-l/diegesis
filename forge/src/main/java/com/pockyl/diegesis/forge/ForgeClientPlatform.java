package com.pockyl.diegesis.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.ClientPlatform;

public final class ForgeClientPlatform implements ClientPlatform {
    @Override
    public boolean serverHasChannel() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ForgeNetwork.present(connection.getConnection());
    }

    @Override
    public void sendToServer(Packets.ClientPacket packet) {
        if (serverHasChannel()) {
            ForgeNetwork.sendToServer(packet);
        }
    }

    @Override
    public BakedModel getModel(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getModel(id);
    }
}
