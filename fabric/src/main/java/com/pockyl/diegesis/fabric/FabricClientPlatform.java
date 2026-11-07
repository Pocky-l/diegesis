package com.pockyl.diegesis.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.ClientPlatform;

public final class FabricClientPlatform implements ClientPlatform {
    @Override
    public boolean serverHasChannel() {
        return Minecraft.getInstance().getConnection() != null && FabricNetwork.serverHasChannel();
    }

    @Override
    public void sendToServer(Packets.ClientPacket packet) {
        if (serverHasChannel()) {
            FabricNetwork.sendToServer(packet);
        }
    }

    @Override
    public BakedModel getModel(ResourceLocation id) {
        ModelManager manager = Minecraft.getInstance().getModelManager();
        BakedModel model = manager.getModel(id);
        return model != null ? model : manager.getMissingModel();
    }
}
