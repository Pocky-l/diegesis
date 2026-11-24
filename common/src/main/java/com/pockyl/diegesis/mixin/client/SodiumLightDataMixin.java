package com.pockyl.diegesis.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.pockyl.diegesis.impl.client.render.OpenLight;

/**
 * Embeddium (Forge) keeps the block a scene stands in for in its copy of the world, and gives a solid block light 0 without
 * asking anyone: the faces of its neighbours towards it came out black. Such a block counts as not solid for its light only,
 * so the light is asked of {@code LevelRenderer.getLightColor}, which lights it as open air ({@link OpenLight}). Sodium on
 * Fabric reads such a block as air already ({@code SodiumWorldSliceMixin}). Without either mod nothing is applied.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess", remap = false)
abstract class SodiumLightDataMixin {
    @ModifyVariable(method = "compute(III)I", at = @At("STORE"), name = "fo", require = 0)
    private boolean diegesis$openWhereStoodIn(boolean fullOpaque, int x, int y, int z) {
        return fullOpaque && !OpenLight.isOpen(x, y, z);
    }
}
