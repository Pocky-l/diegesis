package com.pockyl.diegesis.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import com.pockyl.diegesis.impl.client.render.SceneDraw;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;
import com.pockyl.diegesis.impl.network.DiegesisNetwork;
import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.ClientPlatform;

import java.util.HashMap;
import java.util.Map;

public final class NeoForgeClientPlatform implements ClientPlatform {
    /** The scene models the last resource load baked, by the id a scene names them with, and those of a load still running. */
    private static volatile Map<Identifier, StandaloneModelKey<BlockStateModel>> sceneModels = Map.of();
    private static volatile Map<Identifier, StandaloneModelKey<BlockStateModel>> loading = Map.of();

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
        StandaloneModelKey<BlockStateModel> key = sceneModels.get(id);
        BlockStateModel model = key != null ? Minecraft.getInstance().getModelManager().getStandaloneModel(key) : null;
        return model != null ? model : SceneDraw.missingModel();
    }

    /** The client's handler of the server's payload. */
    static void registerPayloadHandler(RegisterClientPayloadHandlersEvent event) {
        event.register(DiegesisNetwork.ToClient.TYPE, (payload, context) -> {
            if (payload.packet() != null) {
                DiegesisNetwork.receiveOnClient(payload.packet());
            }
        });
    }

    /**
     * Every model the scenes name is baked with the game's models on each resource load. Asked on a worker thread while the
     * load runs, when the game's resource manager already holds the new packs; the keys are used once the models are baked.
     */
    static void registerSceneModels(ModelEvent.RegisterStandalone event) {
        Map<Identifier, StandaloneModelKey<BlockStateModel>> keys = new HashMap<>();
        for (Identifier model : SceneLibrary.modelsToLoad(Minecraft.getInstance().getResourceManager())) {
            StandaloneModelKey<BlockStateModel> key = new StandaloneModelKey<>(model::toString);
            event.register(key, SimpleUnbakedStandaloneModel.blockStateModel(model));
            keys.put(model, key);
        }
        loading = Map.copyOf(keys);
    }

    static void sceneModelsBaked(ModelEvent.BakingCompleted event) {
        sceneModels = loading;
    }
}
