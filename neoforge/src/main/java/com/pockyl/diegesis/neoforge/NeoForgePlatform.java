package com.pockyl.diegesis.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;

import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.Platform;

public final class NeoForgePlatform implements Platform {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean hasChannel(ServerPlayer player) {
        return NeoForgeNetwork.canSend(player);
    }

    @Override
    public void send(ServerPlayer player, Packets.ServerPacket packet) {
        if (hasChannel(player)) {
            NeoForgeNetwork.send(player, packet);
        }
    }

    @Override
    public boolean mayUseBlock(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return !CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit).isCanceled();
    }
}
