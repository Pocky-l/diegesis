package com.pockyl.diegesis.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.Diegesis;

@Mod(Diegesis.MOD_ID)
public final class DiegesisForge {
    @SuppressWarnings("removal")
    public DiegesisForge() {
        DiegesisMod.init();
        ForgeNetwork.register();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                DiegesisMod.onServerTick(event.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DiegesisMod.onPlayerLogout(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> DiegesisMod.onServerStopped(event.getServer()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
