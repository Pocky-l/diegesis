package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.impl.client.focus.FocusController;

/**
 * Moves the camera to the station while the player works at it. Neither loader has a camera position event on 1.21.1
 * (NeoForge's ViewportEvent only turns the camera). Runs after vanilla and other mods placed the camera, and only while
 * focused, so third-person and camera mods keep working the rest of the time. Its priority makes it the last hook at the
 * end of the setup: camera mods that place the camera there too (Perspective API, under Leawind's Third Person) ran after it
 * and turned the station's view away. Their pose is where the flight starts and ends.
 */
@Mixin(value = Camera.class, priority = 2000)
abstract class CameraMixin {
    /** Blocks between the camera and the eyes before the player's body is drawn. */
    private static final double BODY_CLEARANCE = 0.75;

    @Shadow
    private boolean detached;

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    @Final
    private Vector3f forwards;

    @Shadow
    @Final
    private Vector3f up;

    @Shadow
    @Final
    private Vector3f left;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract float getYRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void diegesis$focusCamera(BlockGetter level, Entity entity, boolean detachedView, boolean thirdPersonReverse, float partialTick,
            CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (!focus.isActive()) {
            return;
        }
        focus.frame();
        Vec3 eye = getPosition();
        float eyeYaw = getYRot();
        float eyePitch = getXRot();
        // The turn as the game and camera mods left it: a tilt they add (Aeronautics Camera Sync leans the view with a Sable ship)
        // is in it, not in the yaw and pitch.
        Quaternionf eyeRotation = new Quaternionf(rotation);
        FocusController.CameraPose pose = focus.cameraPose(eye, eyeYaw, eyePitch);
        if (pose == null) {
            return;
        }
        setRotation(pose.yaw(), pose.pitch());
        keepTilt(eyeRotation, eyeYaw, eyePitch, focus.eyeShare());
        setPosition(pose.position());
        // Detached draws the player's own body - but only once the camera is out of the head, never from inside it, and
        // never when the scene hides it.
        detached = !focus.options().hidePlayer() && (detachedView || pose.position().distanceToSqr(eye) > BODY_CLEARANCE * BODY_CLEARANCE);
    }

    /**
     * The eye's tilt, as much as the flight is still (or again) at the eye: the flight in starts from the tilted view and the
     * flight back ends in it, instead of the view jumping level on leaving and tilting back after landing.
     */
    private void keepTilt(Quaternionf eyeRotation, float eyeYaw, float eyePitch, float share) {
        if (share <= 0.0F) {
            return;
        }
        Quaternionf level = new Quaternionf().rotationYXZ((float) Math.PI - eyeYaw * Mth.DEG_TO_RAD, -eyePitch * Mth.DEG_TO_RAD, 0.0F);
        Quaternionf tilt = eyeRotation.mul(level.conjugate(), new Quaternionf());
        if (Math.abs(Math.abs(tilt.w) - 1.0F) < 1.0E-6F) {
            return;
        }
        Quaternionf turn = new Quaternionf().slerp(tilt, share).mul(rotation);
        rotation.set(turn);
        forwards.set(0.0F, 0.0F, -1.0F).rotate(turn);
        up.set(0.0F, 1.0F, 0.0F).rotate(turn);
        left.set(-1.0F, 0.0F, 0.0F).rotate(turn);
    }
}
