package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.impl.client.mesh.ItemShapes;

/** Items with a shape made from their data: the one place every view of an item gets its model from, on both loaders. */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void diegesis$shapedItem(ItemStack stack, Level level, LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
        BakedModel shaped = ItemShapes.resolve(stack, cir.getReturnValue());
        if (shaped != cir.getReturnValue()) {
            cir.setReturnValue(shaped);
        }
    }
}
