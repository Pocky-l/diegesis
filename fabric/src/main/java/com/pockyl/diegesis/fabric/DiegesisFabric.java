package com.pockyl.diegesis.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.pockyl.diegesis.DiegesisMod;

public final class DiegesisFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        DiegesisMod.init();
        FabricNetwork.registerServer();
        ServerTickEvents.END_SERVER_TICK.register(DiegesisMod::onServerTick);
        // The client names its channels after joining: only then can it be sent anything.
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(FabricNetwork.channel())) {
                server.execute(() -> DiegesisMod.onChannelReady(handler.getPlayer()));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DiegesisMod.onPlayerLogout(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(DiegesisMod::onServerStopped);
    }
}
