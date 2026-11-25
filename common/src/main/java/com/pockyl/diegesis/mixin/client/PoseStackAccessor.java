package com.pockyl.diegesis.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Deque;

/** The pose entries themselves, for the HUD slide: it takes its own entry out from under what a part left pushed on it. */
@Mixin(PoseStack.class)
public interface PoseStackAccessor {
    @Accessor("poseStack")
    Deque<PoseStack.Pose> diegesis$poses();
}
