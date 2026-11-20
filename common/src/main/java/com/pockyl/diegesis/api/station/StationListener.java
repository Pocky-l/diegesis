package com.pockyl.diegesis.api.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.TankContents;

import org.jetbrains.annotations.ApiStatus;

/**
 * Watches stations of any mod from the outside: hears what players do there and may refuse it. Register one with
 * {@code Diegesis.addListener}, for one station type or for all of them, during mod setup on both sides.
 *
 * <p>A listener runs where the station's logic runs (the server for {@link Authority#SERVER}, the client for
 * {@link Authority#CLIENT}), right after the logic's own hook, and never while the client only predicts an action. The
 * {@code may} methods can only refuse: the station's own {@link StationLogic} is asked first, then every listener in the
 * order they were added, and the first {@code false} ends it. A listener can never allow what the logic refused, and
 * never computes outputs. Every method has an empty default; a listener that throws is logged and skipped.
 */
@ApiStatus.Experimental
public interface StationListener {
    /** A player started working at the station. */
    default void onOpen(StationContext station, Player player) {
    }

    /** A player stopped working at the station. */
    default void onClose(StationContext station, Player player) {
    }

    /** Whether {@code stack} may go into {@code slot}; return false to refuse. Asked only when the logic allowed it. */
    default boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return true;
    }

    /** Whether the player may take the item out of {@code slot}; return false to refuse. */
    default boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return true;
    }

    /** Whether {@code liquid} may be poured from one tank into another; return false to refuse. */
    default boolean mayPour(StationContext station, Player player, String from, String to, TankContents liquid) {
        return true;
    }

    /** A slot's item changed. */
    default void onSlotChanged(StationContext station, SlotRef slot) {
    }

    /** A player took {@code stack} out of a slot. */
    default void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
    }

    /** Liquid was poured from one tank into another. */
    default void onPoured(StationContext station, Player player, String from, String to, TankContents poured) {
    }

    /** A player set a control to a new value. */
    default void onControlChanged(StationContext station, Player player, String control, float value) {
    }

    /** A player pressed a button control. */
    default void onButton(StationContext station, Player player, String control) {
    }

    /** A player turned or resized an item that stands freely in a {@code placed()} group. */
    default void onPoseChanged(StationContext station, Player player, SlotRef slot, ItemPose pose) {
    }

    /** A mod-defined action arrived from a client ({@code StationView.sendAction}). Check {@code data} like any input. */
    default void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
    }

    /**
     * An effect is played at the station for everyone who sees it: by the logic ({@code StationContext.effect}) or shared by
     * a player's client. Only the look; nothing in the state changed because of it.
     *
     * @param node the node it starts at, or {@code null} for the station's centre
     */
    default void onEffect(StationContext station, Identifier effect, String node, CompoundTag data) {
    }
}
