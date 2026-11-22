package com.pockyl.diegesis.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import com.pockyl.diegesis.DiegesisMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DiegesisFabric implements ModInitializer {
    /**
     * Connections whose client named our channel, served on the next server tick. Channels the client named while logging in are
     * announced from inside the connection's constructor, before Fabric can tell what may be sent to it: asking then crashes the
     * join.
     */
    private static final Set<ServerGamePacketListenerImpl> READY = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        DiegesisMod.init();
        FabricNetwork.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!READY.isEmpty()) {
                List<ServerGamePacketListenerImpl> ready = new ArrayList<>(READY);
                READY.removeAll(ready);
                for (ServerGamePacketListenerImpl handler : ready) {
                    if (handler.getPlayer().connection == handler && !handler.getPlayer().hasDisconnected()) {
                        DiegesisMod.onChannelReady(handler.getPlayer());
                    }
                }
            }
            DiegesisMod.onServerTick(server);
        });
        // The client names its channels after joining: only then can it be sent anything.
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(FabricNetwork.channel())) {
                READY.add(handler);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            READY.remove(handler);
            DiegesisMod.onPlayerLogout(handler.getPlayer());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            READY.clear();
            DiegesisMod.onServerStopped(server);
        });
    }
}
