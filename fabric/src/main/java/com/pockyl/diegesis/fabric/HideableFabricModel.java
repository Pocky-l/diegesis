package com.pockyl.diegesis.fabric;

import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.impl.client.HiddenBlocks;

import java.util.function.Supplier;

/** The model of a block a scene can stand in for: it draws nothing at the positions {@link HiddenBlocks} names. */
final class HideableFabricModel extends ForwardingBakedModel {
    HideableFabricModel(BakedModel model) {
        wrapped = model;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
        if (!HiddenBlocks.isHidden(pos)) {
            super.emitBlockQuads(level, state, pos, random, context);
        }
    }
}
