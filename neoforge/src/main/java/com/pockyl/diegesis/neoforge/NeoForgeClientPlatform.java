package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import com.pockyl.diegesis.impl.client.render.SceneDraw;
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
    public BlockStateModel getModel(Identifier id) {
        StandaloneModelKey<BlockStateModel> key = NeoForgeClient.sceneModel(id);
        BlockStateModel model = key != null ? Minecraft.getInstance().getModelManager().getStandaloneModel(key) : null;
        return model != null ? model : SceneDraw.missingModel();
    }
}
