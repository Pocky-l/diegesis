package com.pockyl.diegesis.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
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
 * Moves the camera to the station while the player works at it, and gives it the station's field of view, both after vanilla
 * and other mods had their say. Neither loader has a
 * camera position event (NeoForge's ViewportEvent only turns the camera). Since Minecraft 26.x the camera is placed once a frame by
 * {@code alignWithEntity}, before the frame's culling is worked out from it. Runs after vanilla and other mods placed the camera, and only while
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
    private float fov;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vec3 position();

    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract float yRot();

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void diegesis$focusCamera(float partialTicks, CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (!focus.isActive()) {
            return;
        }
        focus.frame();
        boolean detachedView = detached;
        Vec3 eye = position();
        float eyeYaw = yRot();
        float eyePitch = xRot();
        // The turn as the game and camera mods left it: a tilt they add is in it, not in the yaw and pitch.
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
        float toRadians = (float) (Math.PI / 180.0);
        Quaternionf level = new Quaternionf().rotationYXZ((float) Math.PI - eyeYaw * toRadians, -eyePitch * toRadians, 0.0F);
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

    /**
     * The station's field of view, set where the camera stores the one it worked out, before the frame's culling and projection
     * are made from it. A hook on what {@code calculateFov} returns was not enough: FOV and zoom mods change that value too, and
     * whichever ran last decided, so at a station the player's FOV setting could come back (90 showed the station far away).
     * What the game and those mods worked out is the player's own view, where flights start and end.
     */
    @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;fov:F", opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void diegesis$focusFov(DeltaTracker deltaTracker, CallbackInfo ci) {
        FocusController focus = FocusController.get();
        if (focus.isActive()) {
            fov = (float) focus.fov(fov);
        }
    }
}
