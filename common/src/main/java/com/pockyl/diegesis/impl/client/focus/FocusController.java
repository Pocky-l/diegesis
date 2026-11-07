package com.pockyl.diegesis.impl.client.focus;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.CameraRig;
import com.pockyl.diegesis.api.scene.CameraShot;
import com.pockyl.diegesis.api.scene.InterfaceOptions;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.station.Authority;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.anim.Easing;
import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.ClientPlatform;

/**
 * The local player's work at a station: flies the camera to the station's rig and back, hides the HUD, keeps the
 * focus screen open and decides when interaction is allowed (after the server accepted, for server stations).
 *
 * <p>States: idle, entering (camera flying in), focused, exiting (flying back). Progress runs on real time, so the
 * flight is smooth at any frame rate and tick lag.
 */
public final class FocusController {
    private static final FocusController INSTANCE = new FocusController();
    /** Height of the little arc the camera flies in, in blocks at the middle of the flight. */
    private static final double ARC_HEIGHT = 0.12;
    /** How fast the camera settles on another seat, per second. */
    private static final float SEAT_GLIDE = 8.0F;

    private ClientStation station;
    private Phase phase = Phase.IDLE;
    private boolean confirmed;
    private float progress;
    private long lastFrameNanos;
    private boolean savedHideGui;
    private float startYaw;
    private float startPitch;
    /** Where the camera's base shot is now: the seat's, or the rig's; glides when the server gives another seat. */
    private final Vector3f basePosition = new Vector3f();
    private final Vector3f baseTarget = new Vector3f();

    private FocusController() {
    }

    public static FocusController get() {
        return INSTANCE;
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    /** Whether the camera is flying back after the player left. */
    public boolean isExiting() {
        return phase == Phase.EXITING;
    }

    /** Whether the player may interact: the camera arrived and the server (if any) accepted. */
    public boolean isInteractive() {
        return phase == Phase.FOCUSED && confirmed;
    }

    public ClientStation station() {
        return station;
    }

    /** Starts working at a station; {@code awaitServer} for server stations until the server answers. */
    public void begin(ClientStation target, boolean awaitServer) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (phase == Phase.EXITING && station != null && station.pos().equals(target.pos())) {
            // Used again while the camera was still flying back: turn round from where it is. The HUD state saved at
            // the first entry stays the one to restore.
            station = target;
            confirmed = !awaitServer;
            phase = Phase.ENTERING;
            lastFrameNanos = Util.getNanos();
            if (confirmed) {
                station.setFocusedInternal(true);
            }
            minecraft.setScreen(new FocusScreen());
            return;
        }
        if (phase != Phase.IDLE) {
            return;
        }
        station = target;
        confirmed = !awaitServer;
        phase = Phase.ENTERING;
        progress = 0.0F;
        lastFrameNanos = Util.getNanos();
        startYaw = player.getYRot();
        startPitch = player.getXRot();
        savedHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
        FocusInput.get().look().reset();
        snapBaseShot();
        lookAtStation(player);
        if (confirmed) {
            station.setFocusedInternal(true);
        }
        minecraft.setScreen(new FocusScreen());
    }

    private void snapBaseShot() {
        CameraRig rig = rig();
        CameraShot seat = seatShot(rig);
        basePosition.set(seat != null ? seat.position() : rig.position());
        baseTarget.set(seat != null ? seat.target() : rig.target());
    }

    /** The view from the local player's seat, or null. */
    private CameraShot seatShot(CameraRig rig) {
        return station == null ? null : station.seat().map(seat -> rig.seats().get(seat)).orElse(null);
    }

    /** The server accepted the player at {@code target}. */
    public void confirm(ClientStation target) {
        if (station != null && station.pos().equals(target.pos()) && phase != Phase.EXITING && phase != Phase.IDLE) {
            station = target;
            if (!confirmed) {
                confirmed = true;
                station.setFocusedInternal(true);
            }
        }
    }

    /** The server refused. */
    public void abort(BlockPos pos) {
        if (station != null && station.pos().equals(pos)) {
            exit(false);
        }
    }

    /** The server ended the player's use. */
    public void serverClosed(BlockPos pos) {
        if (station != null && station.pos().equals(pos) && phase != Phase.IDLE) {
            exit(false);
        }
    }

    /** The player leaves (Escape, inventory key, another screen). */
    public void requestExit() {
        exit(true);
    }

    private void exit(boolean tellServer) {
        if (phase == Phase.IDLE || phase == Phase.EXITING) {
            return;
        }
        if (tellServer && station.type().authority() == Authority.SERVER) {
            ClientPlatform.INSTANCE.sendToServer(new Packets.Close());
        }
        FocusInput.get().cancel();
        if (confirmed) {
            station.setFocusedInternal(false);
        }
        phase = Phase.EXITING;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FocusScreen) {
            minecraft.setScreen(null);
        }
    }

    /** Drops everything at once (level change, disconnect). */
    public void reset() {
        if (phase != Phase.IDLE) {
            Minecraft.getInstance().options.hideGui = savedHideGui;
        }
        FocusInput.get().cancel();
        FocusInput.get().look().reset();
        phase = Phase.IDLE;
        station = null;
        confirmed = false;
    }

    /** Advances the camera flight; once per frame, before the camera is set up. */
    public void frame() {
        if (phase == Phase.IDLE) {
            return;
        }
        long now = Util.getNanos();
        float dt = Math.min((now - lastFrameNanos) / 1.0e9F, 0.1F);
        lastFrameNanos = now;
        FocusInput.get().look().advance(dt);
        CameraRig currentRig = rig();
        CameraShot seat = seatShot(currentRig);
        float settle = 1.0F - (float) Math.exp(-dt * SEAT_GLIDE);
        basePosition.lerp(seat != null ? seat.position() : currentRig.position(), settle);
        baseTarget.lerp(seat != null ? seat.target() : currentRig.target(), settle);
        float duration = Math.max(currentRig.transition(), 0.01F);
        Minecraft minecraft = Minecraft.getInstance();
        if (phase == Phase.ENTERING) {
            progress = Math.min(1.0F, progress + dt / duration);
            if (progress >= 1.0F) {
                phase = Phase.FOCUSED;
            }
        } else if (phase == Phase.EXITING) {
            progress = Math.max(0.0F, progress - dt / duration);
            if (progress <= 0.0F) {
                minecraft.options.hideGui = savedHideGui;
                phase = Phase.IDLE;
                station = null;
                return;
            }
        }
        if ((phase == Phase.FOCUSED || phase == Phase.ENTERING) && !(minecraft.screen instanceof FocusScreen)) {
            // Another screen replaced ours (chat, inventory, death): leave the station.
            exit(true);
        }
        if (minecraft.player == null || !minecraft.player.isAlive()) {
            exit(true);
        }
    }

    /** The scene's camera rig, or the default when the scene is missing. */
    public CameraRig rig() {
        if (station == null || station.scene() == null) {
            return CameraRig.DEFAULT;
        }
        SceneDefinition scene = station.scene().definition();
        return scene.camera();
    }

    /** The interface options of the station worked at, or the defaults. */
    public InterfaceOptions options() {
        if (station == null || station.scene() == null) {
            return InterfaceOptions.DEFAULT;
        }
        return station.scene().definition().ui();
    }

    /** What the camera looks at, in the station's pixels. */
    public org.joml.Vector3fc baseTarget() {
        return baseTarget;
    }

    /** How far the camera is between the player's eyes (0) and the rig (1), eased. */
    public float blend() {
        return Easing.inOutCubic(progress);
    }

    /**
     * The camera pose for this frame, or null when the camera is the player's own. {@code eye} is where the vanilla
     * camera would be (the start and end of the flight).
     */
    public CameraPose cameraPose(Vec3 eye, float playerYaw, float playerPitch) {
        if (phase == Phase.IDLE || station == null) {
            return null;
        }
        CameraRig rig = rig();
        Vector3f position = new Vector3f(basePosition);
        Vector3f target = new Vector3f(baseTarget);
        float shot = inventoryShot();
        if (shot > 0.0F) {
            com.pockyl.diegesis.api.scene.CameraShot inventory = rig.inventory().get();
            position.lerp(inventory.position(), shot);
            target.lerp(inventory.target(), shot);
        }
        Glance look = FocusInput.get().look();
        if (look.amount() > 0.0F) {
            position.lerp(look.shot().position(), look.amount());
            target.lerp(look.shot().target(), look.amount());
        }
        Vec3 rigPos = station.toWorld(position.x(), position.y(), position.z());
        Vec3 rigTarget = station.toWorld(target.x(), target.y(), target.z());
        Vec3 dir = rigTarget.subtract(rigPos);
        float rigYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float rigPitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));

        float t = blend();
        float fromYaw = phase == Phase.EXITING ? playerYaw : startYaw;
        float fromPitch = phase == Phase.EXITING ? playerPitch : startPitch;
        Vec3 pos = eye.lerp(rigPos, t).add(0.0, Math.sin(Math.PI * t) * ARC_HEIGHT, 0.0);
        float yaw = fromYaw + Mth.wrapDegrees(rigYaw - fromYaw) * t;
        float pitch = Mth.lerp(t, fromPitch, rigPitch);
        return new CameraPose(pos, yaw, pitch);
    }

    /** The field of view while focused, blended from the player's {@code original}. */
    public double fov(double original) {
        if (phase == Phase.IDLE) {
            return original;
        }
        CameraRig rig = rig();
        CameraShot seat = seatShot(rig);
        float rigFov = seat != null && seat.fov() > 0.0F ? seat.fov() : rig.fov();
        float shot = inventoryShot();
        if (shot > 0.0F && rig.inventory().get().fov() > 0.0F) {
            rigFov = Mth.lerp(shot, rigFov <= 0.0F ? (float) original : rigFov, rig.inventory().get().fov());
        }
        Glance look = FocusInput.get().look();
        if (look.amount() > 0.0F && look.shot().fov() > 0.0F) {
            rigFov = Mth.lerp(look.amount(), rigFov <= 0.0F ? (float) original : rigFov, look.shot().fov());
        }
        return rigFov <= 0.0F ? original : Mth.lerp(blend(), original, rigFov);
    }

    /** How far the camera is towards the rig's inventory shot, 0 to 1, eased; 0 without one. */
    private float inventoryShot() {
        if (station == null || rig().inventory().isEmpty() || phase == Phase.IDLE) {
            return 0.0F;
        }
        return Easing.inOutCubic(FocusInput.get().inventoryReveal());
    }

    /** Turns the player to the station, so the people around see them look at it. */
    private void lookAtStation(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 center = station.pos().getCenter().add(0.0, 0.3, 0.0);
        Vec3 dir = center.subtract(eye);
        player.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
        player.yHeadRot = player.getYRot();
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) {
    }

    private enum Phase {
        IDLE, ENTERING, FOCUSED, EXITING
    }
}
