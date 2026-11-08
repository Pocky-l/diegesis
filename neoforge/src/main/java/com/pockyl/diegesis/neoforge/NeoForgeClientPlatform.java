package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.ClientPlatform;

public final class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public boolean serverHasChannel() {
        return NeoForgeNetwork.serverHasChannel();
    }

    @Override
    public void sendToServer(Packets.ClientPacket packet) {
        if (serverHasChannel()) {
            NeoForgeNetwork.sendToServer(packet);
        }
    }

    @Override
    public BakedModel getModel(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(id));
    }
}
