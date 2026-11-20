package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.impl.codec.StrictCodecs;

import java.util.Optional;

/**
 * Makes a node a place for an item: players drop items from their inventory onto it and pick them up again.
 *
 * @param slot    the station slot the node shows, e.g. {@code "grid[4]"}
 * @param display how the item is shown
 * @param size     the size of the slot (its drop zone and highlight) in pixels; also of the shown item without
 *                 {@code item_size}
 * @param itemSize the size of the shown item in pixels, when it differs from the slot's
 * @param counts   when the stack's count is drawn: slots close together stay readable with {@code hover}
 * @param highlight the square laid over the slot under the cursor; off for things that are no slots to the eye, as food
 *                  swimming in a pot (the hover outline stays)
 * @param countFacing which way the count's digits face: lying on the slot ({@code up}), or standing at the item's foot facing the
 *                    block's front ({@code front}), for an item standing in a wall's hollow that the camera looks at straight
 * @param idleHighlight how strongly the square shows while the cursor is elsewhere (0 to 1 of the hovered one), so the places
 *                      to put things are seen at a glance; 0, the default, shows it only under the cursor
 * @param ghost what goes here, shown see-through in the empty slot to the player at the station, as vanilla's screens draw an
 *              outline in an empty armour or template slot: item ids, or {@code #}tags; several take turns. Empty, the default:
 *              nothing. A behaviour may set its own with {@code NodeView.setGhostItems}
 * @param ghostAlpha how opaque the ghost is, 0 to 1
 * @param ghostPeriod seconds each of several ghosts is shown before the next
 */
public record SlotBinding(SlotRef slot, ItemDisplay display, float size, Optional<Float> itemSize, Counts counts, boolean highlight,
        CountFacing countFacing, float idleHighlight, java.util.List<String> ghost, float ghostAlpha, float ghostPeriod) {
    /** A ghost's opacity and turn by default: faint enough never to be taken for a real item; vanilla's 1.5 s a turn. */
    public static final float DEFAULT_GHOST_ALPHA = 0.4F;
    public static final float DEFAULT_GHOST_PERIOD = 1.5F;

    public static final Codec<SlotBinding> CODEC = StrictCodecs.record(instance -> instance.group(
            SlotRef.CODEC.fieldOf("slot").forGetter(SlotBinding::slot),
            StrictCodecs.optional(ItemDisplay.CODEC, "display", ItemDisplay.FLAT).forGetter(SlotBinding::display),
            StrictCodecs.optional(Codec.floatRange(1.0F, 64.0F), "size", 6.0F).forGetter(SlotBinding::size),
            StrictCodecs.optional(Codec.floatRange(0.5F, 64.0F), "item_size").forGetter(SlotBinding::itemSize),
            StrictCodecs.optional(Counts.CODEC, "counts", Counts.ALWAYS).forGetter(SlotBinding::counts),
            StrictCodecs.optional(Codec.BOOL, "highlight", true).forGetter(SlotBinding::highlight),
            StrictCodecs.optional(CountFacing.CODEC, "count_facing", CountFacing.UP).forGetter(SlotBinding::countFacing),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "idle_highlight", 0.0F).forGetter(SlotBinding::idleHighlight),
            StrictCodecs.optional(Codec.STRING.listOf(), "ghost", java.util.List.of()).forGetter(SlotBinding::ghost),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "ghost_alpha", DEFAULT_GHOST_ALPHA).forGetter(SlotBinding::ghostAlpha),
            StrictCodecs.optional(Codec.floatRange(0.1F, 60.0F), "ghost_period", DEFAULT_GHOST_PERIOD).forGetter(SlotBinding::ghostPeriod)
    ).apply(instance, SlotBinding::new));

    /** A slot without a ghost. */
    public SlotBinding(SlotRef slot, ItemDisplay display, float size, Optional<Float> itemSize, Counts counts, boolean highlight,
            CountFacing countFacing, float idleHighlight) {
        this(slot, display, size, itemSize, counts, highlight, countFacing, idleHighlight, java.util.List.of(), DEFAULT_GHOST_ALPHA,
                DEFAULT_GHOST_PERIOD);
    }

    /** A slot whose square shows only under the cursor. */
    public SlotBinding(SlotRef slot, ItemDisplay display, float size, Optional<Float> itemSize, Counts counts, boolean highlight,
            CountFacing countFacing) {
        this(slot, display, size, itemSize, counts, highlight, countFacing, 0.0F);
    }

    /** A slot whose count lies on it. */
    public SlotBinding(SlotRef slot, ItemDisplay display, float size, Optional<Float> itemSize, Counts counts, boolean highlight) {
        this(slot, display, size, itemSize, counts, highlight, CountFacing.UP);
    }

    public static SlotBinding of(SlotRef slot) {
        return new SlotBinding(slot, ItemDisplay.FLAT, 6.0F, Optional.empty(), Counts.ALWAYS, true);
    }

    /** Which way a slot's count faces. */
    public enum CountFacing implements StringRepresentable {
        /** Lying on the slot, read from above. */
        UP("up"),
        /** Standing at the item's foot, facing the block's front. */
        FRONT("front"),
        /**
         * At the item's foot, always turned to the camera, as a name tag is: reads whole from a camera that looks down at a slant,
         * where a lying or standing number is squashed.
         */
        CAMERA("camera");

        public static final Codec<CountFacing> CODEC = StringRepresentable.fromEnum(CountFacing::values);

        private final String name;

        CountFacing(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** The size the item is drawn at. */
    public float shownItemSize() {
        return itemSize.orElse(size);
    }

    /** When a slot shows how many items lie in it. */
    public enum Counts implements StringRepresentable {
        ALWAYS("always"),
        /** Only while the cursor is on the slot. */
        HOVER("hover"),
        NEVER("never");

        public static final Codec<Counts> CODEC = StringRepresentable.fromEnum(Counts::values);

        private final String name;

        Counts(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** How an item in a slot is drawn. */
    public enum ItemDisplay implements StringRepresentable {
        /** Lying on the surface, like an item put down on a table. */
        FLAT("flat"),
        /** Standing up, facing the camera side of the block. */
        UPRIGHT("upright"),
        /** Hovering and slowly turning, like a crafting result. */
        FLOATING("floating"),
        /** Not drawn, and no count: the scene shows what is in the slot its own way, as clay on a wheel is the pot. */
        HIDDEN("hidden");

        public static final Codec<ItemDisplay> CODEC = StringRepresentable.fromEnum(ItemDisplay::values);

        private final String name;

        ItemDisplay(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
