package com.pockyl.diegesis.api.event;

import org.joml.Vector3fc;

/**
 * Something the local player did in a scene, reported to the station's client behaviour. State changes (items, control
 * values) arrive separately through {@code onStateChanged}, for every player's actions.
 *
 * <p>New versions add events: test them with {@code instanceof}, or give a {@code switch} over them a {@code default} branch.
 */
public sealed interface SceneEvent {
    /** The cursor moved onto a node ({@code node} set) or off every node ({@code node} null). */
    record Hover(String node) implements SceneEvent {
    }

    /** A node without any role, but with a collider, was clicked. */
    record Click(String node, int button) implements SceneEvent {
    }

    /**
     * The mouse working on a node with {@code "strokes": true}: while a button is held this comes every frame, so a hand held still
     * goes on working, and {@code HOVER} comes every frame while the cursor is over the node with no button down, to show what a
     * press would do. {@code point} is where the cursor is on the upright plane through the node's origin that faces the camera,
     * in scene pixels: it is there also beside the node and in the air.
     */
    record Stroke(String node, Phase phase, int button, Vector3fc point) implements SceneEvent {
        /** A stroke starts once, moves every frame and ends once; hovering comes before it. */
        public enum Phase {
            HOVER, START, MOVE, END
        }
    }

    /** A grabbable node was picked up. */
    record Grab(String node) implements SceneEvent {
    }

    /**
     * A grabbable node was let go; {@code target} is the node under it at that moment (null when none): the hook for
     * "pour this vial into that flask" style interactions.
     */
    record Release(String node, String target) implements SceneEvent {
    }

    /**
     * A carried node started ({@code touching} true) or stopped touching another node's hit box: the hook for trigger
     * zones (a node with a {@code collider} and nothing else) and for knocks between objects. {@code speed} is how
     * fast the carried node moved, in pixels per second.
     */
    record Contact(String node, String other, boolean touching, float speed) implements SceneEvent {
    }

    /**
     * The vessel the local player holds started ({@code pouring} true) or stopped pouring into another tank; the amounts
     * reach the logic through {@code StationLogic.onPoured}.
     */
    record Pour(String node, String target, boolean pouring) implements SceneEvent {
    }

    /** The inventory drawer slid out ({@code open} true) or back. */
    record InventoryDrawer(boolean open) implements SceneEvent {
    }

    /** The camera set off for a glance of the rig ({@code looking} true) or back from it. */
    record Glance(String glance, boolean looking) implements SceneEvent {
    }

    /** A control was moved by the local player; {@code value} is the new value (also sent to the server). */
    record ControlMoved(String control, float value) implements SceneEvent {
    }

    /** A button control was pressed by the local player. */
    record ButtonPressed(String control) implements SceneEvent {
    }

    /**
     * A placed thing knocked into another ({@code other} its slot) or into a wall or the floor ({@code other} null), with the
     * scene's physics ({@code placing.physics}): coming together at {@code speed} pixels a second, at least the scene's
     * {@code feedback.impact_min_speed}. {@code mass} is its weight (cubic pixels times density), {@code point} where it happened
     * in scene pixels. Comes at most every 0.1 second per thing, never while things rest. A thing put down landing is a
     * {@link Landed} instead.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    record Impact(com.pockyl.diegesis.api.state.SlotRef slot, com.pockyl.diegesis.api.state.SlotRef other, float speed, float mass,
            Vector3fc point) implements SceneEvent {
    }

    /**
     * A thing put down with the scene's physics touched what it lands on ({@code on} its slot, null for a surface), once per
     * putting down, at {@code speed} pixels a second; {@code flat} for a flat icon. Comes also with the landing sound off.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    record Landed(com.pockyl.diegesis.api.state.SlotRef slot, com.pockyl.diegesis.api.state.SlotRef on, float speed, float mass,
            boolean flat) implements SceneEvent {
    }

    /** A placed thing fell off its surfaces (pushed over an open edge): it goes back to the inventory next. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    record FellOff(com.pockyl.diegesis.api.state.SlotRef slot) implements SceneEvent {
    }

    /**
     * A stream of liquid reached where it lands, once per stream: one the player pours ({@code source} the vessel) or one code runs
     * ({@code StationView.pourStream}, {@code source} null). {@code target} is the node whose liquid it falls into (null: it only
     * splashes), {@code point} where it lands in scene pixels, {@code color} its ARGB colour.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    record StreamLanded(String source, String target, Vector3fc point, int color) implements SceneEvent {
    }

    /**
     * A carried node's contact box went under ({@code under} true) or came out of the liquid of {@code tank}'s node: dipped in,
     * pulled out. {@code depth} is how deep its lowest point went, in pixels below the surface.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    record Submerge(String node, String tank, boolean under, float depth) implements SceneEvent {
    }

    /**
     * The stack in the local player's hand started ({@code touching} true) or stopped touching a node that is a {@code trigger}
     * or carries marks: a stick held into a flame. {@code speed} is how fast the hand moved, pixels a second.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    record ItemContact(net.minecraft.world.item.ItemStack stack, String other, boolean touching, float speed) implements SceneEvent {
    }

    /**
     * A carried node ({@code node}), or the stack in the hand ({@code node} null), started touching a node carrying {@code mark}
     * ({@code exposed} true), or left the last such node ({@code exposed} false). {@code source} is the marked node. Marks are labels
     * ({@code Marks}): the station's logic decides what being exposed to fire or water does.
     */
    @org.jetbrains.annotations.ApiStatus.Experimental
    record Exposed(String node, net.minecraft.resources.Identifier mark, String source, boolean exposed) implements SceneEvent {
    }
}
