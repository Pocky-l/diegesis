package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * Moves the camera to the station while the player works at it. Neither loader has a camera position event on 1.21.1
 * (NeoForge's ViewportEvent only turns the camera). Runs after vanilla and other mods placed the camera, and only while
 * focused, so third-person and camera mods keep working the rest of the time.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
    /** Blocks between the camera and the eyes before the player's body is drawn. */
    private static final double BODY_CLEARANCE = 0.75;

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void diegesis$focusCamera(BlockGetter level, Entity entity, boolean detachedView, boolean thirdPersonReverse, float partialTick,
            CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (!focus.isActive()) {
            return;
        }
        focus.frame();
        Vec3 eye = getPosition();
        FocusController.CameraPose pose = focus.cameraPose(eye, entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
        if (pose == null) {
            return;
        }
        setRotation(pose.yaw(), pose.pitch());
        setPosition(pose.position());
        // Detached draws the player's own body - but only once the camera is out of the head, never from inside it, and
        // never when the scene hides it.
        detached = !focus.options().hidePlayer() && (detachedView || pose.position().distanceToSqr(eye) > BODY_CLEARANCE * BODY_CLEARANCE);
    }
}
