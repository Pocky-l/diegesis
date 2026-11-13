package com.pockyl.diegesis.impl.client.focus;

import com.mojang.blaze3d.platform.Window;
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
import com.pockyl.diegesis.api.scene.HudOptions;
import com.pockyl.diegesis.api.scene.InterfaceOptions;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.station.Authority;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.anim.Easing;
import com.pockyl.diegesis.impl.client.hud.HudSlide;
import com.pockyl.diegesis.impl.client.render.ClearBlocks;
import com.pockyl.diegesis.impl.network.Packets;
import com.pockyl.diegesis.platform.ClientPlatform;

/**
 * The local player's work at a station: flies the camera to the station's rig and back, hides the HUD, keeps the
 * focus screen open and decides when interaction is allowed (after the server accepted, for server stations).
 *
 * <p>States: idle, entering (camera flying in), focused, exiting (flying back). Progress runs on real time, so the
 * flight is smooth at any frame rate and tick lag. Going over to a neighbouring station is entering it from where the camera
 * was at the station left, not from the player's eyes. A flight sets off at once and lands softly ({@link Easing#flight}), on a
 * slight arc, with the field of view and the first-person hand in step with it.
 */
public final class FocusController {
    private static final FocusController INSTANCE = new FocusController();
    /** Least height of the little arc the camera flies in, in blocks at the middle of the flight. */
    private static final double ARC_HEIGHT = 0.12;
    /** The arc's height as a part of the flight's length, for longer flights. */
    private static final double ARC_SHARE = 0.1;
    /** The part of a flight (in time) over which the first-person hand goes down, or, flying back, comes up again at its end. */
    private static final float HAND_SHARE = 0.3F;
    /** A little later the hand is no longer drawn: the game draws it a tick behind, so it is out of the picture by then. */
    private static final float HAND_GONE = 0.45F;
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
    /** Where the camera would look without the station, in the last frame: the way back heads there. */
    private float eyeYaw;
    private float eyePitch;
    /** Where the camera's base shot is now: the seat's, or the rig's; glides when the server gives another seat. */
    private final Vector3f basePosition = new Vector3f();
    private final Vector3f baseTarget = new Vector3f();
    /** Where the camera flies from while going over to a neighbour, and its field of view there; null otherwise. */
    private CameraPose flightFrom;
    private double flightFromFov;
    /** The station flown over from: the blocks kept out of its shots stay out until the camera arrives. */
    private ClientStation flightFromStation;
    private float flightTime;
    /** The flight was turned to another station half way: it sets off at speed (the camera is moving already). */
    private boolean flightOnward;
    /** Leaving half way over to a neighbour: the camera flies back from where it was then, not from the rig. */
    private CameraPose exitFrom;
    private double exitFromFov;
    /** The pose and field of view of the last frame. */
    private CameraPose lastPose;
    private double lastFov;

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
        target.setOpenedByUse(true);
        if (phase == Phase.EXITING && station != null && station.pos().equals(target.pos())) {
            // Used again while the camera was still flying back: turn round from where it is. The HUD state saved at
            // the first entry stays the one to restore.
            station = target;
            float at = blend();
            // The way in starts from the look the way back was heading to, so the camera turns round where it is.
            startYaw = eyeYaw;
            startPitch = eyePitch;
            exitFrom = null;
            confirmed = !awaitServer;
            phase = Phase.ENTERING;
            // From where the camera is: the way in eases differently from the way back.
            progress = Easing.flightInverse(at);
            hudLeaves(minecraft);
            lastFrameNanos = Util.getNanos();
            if (confirmed) {
                station.setFocusedInternal(true);
            }
            openScreen(minecraft);
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
        // Where the camera looked in the last frame: the player's eyes, or a third-person view of a camera mod.
        startYaw = minecraft.gameRenderer.getMainCamera().getYRot();
        startPitch = minecraft.gameRenderer.getMainCamera().getXRot();
        savedHideGui = minecraft.options.hideGui;
        hudLeaves(minecraft);
        flightFrom = null;
        exitFrom = null;
        FocusInput.get().look().reset();
        FocusInput.get().neighbors().reset();
        FocusInput.get().gains().reset();
        snapBaseShot();
        lookAtStation(player);
        if (confirmed) {
            station.setFocusedInternal(true);
        }
        openScreen(minecraft);
    }

    private static void openScreen(Minecraft minecraft) {
        BlurMods.keepOff();
        CameraMods.giveWay();
        minecraft.setScreen(new FocusScreen());
    }

    /** Whether the camera is flying over to a neighbour. */
    public boolean isGoingOver() {
        return phase == Phase.ENTERING && flightFrom != null;
    }

    /**
     * Goes over from the station worked at to {@code target}, a neighbour: the camera flies from where it is to the new rig in
     * {@code seconds}, the focus screen stays, and the station left hears that the player has gone. Flying over already, the
     * camera turns to {@code target} from where it is (owner, 2026-11-09: two quick presses of D go two stations on at once).
     */
    public void goOver(ClientStation target, boolean awaitServer, float seconds) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean onward = isGoingOver();
        if (player == null || phase != Phase.FOCUSED && !onward || station == null || lastPose == null) {
            return;
        }
        flightOnward = onward;
        FocusInput.get().cancel();
        FocusInput.get().look().reset();
        FocusInput.get().neighbors().reset();
        ClientStation left = station;
        boolean wasConfirmed = confirmed;
        target.setOpenedByUse(false);
        flightFrom = lastPose;
        flightFromFov = lastFov;
        flightFromStation = left;
        flightTime = seconds;
        station = target;
        confirmed = !awaitServer;
        phase = Phase.ENTERING;
        progress = 0.0F;
        lastFrameNanos = Util.getNanos();
        snapBaseShot();
        lookAtStation(player);
        // The station left hears of it with the flight under way: what it hands over (a book in the air) knows where to.
        if (wasConfirmed) {
            left.setFocusedInternal(false);
        }
        if (confirmed) {
            station.setFocusedInternal(true);
        }
    }

    /** How far the camera has flown over to a neighbouring station, eased, 0 to 1; 1 when it is not flying over. */
    public float goingOver() {
        return flightFrom == null ? 1.0F : blend();
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

    /** The server refused; after going over to a neighbour it still holds the player at the station left: that ends too. */
    public void abort(BlockPos pos) {
        if (station != null && station.pos().equals(pos)) {
            exit(true);
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
        if (flightFrom != null && lastPose != null) {
            // Half way over to a neighbour: back from where the camera is, over the whole way out.
            exitFrom = lastPose;
            exitFromFov = lastFov;
            flightFrom = null;
            progress = 1.0F;
        } else if (phase == Phase.ENTERING) {
            // Turning back half way in: from where the camera is, as the way back eases differently from the way in.
            progress = 1.0F - Easing.flightInverse(1.0F - blend());
        }
        phase = Phase.EXITING;
        Minecraft minecraft = Minecraft.getInstance();
        if (!savedHideGui && sliding()) {
            // The HUD comes back while the camera flies back.
            minecraft.options.hideGui = false;
            HudSlide.in(hud());
        }
        if (minecraft.screen instanceof FocusScreen) {
            minecraft.setScreen(null);
        }
    }

    /**
     * The HUD leaves: it slides off the screen while the scene hides it and the player had it shown, and is hidden once it is off
     * ({@link #frame}); otherwise it is hidden at once, as before there was a slide.
     */
    private void hudLeaves(Minecraft minecraft) {
        if (!savedHideGui && sliding()) {
            minecraft.options.hideGui = false;
            HudSlide.out(hud());
        } else {
            minecraft.options.hideGui = true;
        }
    }

    /** Whether the station's scene lets the HUD slide away rather than vanish. */
    private boolean sliding() {
        return options().hideHud() && hud().slide();
    }

    private HudOptions hud() {
        return station == null || station.scene() == null ? HudOptions.DEFAULT : station.scene().definition().hud();
    }

    /** Drops everything at once (level change, disconnect). */
    public void reset() {
        if (phase != Phase.IDLE) {
            Minecraft.getInstance().options.hideGui = savedHideGui;
        }
        HudSlide.reset();
        FocusInput.get().cancel();
        FocusInput.get().look().reset();
        phase = Phase.IDLE;
        station = null;
        confirmed = false;
        flightFrom = null;
        flightFromStation = null;
        exitFrom = null;
        lastPose = null;
        ClearBlocks.clear();
    }

    /** Advances the camera flight; once per frame, before the camera is set up. */
    public void frame() {
        HudSlide.frame();
        if (phase == Phase.IDLE) {
            return;
        }
        if (phase != Phase.EXITING && HudSlide.gone() && !Minecraft.getInstance().options.hideGui) {
            // All of it is off the screen: hidden now, as F1 hides it.
            Minecraft.getInstance().options.hideGui = true;
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
        float duration = Math.max(flightFrom != null ? flightTime : currentRig.transition(), 0.01F);
        Minecraft minecraft = Minecraft.getInstance();
        if (phase == Phase.ENTERING) {
            progress = Math.min(1.0F, progress + dt / duration);
            if (progress >= 1.0F) {
                phase = Phase.FOCUSED;
                flightFrom = null;
                flightFromStation = null;
                flightOnward = false;
            }
        } else if (phase == Phase.EXITING) {
            progress = Math.max(0.0F, progress - dt / duration);
            if (progress <= 0.0F) {
                minecraft.options.hideGui = savedHideGui;
                phase = Phase.IDLE;
                station = null;
                flightFromStation = null;
                exitFrom = null;
                lastPose = null;
                ClearBlocks.clear();
                return;
            }
        }
        ClearBlocks.keep(station, flightFromStation, dt);
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

    /**
     * How far the camera is between the player's eyes (0) and the rig (1), eased: flying in or back it sets off at once and lands
     * softly, so the way back runs the curve from its other end.
     */
    public float blend() {
        if (flightFrom != null) {
            return flightOnward ? Easing.outCubic(progress) : Easing.flight(progress);
        }
        return phase == Phase.EXITING ? 1.0F - Easing.flight(1.0F - progress) : Easing.flight(progress);
    }

    /**
     * How far the camera has come from the player's view to the station worked at, eased: rising while it flies in, falling while
     * it flies back, 1 at the station and while going over to a neighbour, 0 when nobody works at one. What comes with the
     * station (the inventory, an addon's book) shows by it, in step with the camera.
     */
    public float arrival() {
        if (phase == Phase.IDLE) {
            return 0.0F;
        }
        return phase == Phase.FOCUSED || flightFrom != null ? 1.0F : blend();
    }

    /**
     * How far the first-person hand has gone down, 0 to 1: over the first part of the flight in, and up again over the last part
     * of the flight back, as when the held item changes, instead of vanishing and popping back.
     */
    public float handAway() {
        if (phase == Phase.IDLE) {
            return 0.0F;
        }
        if (phase == Phase.FOCUSED || flightFrom != null) {
            return 1.0F;
        }
        float t = Mth.clamp(progress / HAND_SHARE, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Whether the first-person hand is out of the picture, so it is not drawn at all. */
    public boolean isHandGone() {
        return phase == Phase.FOCUSED || flightFrom != null || phase != Phase.IDLE && progress >= HAND_GONE;
    }

    /**
     * The camera pose for this frame, or null when the camera is the player's own. {@code eye}, {@code eyeYaw} and
     * {@code eyePitch} are where the camera would be without the station, as vanilla or a camera mod placed it (the start and
     * end of the flight).
     */
    public CameraPose cameraPose(Vec3 eye, float eyeYaw, float eyePitch) {
        if (phase == Phase.IDLE || station == null) {
            return null;
        }
        this.eyeYaw = eyeYaw;
        this.eyePitch = eyePitch;
        float t = blend();
        CameraPose from;
        CameraPose to = rigPose();
        if (flightFrom != null) {
            from = flightFrom;
        } else if (phase == Phase.EXITING) {
            from = new CameraPose(eye, eyeYaw, eyePitch);
            if (exitFrom != null) {
                to = exitFrom;
            }
        } else {
            from = new CameraPose(eye, startYaw, startPitch);
        }
        Vec3 path = to.position().subtract(from.position());
        Vec3 pos = from.position().add(path.scale(t)).add(arc(path).scale(Math.sin(Math.PI * t)));
        float yaw = from.yaw() + Mth.wrapDegrees(to.yaw() - from.yaw()) * t;
        float pitch = Mth.lerp(t, from.pitch(), to.pitch());
        lastPose = new CameraPose(pos, yaw, pitch);
        return lastPose;
    }

    /**
     * Which way and how far the middle of a flight along {@code path} bows out of the straight line: up, square to the path, by a
     * small part of its length. Flying in and up to a station the camera swings up and over onto it rather than sliding along a
     * rail; going over to a neighbour beside it, it makes a small hop.
     */
    private static Vec3 arc(Vec3 path) {
        double length = path.length();
        if (length < 1.0E-4) {
            return Vec3.ZERO;
        }
        Vec3 along = path.scale(1.0 / length);
        // Up, less its part along the path: square to the path, in the upright plane through it.
        Vec3 bow = new Vec3(0.0, 1.0, 0.0).subtract(along.scale(along.y));
        double size = bow.length();
        if (size < 1.0E-3) {
            return Vec3.ZERO;
        }
        return bow.scale(Math.max(ARC_HEIGHT, length * ARC_SHARE) / size);
    }

    /** Where the camera is at the rig this frame: the seat's or the rig's shot, moved by the inventory shot and a glance. */
    private CameraPose rigPose() {
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
        return new CameraPose(rigPos, rigYaw, rigPitch);
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
        double there = rigFov <= 0.0F ? original : keepWidth(rigFov, rig.keepWidth());
        if (flightFrom != null) {
            lastFov = zoom(blend(), flightFromFov > 0.0 ? flightFromFov : there, there);
        } else if (phase == Phase.EXITING && exitFrom != null) {
            lastFov = zoom(blend(), original, exitFromFov);
        } else {
            lastFov = zoom(blend(), original, there);
        }
        return lastFov;
    }

    /**
     * The field of view {@code t} of the way from {@code from} to {@code to} degrees, evenly in how much the picture is magnified:
     * evenly in degrees, most of a zoom in came at the end of the flight, after the camera had nearly arrived.
     */
    private static double zoom(float t, double from, double to) {
        double a = Math.tan(Math.toRadians(Mth.clamp(from, 1.0, 179.0)) / 2.0);
        double b = Math.tan(Math.toRadians(Mth.clamp(to, 1.0, 179.0)) / 2.0);
        return 2.0 * Math.toDegrees(Math.atan(a * Math.pow(b / a, t)));
    }

    /**
     * The vertical field of view that shows the width {@code fov} shows in a window of shape {@code keep} (width / height), when
     * the window is narrower: the game keeps the vertical field, so a 4:3 window or a tall one lost the sides of the shot, where
     * stations put their books.
     */
    private static double keepWidth(double fov, float keep) {
        Window window = Minecraft.getInstance().getWindow();
        double aspect = window.getHeight() > 0 ? (double) window.getWidth() / window.getHeight() : keep;
        if (keep <= 0.0F || aspect >= keep) {
            return fov;
        }
        double wider = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(fov) / 2.0) * keep / aspect));
        return Math.min(wider, 170.0);
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
