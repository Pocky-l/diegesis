package com.pockyl.diegesis.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.Diegesis;

/** The NeoForge entry point on both sides. The client part is {@link NeoForgeClient}. */
@Mod(Diegesis.MOD_ID)
public final class DiegesisNeoForge {
    public DiegesisNeoForge(IEventBus modBus) {
        DiegesisMod.init();
        modBus.addListener(NeoForgeNetwork::register);
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerTickEvent.Post.class, event -> DiegesisMod.onServerTick(event.getServer()));
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DiegesisMod.onPlayerLogout(player);
            }
        });
        bus.addListener(ServerStoppedEvent.class, event -> DiegesisMod.onServerStopped(event.getServer()));
    }
}
