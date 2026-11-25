package com.pockyl.diegesis.fabric;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.Platform;

import java.util.function.Supplier;

public final class FabricPlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean hasChannel(ServerPlayer player) {
        return FabricNetwork.canSend(player);
    }

    @Override
    public void send(ServerPlayer player, Packets.ServerPacket packet) {
        if (hasChannel(player)) {
            FabricNetwork.send(player, packet);
        }
    }

    @Override
    public <T> T encodeFor(ServerPlayer player, Supplier<T> encoder) {
        return PacketContext.supplyWithContext(player, encoder);
    }

    @Override
    public boolean mayUseBlock(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit)
                != InteractionResult.FAIL;
    }
}
