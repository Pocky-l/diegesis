package com.pockyl.diegesis.impl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.TankContents;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationListener;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@link StationListener}s mods added, and the logic wrapper that calls them after a station's own logic. A station
 * without listeners keeps its bare logic, so nothing costs anything until a mod adds one.
 */
public final class Listeners {
    private static final List<StationListener> ALL = new CopyOnWriteArrayList<>();
    private static final Map<Identifier, List<StationListener>> BY_STATION = new ConcurrentHashMap<>();
    private static final Set<StationListener> FAILED = ConcurrentHashMap.newKeySet();
    private static final StationListener[] NONE = new StationListener[0];

    private Listeners() {
    }

    public static void add(StationListener listener) {
        ALL.add(listener);
    }

    public static void add(Identifier station, StationListener listener) {
        BY_STATION.computeIfAbsent(station, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /** The listeners of one station type, those for this type first, then those for every station. */
    public static StationListener[] of(Identifier station) {
        List<StationListener> own = BY_STATION.get(station);
        if ((own == null || own.isEmpty()) && ALL.isEmpty()) {
            return NONE;
        }
        List<StationListener> listeners = new ArrayList<>();
        if (own != null) {
            listeners.addAll(own);
        }
        listeners.addAll(ALL);
        return listeners.toArray(NONE);
    }

    /** The station's logic with its listeners after it; the logic itself when there are none. */
    public static StationLogic wrap(Identifier station, StationLogic logic) {
        StationListener[] listeners = of(station);
        return listeners.length == 0 ? logic : new Listened(logic, listeners);
    }

    /** Tells the listeners of a station logic about an effect played there; nothing for a bare logic. */
    public static void effect(StationLogic logic, StationContext station, Identifier effect, String node, CompoundTag data) {
        if (logic instanceof Listened listened) {
            for (StationListener listener : listened.listeners) {
                run(listener, () -> listener.onEffect(station, effect, node, data));
            }
        }
    }

    private static void run(StationListener listener, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            failed(listener, e);
        }
    }

    private static boolean ask(StationListener listener, java.util.function.BooleanSupplier call) {
        try {
            return call.getAsBoolean();
        } catch (RuntimeException e) {
            failed(listener, e);
            return true;
        }
    }

    private static void failed(StationListener listener, RuntimeException e) {
        if (FAILED.add(listener)) {
            DiegesisMod.LOGGER.error("Station listener {} failed (logged once; it is still called)", listener.getClass().getName(), e);
        }
    }

    /** The logic first, then each listener; {@code may} asks stop at the first refusal. */
    private static final class Listened implements StationLogic {
        private final StationLogic logic;
        private final StationListener[] listeners;

        Listened(StationLogic logic, StationListener[] listeners) {
            this.logic = logic;
            this.listeners = listeners;
        }

        @Override
        public void onOpen(StationContext station, Player player) {
            logic.onOpen(station, player);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onOpen(station, player));
            }
        }

        @Override
        public void onClose(StationContext station, Player player) {
            logic.onClose(station, player);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onClose(station, player));
            }
        }

        @Override
        public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
            if (!logic.mayPlace(station, player, slot, stack)) {
                return false;
            }
            for (StationListener listener : listeners) {
                if (!ask(listener, () -> listener.mayPlace(station, player, slot, stack))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean mayTake(StationContext station, Player player, SlotRef slot) {
            if (!logic.mayTake(station, player, slot)) {
                return false;
            }
            for (StationListener listener : listeners) {
                if (!ask(listener, () -> listener.mayTake(station, player, slot))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean mayPour(StationContext station, Player player, String from, String to, TankContents liquid) {
            if (!logic.mayPour(station, player, from, to, liquid)) {
                return false;
            }
            for (StationListener listener : listeners) {
                if (!ask(listener, () -> listener.mayPour(station, player, from, to, liquid))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void updateOutputs(StationContext station) {
            logic.updateOutputs(station);
        }

        @Override
        public ItemStack takeOutput(StationContext station, Player player, String output) {
            return logic.takeOutput(station, player, output);
        }

        @Override
        public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
            logic.onTaken(station, player, slot, stack);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onTaken(station, player, slot, stack));
            }
        }

        @Override
        public void onSlotChanged(StationContext station, SlotRef slot) {
            logic.onSlotChanged(station, slot);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onSlotChanged(station, slot));
            }
        }

        @Override
        public void onPoseChanged(StationContext station, Player player, SlotRef slot, ItemPose pose) {
            logic.onPoseChanged(station, player, slot, pose);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onPoseChanged(station, player, slot, pose));
            }
        }

        @Override
        public void onControlChanged(StationContext station, Player player, String control, float value) {
            logic.onControlChanged(station, player, control, value);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onControlChanged(station, player, control, value));
            }
        }

        @Override
        public void onButton(StationContext station, Player player, String control) {
            logic.onButton(station, player, control);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onButton(station, player, control));
            }
        }

        @Override
        public void onPoured(StationContext station, Player player, String from, String to, TankContents poured) {
            logic.onPoured(station, player, from, to, poured);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onPoured(station, player, from, to, poured));
            }
        }

        @Override
        public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
            logic.onCustomAction(station, player, action, data);
            for (StationListener listener : listeners) {
                run(listener, () -> listener.onCustomAction(station, player, action, data));
            }
        }

        @Override
        public void tick(StationContext station) {
            logic.tick(station);
        }
    }
}
