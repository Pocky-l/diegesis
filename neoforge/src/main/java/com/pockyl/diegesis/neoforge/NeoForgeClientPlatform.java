package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.pockyl.diegesis.impl.client.render.SceneDraw;
import com.pockyl.diegesis.impl.network.DiegesisNetwork;
import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.ClientPlatform;

public final class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public boolean serverHasChannel() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(DiegesisNetwork.ToServer.TYPE);
    }

    @Override
    public void sendToServer(Packets.ClientPacket packet) {
        if (serverHasChannel()) {
            ClientPacketDistributor.sendToServer(new DiegesisNetwork.ToServer(packet));
        }
    }

    @Override
    public BlockStateModel getModel(Identifier id) {
        StandaloneModelKey<BlockStateModel> key = NeoForgeClient.sceneModel(id);
        BlockStateModel model = key != null ? Minecraft.getInstance().getModelManager().getStandaloneModel(key) : null;
        return model != null ? model : SceneDraw.missingModel();
    }
}
