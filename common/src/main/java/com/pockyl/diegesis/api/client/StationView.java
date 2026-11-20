package com.pockyl.diegesis.api.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationType;

import java.util.Optional;
import java.util.function.Predicate;

/** A station shown on this client, as its {@link StationBehavior} sees it. */
public interface StationView {
    StationType type();

    Level level();

    BlockPos pos();

    /** The direction the station's front faces for this use; the scene is turned to it. */
    Direction facing();

    /**
     * How far past {@link #facing()} the scene is turned for this use, degrees, within -45 to 45: non-zero only for
     * {@code Orientation.PLAYER_FREE}, whose scene faces the player at their own angle. Positive turns the scene the way
     * north turns to east. A node that stands in for the block itself is kept square to the world by
     * {@code node.rotateTo(0, turn(), 0, 0)}.
     */
    default float turn() {
        return 0.0F;
    }

    /** This client's copy of the state (the server's for server stations, kept in sync; read it, do not change it). */
    StationState state();

    /** Whether the local player is working at the station right now. */
    boolean isFocused();

    /**
     * Whether anybody works at the station right now, the local player or another one. While so, a station that
     * {@code hidesBlock()} is drawn by its scene alone: show the scene's copy of the block then, and only then.
     */
    boolean isInUse();

    /**
     * Whether the block's own model is left out of the world in this frame, for a station that {@code hidesBlock()}: draw the
     * scene's copy of the block exactly then, and the two swap without a blink. It follows {@link #isInUse} and
     * {@code StationBehavior.holdsBlock} a frame late, and after them stays true until the chunk, built on another thread, shows
     * the block again.
     */
    default boolean isBlockHidden() {
        return isInUse();
    }

    /** The seat the local player holds here ({@code StationType.Builder.seat}); empty when they have none. */
    Optional<String> seat();

    /** Seconds since the station appeared on this client. */
    float age();

    Optional<NodeView> node(String id);

    /** Converts a point in the scene (Blockbench pixels, before the facing turn) to world coordinates. */
    Vec3 toWorld(float x, float y, float z);

    /**
     * Sends a mod-defined action to the station logic ({@code StationLogic.onCustomAction}) - on the server for a
     * server station, directly for a client-only one. Only while the local player works at the station.
     */
    void sendAction(String action, CompoundTag data);

    /**
     * Whether shift is held now, as the station's own clicks see it: for a {@code SceneEvent.Click} or a key that does more
     * with shift. Read it here rather than from {@code Screen.hasShiftDown()}, which test drivers cannot press.
     */
    default boolean isShiftDown() {
        return com.pockyl.diegesis.impl.client.focus.FocusScreen.shiftDown();
    }

    /**
     * Throws a burst of scene particles from a node (its origin; the station's centre for {@code null}).
     *
     * @param particle a style id ({@link DiegesisParticles} or registered)
     * @param color    an ARGB tint, or 0 for the style's own colour
     */
    void spawnParticles(Identifier particle, String node, int count, int color);

    /**
     * Scene particles at a point of the scene (pixels, before the facing turn), flying off with {@code velocity}
     * (pixels per second) give or take a little.
     */
    void spawnParticles(Identifier particle, Vector3fc at, Vector3fc velocity, float spread, int count, int color);

    /**
     * Scene particles anywhere inside the liquid of a node with a {@code tank} (wherever the node is now, also while it is
     * carried or tipped); they rise with {@code velocity} (pixels per second, in the node's space) and die at the
     * surface. Nothing happens while the tank is empty. Glints in a reacting brew, bubbles from a dropped stone.
     */
    void spawnInLiquid(Identifier particle, String node, int count, int color, Vector3fc velocity);

    /**
     * Scene particles leaving the surface of a node's liquid, at random points of it, with {@code velocity} (pixels per second, in
     * the node's space): steam off a soup. Nothing happens while the tank is empty.
     */
    void spawnOnLiquid(Identifier particle, String node, int count, int color, Vector3fc velocity);

    /**
     * Scene particles on the surface of a node's liquid along its walls, where a pot starts to boil: small bubbles, {@code scale} times the
     * style's size, that stay where they are and ring the liquid when they end. Use {@code DiegesisParticles.BUBBLE_POP}.
     */
    void spawnAtLiquidEdge(Identifier particle, String node, int count, int color, float scale);

    /**
     * Keeps a stream of liquid running this frame from {@code from} down to {@code to} (scene pixels, before the facing turn), as
     * the stream of a vessel poured by hand looks: a bottle an animation tips over a pot, milk poured over a cake. Call it every
     * frame while it runs; once the calls stop, the stream lets go of {@code from} and falls the rest of the way. Calls with the
     * same {@code key} feed one stream.
     *
     * @param color      ARGB colour of the liquid
     * @param flow       how thick it runs, 0 to 1 (the scene's {@code pouring.stream_width} at 1)
     * @param liquidNode a node with a tank whose liquid the stream lands in and stirs up with rings, or {@code null}: it only splashes
     */
    default void pourStream(String key, Vector3fc from, Vector3fc to, int color, float flow, String liquidNode) {
    }

    /**
     * Where the local player's cursor points on the level plane at {@code height} (scene pixels, before the facing turn): a spoon
     * following the hand over a soup. Empty when the player is not working here or looks away from the plane.
     */
    Optional<org.joml.Vector3f> cursorOn(float height);

    /**
     * Where a point of the local player's screen ({@code x, y} fractions of its width and height, 0,0 the top left) lies on the level
     * plane at {@code height} (scene pixels, before the facing turn): things coming from the player's side, as food from the
     * inventory at the bottom of the screen. Empty when the player is not working here or that point looks away from the plane.
     */
    Optional<org.joml.Vector3f> screenOn(float x, float y, float height);

    /**
     * Where the local player's cursor points on the plane through a node at {@code height} over its origin, in the node's own pixels: a
     * page, a map on a table, also while the node is tilted and moves. Empty when the player is not working here, or there is no such node.
     */
    Optional<org.joml.Vector3f> pointerOn(String node, float height);

    /**
     * Opens the scene's inventory for the local player, or closes it: slides the {@code inventory_drawer} out and pins it,
     * or shows an inventory laid out with {@code "show": "toggle"}. Only while the player works at the station.
     */
    void setInventoryOpen(boolean open);

    /** Whether the scene's inventory is out for the local player. */
    boolean isInventoryOpen();

    /**
     * Which of the local player's stacks a {@code compact} inventory drawer shows: the ones the station can use. Null, the
     * default, shows every stack that is not empty.
     */
    void setInventoryFilter(Predicate<ItemStack> filter);

    /**
     * Moves what stands on a surface of the scene's {@code placing} with a part that moves: a drawer sliding out, the shelves
     * of a door. {@code transform} is in station pixels, from where the surface is written in the scene to where it is now;
     * write the surface where the part stands open, so that the cursor finds its places there. The identity, the default,
     * leaves the items where they are.
     *
     * @param surface the surface's index in the scene's {@code placing.surfaces}
     */
    default void setSurfaceTransform(int surface, Matrix4fc transform) {
    }

    /**
     * Whether the items of the scene's {@code placing} are drawn: off while they are shut away, inside a cupboard whose
     * copy the scene no longer shows. On by default; set it every frame it should hold.
     */
    default void setPlacingVisible(boolean visible) {
    }

    /**
     * Whether the items of the scene's {@code placing} can be reached: picked up, moved, and the surfaces take what the hand
     * carries. Off while they are seen but out of reach, behind the shut glass door of an oven. On by default; set it every
     * frame it should hold.
     */
    default void setPlacingReachable(boolean reachable) {
    }

    /**
     * Whether the items on one surface of the scene's {@code placing} are drawn, and the surface takes any: off while that
     * surface is shut away (one of two drawers pushed in) and the rest are open. On by default; kept until set again.
     *
     * @param surface the surface's index in the scene's {@code placing.surfaces}
     */
    default void setSurfaceVisible(int surface, boolean visible) {
    }

    /**
     * Whether the scene's {@code placing} has room for one more stack of {@code stack}: a free spot on a surface shown now where
     * it stands clear of the items there (at its full size, or smaller down to {@code scale_range}'s least). What fits in is a
     * matter of room, not of slots: a station asks before it takes a stack in by a shift-click. False without a placing.
     */
    default boolean hasRoomFor(net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    /**
     * Whether the library leaves out the stack on the local player's cursor while it is over the scene: the station draws it
     * itself, as the tool it is (a knife over a board that comes down when clicked). Set it every frame it should hold; it is off
     * again when the player leaves.
     */
    void setCarriedHidden(boolean hidden);

    /**
     * Keeps the stroke going after its button came up, until the next click, which ends it and does nothing else: a short click on
     * a stroke node can take the object in hand (a spoon to stir with) so that nobody has to hold the button. Works only while the
     * behaviour hears that stroke's {@code SceneEvent.Stroke} END; the stroke starts again with START.
     */
    void holdStroke();

    /**
     * Whether the local player came to the station by using its block, not by going over from a neighbour: what they held in the
     * hand was meant for it (food to put down, a knife to cut with). Holds while they work here.
     */
    boolean openedByUse();

    /**
     * Takes the stack in the player's inventory slot {@code slot} (0-35, the hotbar first) onto the cursor, as a click on it in the
     * drawer does; the server's answer decides. When the player leaves, it goes back to that slot if that is still free.
     */
    void takeFromInventory(int slot);

    /**
     * Puts the stack on the cursor back into the player's inventory, to the slot it was taken from while that is free, as a right
     * click that nothing in the scene takes does (scene option {@code put_away}); the server's answer decides. For a station whose
     * own right click would otherwise use the stack, such as a knife over food.
     */
    void putAwayCarried();

    /**
     * Glides the local player's camera to a glance of the rig ({@code camera.glances}); it stays there until the player
     * looks back or {@link #glanceBack()}. Only while the player works at the station.
     *
     * @return false when the scene has no such glance or the player is not working here
     */
    boolean glance(String id);

    /** Glides the camera back from a glance to the rig. */
    void glanceBack();

    /** The glance the local player looks at or is on the way to. */
    Optional<String> currentGlance();

    /**
     * Moves the whole camera of the station (its shot, seat, inventory shot and glances) by {@code x}, {@code y}, {@code z} scene
     * pixels; it glides there. For a station whose work moves, such as a tower worked at different heights. Kept until set again.
     * Set from {@code onSceneReady} or {@code onOpen}, the camera flies straight to the moved shot.
     */
    default void moveCamera(float x, float y, float z) {
    }

    /**
     * Mirrors the station's camera (its shot, inventory shot and glances) across the upright plane {@code x} = {@code plane}
     * scene pixels: a shot from the front left looks from the front right. For a block that comes in two hands, such as a
     * cupboard whose door hangs on either side, so that the camera stands on the side the door leaves free. {@code NaN} turns it
     * off. Set from {@code onSceneReady} or {@code onOpen}; kept until set again.
     */
    default void mirrorCamera(float plane) {
    }

    /** The plane the camera is mirrored across ({@link #mirrorCamera}), or {@code NaN}. */
    default float cameraMirror() {
        return Float.NaN;
    }

    /** How far the camera is moved by {@link #moveCamera}, scene pixels. */
    default org.joml.Vector3fc cameraMove() {
        return new org.joml.Vector3f();
    }

    /**
     * Shows a short line at the edge of the screen while the local player works at the station (scene field
     * {@code captions}): whose turn it is, what just happened. It replaces the line shown before.
     *
     * @param text    the line; {@code null} clears it
     * @param seconds how long it stays; 0 or less until it is replaced
     */
    void showCaption(net.minecraft.network.chat.Component text, float seconds);

    /**
     * Lays something drawn by the mod over the scene for the local player: a book of recipes, a map. It takes the mouse until it is
     * closed; {@code null} closes it. Only while the player works at the station.
     */
    void showOverlay(ScreenOverlay overlay);

    /**
     * Keeps a sound going at the station for as long as it is wanted: simmering, a machine running, a stir. Call it every frame or now and
     * then with what you want now; it glides to that volume and pitch, starts by itself and fades out at a volume of 0. The sound's file must
     * loop without a seam. {@code name} tells a station's loops apart.
     */
    void loopSound(String name, SoundEvent sound, float volume, float pitch);

    /** Plays a sound at a node (or the station's centre for {@code null}). */
    void playSound(SoundEvent sound, String node, float volume, float pitch);

    /**
     * The open vanilla menu of a menu-backed station ({@code StationType.Builder.menu}) while the local player works at it;
     * null for other stations and before the server has opened it.
     */
    default MenuAccess menu() {
        return null;
    }
}
