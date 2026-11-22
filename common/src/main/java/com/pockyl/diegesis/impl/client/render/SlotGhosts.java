package com.pockyl.diegesis.impl.client.render;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.scene.SlotBinding;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What an empty slot shows see-through to the player at the station: the behaviour's ghosts, else the scene file's ids and tags,
 * one after the other. Tags are read each time, so a reload of the server's tags shows at once.
 */
final class SlotGhosts {
    private static final Set<String> WARNED = new HashSet<>();

    private SlotGhosts() {
    }

    /** The ghost shown now, {@code time} seconds into the station's life; empty when the slot has none. */
    static ItemStack shown(NodeInstance node, SlotBinding slot, float time) {
        List<ItemStack> stacks = node.ghostItems();
        if (stacks == null) {
            stacks = resolve(slot.ghost());
        }
        if (stacks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int at = Math.floorMod((int) Math.floor(time / slot.ghostPeriod()), stacks.size());
        return stacks.get(at);
    }

    private static List<ItemStack> resolve(List<String> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (String entry : entries) {
            boolean tag = entry.startsWith("#");
            Identifier id = Identifier.tryParse(tag ? entry.substring(1) : entry);
            if (id == null) {
                warn(entry);
                continue;
            }
            if (tag) {
                stacks.addAll(tagged(TagKey.create(Registries.ITEM, id)));
            } else {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item == Items.AIR) {
                    // Another mod's item that is not installed: left out quietly, as a tag of nothing is.
                    continue;
                }
                stacks.add(item.getDefaultInstance());
            }
        }
        return stacks;
    }

    /**
     * The items of a tag, as {@code ItemStack.is(tag)} sees them (the registry's own tag lists can be empty on a client): looked up
     * by every item's holder, kept for a few seconds, so a reload of the server's tags shows soon.
     */
    private static List<ItemStack> tagged(TagKey<Item> tag) {
        long now = System.currentTimeMillis();
        Tagged known = TAGGED.get(tag);
        if (known != null && now - known.at() < TAG_KEPT_MS) {
            return known.stacks();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.builtInRegistryHolder().is(tag)) {
                stacks.add(item.getDefaultInstance());
            }
        }
        TAGGED.put(tag, new Tagged(List.copyOf(stacks), now));
        return stacks;
    }

    private record Tagged(List<ItemStack> stacks, long at) {
    }

    private static final long TAG_KEPT_MS = 5000L;
    private static final java.util.Map<TagKey<Item>, Tagged> TAGGED = new java.util.HashMap<>();

    private static void warn(String entry) {
        if (WARNED.add(entry)) {
            DiegesisMod.LOGGER.warn("Slot ghost '{}' is neither an item id nor a #tag", entry);
        }
    }
}
