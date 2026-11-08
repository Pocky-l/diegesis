package com.pockyl.diegesis.neoforge;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import com.pockyl.diegesis.impl.client.HiddenBlocks;

import java.util.List;

/** The model of a block a scene can stand in for: it draws nothing at the positions {@link HiddenBlocks} names. */
final class HideableModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> HIDDEN = new ModelProperty<>();

    HideableModel(BakedModel model) {
        super(model);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        ModelData own = super.getModelData(level, pos, state, data);
        return HiddenBlocks.isHidden(pos) ? own.derive().with(HIDDEN, true).build() : own;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
        return data.has(HIDDEN) ? List.of() : super.getQuads(state, side, random, data, renderType);
    }
}
