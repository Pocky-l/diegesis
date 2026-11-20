package com.pockyl.diegesis.impl.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.client.MenuRefusal;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A station's own behaviour followed by those other mods added. Every hook reaches each of them in that order; a hook that
 * takes input ({@code onItemClick}, {@code onScroll}, keys) stops at the first that takes it, so an added behaviour can
 * add a gesture but never steal one the station handles. One that throws is logged once and the others still run.
 */
final class BehaviorChain implements StationBehavior {
    private static final Set<Class<?>> FAILED = ConcurrentHashMap.newKeySet();

    private final Identifier station;
    private final StationBehavior[] behaviors;

    BehaviorChain(Identifier station, StationBehavior[] behaviors) {
        this.station = station;
        this.behaviors = behaviors;
    }

    private void each(java.util.function.Consumer<StationBehavior> call) {
        for (StationBehavior behavior : behaviors) {
            try {
                call.accept(behavior);
            } catch (RuntimeException e) {
                failed(behavior, e);
            }
        }
    }

    private boolean first(java.util.function.Predicate<StationBehavior> call) {
        for (StationBehavior behavior : behaviors) {
            try {
                if (call.test(behavior)) {
                    return true;
                }
            } catch (RuntimeException e) {
                failed(behavior, e);
            }
        }
        return false;
    }

    private void failed(StationBehavior behavior, RuntimeException e) {
        if (FAILED.add(behavior.getClass())) {
            DiegesisMod.LOGGER.error("Client code {} at station {} failed", behavior.getClass().getName(), station, e);
        }
    }

    @Override
    public void onSceneReady(StationView view) {
        each(behavior -> behavior.onSceneReady(view));
    }

    @Override
    public void onOpen(StationView view) {
        each(behavior -> behavior.onOpen(view));
    }

    @Override
    public void onClose(StationView view) {
        each(behavior -> behavior.onClose(view));
    }

    @Override
    public void tick(StationView view) {
        each(behavior -> behavior.tick(view));
    }

    @Override
    public boolean holdsBlock(StationView view) {
        return first(behavior -> behavior.holdsBlock(view));
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        each(behavior -> behavior.frame(view, deltaSeconds));
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        each(behavior -> behavior.onStateChanged(view, changes));
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        each(behavior -> behavior.onEvent(view, event));
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        return first(behavior -> behavior.onItemClick(view, node, stack, button, shift));
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return first(behavior -> behavior.onScroll(view, node, amount));
    }

    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        return first(behavior -> behavior.onKey(view, keyCode, scanCode, modifiers));
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return first(behavior -> behavior.onChar(view, character, modifiers));
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        return first(behavior -> behavior.onInventoryShiftClick(view, slot, stack));
    }

    @Override
    public void onEffect(StationView view, Identifier effect, String node, CompoundTag data) {
        each(behavior -> behavior.onEffect(view, effect, node, data));
    }

    @Override
    public void onMenuOpened(StationView view) {
        each(behavior -> behavior.onMenuOpened(view));
    }

    @Override
    public void onMenuRefused(StationView view, MenuRefusal why) {
        each(behavior -> behavior.onMenuRefused(view, why));
    }
}
