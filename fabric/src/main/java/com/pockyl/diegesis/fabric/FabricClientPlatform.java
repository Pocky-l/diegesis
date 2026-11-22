package com.pockyl.diegesis.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.impl.client.render.SceneDraw;
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
    public BlockStateModel getModel(Identifier id) {
        ExtraModelKey<BlockStateModel> key = DiegesisFabricClient.sceneModel(id);
        BlockStateModel model = key != null ? Minecraft.getInstance().getModelManager().getModel(key) : null;
        return model != null ? model : SceneDraw.missingModel();
    }
}
