package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.DataResult;
import org.joml.Vector2f;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Free placing: items are put down anywhere on the scene's surfaces and stay there, turned and sized as the player likes. It
 * shows the slot group {@code group}, whose rules must be {@code placed()}; an item of the group is an object of the scene, not
 * a node of the file. Written as {@code "placing"} in a scene file; {@code group} and {@code surfaces} are required.
 *
 * @param group        the slot group that holds the items
 * @param surfaces     where they can stand
 * @param itemSize     pixels the longest side of an item measures at size 1
 * @param turnStep     degrees of one notch of the wheel
 * @param scaleRange   the sizes a player can give an item, as factors of {@code item_size}
 * @param scaleStep    what one notch of the wheel changes the size by
 * @param turnSpeed    degrees an item turns per GUI pixel of a drag
 * @param stacking     items can stand on each other; what stood on an item comes down when it is taken away
 * @param blockModels  a block item with a flat icon (a lantern, a torch, a flower pot) stands there as the block
 * @param itemLight    items of blocks that give light shine on the scene, candles and torches burn
 * @param lightRadius  how far their light reaches, in pixels
 * @param lightColor   ARGB colour of the pool of light they throw (alpha: its strength)
 * @param shadows      items cast their shape on what they stand on
 * @param snap         positions are rounded to this many pixels; 0 keeps them as they are
 * @param previewColor ARGB colour of the see-through copy that shows where the item in the hand would stand
 * @param counts       when the count of a stack of more than one is written at its foot, facing the front: {@code always},
 *                     {@code hover} (while the cursor is on it) or {@code never}
 * @param physics      items fall, tip over and push each other ({@code "physics"}); empty: they stand exactly as put
 * @param framePose    every item stands as an item frame shows it; false: an item with a 3D model (a can, a jar) stands as it is
 *                     modelled, upright, where a frame would lay it on its back to face out of the wall; flat icons stand as in a
 *                     frame either way
 * @param hoverLift    pixels an item rises while the cursor is on it; 0 (default) keeps it still, the outline alone says so
 * @param handling     how the hand stacks, turns and picks items (its fields are written at the top level of {@code "placing"})
 */
public record PlacingOptions(String group, List<PlaceSurface> surfaces, float itemSize, float turnStep, float[] scaleRange, float scaleStep,
        float turnSpeed, boolean stacking, boolean blockModels, boolean itemLight, float lightRadius, int lightColor, boolean shadows,
        float snap, int previewColor, SlotBinding.Counts counts, Optional<PlacingPhysics> physics, boolean framePose, float hoverLift,
        Handling handling) {
    /**
     * How the hand stacks, turns and picks items. Written at the top level of {@code "placing"}.
     *
     * @param stackLow  an item lower than this share of {@code item_size} is all top: pointed at anywhere, the stack in the hand
     *                  goes on it (a book lying down)
     * @param stackFrom of a taller item, the share of its height from its foot up from which pointing at it means on top of it;
     *                  lower, the stack in the hand goes in front of it
     * @param pickMin   pixels each side of an item's hit box measures at least for the cursor: a lying sheet seen edge-on or the
     *                  tip of a slanted icon is still hit, though only where the cursor is on no item exactly; 0: exact boxes
     * @param gripBlend GUI pixels of the cursor's travel over which an item just taken goes from where the hand took it to
     *                  under the cursor: taken and put again without moving, it stands exactly where it stood, and the drag of
     *                  a placed item keeps the point grabbed under the cursor; 0: it jumps under the cursor at once
     * @param stackSnap  put on a wider thing, the stack in the hand goes where the cursor points on its top; within this share
     *                   of that thing's half size from its middle (each way) it goes to the exact middle, so same things pile
     *                   up neatly. 1: always the middle
     * @param stackEdge  how far in from the edge of what it goes on the middle of the stack in the hand stays, as a share of its
     *                   own half size: 0 lets its middle reach the edge (no further), 0.5 keeps half of it over the top
     * @param stackAlign degrees: a flat thing laid on a flat thing turned within this much of it (or of a quarter turn from it)
     *                   takes its turn, so a pile of sheets lies square; 0: never
     * @param turnDetents degrees between the turns about the vertical a turn by hand settles on (45: square and diagonal)
     * @param turnDetent  degrees: a turn by hand let go of within this much of one of those settles onto it; anywhere else it
     *                    stays as it is. 0: never
     * @param stackShrink of the size the player gave it, how small a thing put on another is made at most to fit under what is
     *                    over it (never below {@code scale_range}); where it does not fit at that, it goes beside. Taken again
     *                    later, it has its own size back. 1: never smaller
     */
    public record Handling(float stackLow, float stackFrom, float pickMin, float gripBlend, float stackSnap, float stackEdge,
            float stackAlign, float turnDetents, float turnDetent, float stackShrink, boolean rightPutsOne) {
        public static final Handling DEFAULT = new Handling(0.45F, 0.5F, 0.75F, 12.0F, 0.3F, 0.0F, 12.0F, 90.0F, 3.0F, 0.75F, true);

        /** The handling before {@link #rightPutsOne} existed, with it on. */
        public Handling(float stackLow, float stackFrom, float pickMin, float gripBlend, float stackSnap, float stackEdge, float stackAlign,
                float turnDetents, float turnDetent, float stackShrink) {
            this(stackLow, stackFrom, pickMin, gripBlend, stackSnap, stackEdge, stackAlign, turnDetents, turnDetent, stackShrink, true);
        }

        static final MapCodec<Handling> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "stack_low", DEFAULT.stackLow()).forGetter(Handling::stackLow),
                StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "stack_from", DEFAULT.stackFrom()).forGetter(Handling::stackFrom),
                StrictCodecs.optional(Codec.floatRange(0.0F, 2.0F), "pick_min", DEFAULT.pickMin()).forGetter(Handling::pickMin),
                StrictCodecs.optional(Codec.floatRange(0.0F, 64.0F), "grip_blend", DEFAULT.gripBlend()).forGetter(Handling::gripBlend),
                StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "stack_snap", DEFAULT.stackSnap()).forGetter(Handling::stackSnap),
                StrictCodecs.optional(Codec.floatRange(0.0F, 0.5F), "stack_edge", DEFAULT.stackEdge()).forGetter(Handling::stackEdge),
                StrictCodecs.optional(Codec.floatRange(0.0F, 45.0F), "stack_align", DEFAULT.stackAlign()).forGetter(Handling::stackAlign),
                StrictCodecs.optional(Codec.floatRange(1.0F, 180.0F), "turn_detents", DEFAULT.turnDetents()).forGetter(Handling::turnDetents),
                StrictCodecs.optional(Codec.floatRange(0.0F, 20.0F), "turn_detent", DEFAULT.turnDetent()).forGetter(Handling::turnDetent),
                StrictCodecs.optional(Codec.floatRange(0.25F, 1.0F), "stack_shrink", DEFAULT.stackShrink()).forGetter(Handling::stackShrink),
                StrictCodecs.optional(Codec.BOOL, "right_puts_one", DEFAULT.rightPutsOne()).forGetter(Handling::rightPutsOne)
        ).apply(instance, Handling::new));
    }

    /** The light of the items, read as one part: a record codec takes at most 16 fields. */
    private record Light(boolean itemLight, float lightRadius, int lightColor) {
        static final MapCodec<Light> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                StrictCodecs.optional(Codec.BOOL, "item_light", true).forGetter(Light::itemLight),
                StrictCodecs.optional(Codec.floatRange(1.0F, 256.0F), "light_radius", 11.0F).forGetter(Light::lightRadius),
                StrictCodecs.optional(Visual.COLOR, "light_color", 0xC0FFC888).forGetter(Light::lightColor)
        ).apply(instance, Light::new));
    }

    /** How the items look while worked with, read as one part (a record codec takes at most 16 fields). */
    private record Look(boolean shadows, int previewColor, float hoverLift) {
        static final MapCodec<Look> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                StrictCodecs.optional(Codec.BOOL, "shadows", true).forGetter(Look::shadows),
                StrictCodecs.optional(Visual.COLOR, "preview_color", 0xA0FFFFFF).forGetter(Look::previewColor),
                StrictCodecs.optional(Codec.floatRange(0.0F, 4.0F), "hover_lift", 0.0F).forGetter(Look::hoverLift)
        ).apply(instance, Look::new));
    }

    public static final Codec<PlacingOptions> CODEC = StrictCodecs.<PlacingOptions>record(instance -> instance.group(
            NodeDefinition.ID_CODEC.fieldOf("group").forGetter(PlacingOptions::group),
            PlaceSurface.CODEC.listOf().fieldOf("surfaces").forGetter(PlacingOptions::surfaces),
            StrictCodecs.optional(Codec.floatRange(0.5F, 64.0F), "item_size", 6.0F).forGetter(PlacingOptions::itemSize),
            StrictCodecs.optional(Codec.floatRange(1.0F, 180.0F), "turn_step", 15.0F).forGetter(PlacingOptions::turnStep),
            StrictCodecs.optional(StrictCodecs.RANGE, "scale_range", new float[] {0.5F, 1.5F}).forGetter(PlacingOptions::scaleRange),
            StrictCodecs.optional(Codec.floatRange(0.01F, 4.0F), "scale_step", 0.1F).forGetter(PlacingOptions::scaleStep),
            StrictCodecs.optional(Codec.floatRange(0.05F, 8.0F), "turn_speed", 1.2F).forGetter(PlacingOptions::turnSpeed),
            StrictCodecs.optional(Codec.BOOL, "stacking", true).forGetter(PlacingOptions::stacking),
            StrictCodecs.optional(Codec.BOOL, "block_models", true).forGetter(PlacingOptions::blockModels),
            Light.MAP_CODEC.forGetter(placing -> new Light(placing.itemLight, placing.lightRadius, placing.lightColor)),
            Look.MAP_CODEC.forGetter(placing -> new Look(placing.shadows, placing.previewColor, placing.hoverLift)),
            StrictCodecs.optional(Codec.floatRange(0.0F, 16.0F), "snap", 0.25F).forGetter(PlacingOptions::snap),
            StrictCodecs.optional(SlotBinding.Counts.CODEC, "counts", SlotBinding.Counts.NEVER).forGetter(PlacingOptions::counts),
            StrictCodecs.optional(PlacingPhysics.CODEC, "physics").forGetter(PlacingOptions::physics),
            StrictCodecs.optional(Codec.BOOL, "frame_pose", false).forGetter(PlacingOptions::framePose),
            Handling.MAP_CODEC.forGetter(PlacingOptions::handling)
    ).apply(instance, (group, surfaces, itemSize, turnStep, scaleRange, scaleStep, turnSpeed, stacking, blockModels, light, look, snap,
            counts, physics, framePose, handling) -> new PlacingOptions(group, surfaces, itemSize, turnStep, scaleRange, scaleStep, turnSpeed,
            stacking, blockModels, light.itemLight(), light.lightRadius(), light.lightColor(), look.shadows(), snap, look.previewColor(), counts,
            physics, framePose, look.hoverLift(), handling)))
            .comapFlatMap(PlacingOptions::validate, placing -> placing);

    public PlacingOptions {
        surfaces = List.copyOf(surfaces);
    }

    /** The options without physics, as before {@code physics} existed. */
    public PlacingOptions(String group, List<PlaceSurface> surfaces, float itemSize, float turnStep, float[] scaleRange, float scaleStep,
            float turnSpeed, boolean stacking, boolean blockModels, boolean itemLight, float lightRadius, int lightColor, boolean shadows,
            float snap, int previewColor, SlotBinding.Counts counts) {
        this(group, surfaces, itemSize, turnStep, scaleRange, scaleStep, turnSpeed, stacking, blockModels, itemLight, lightRadius, lightColor,
                shadows, snap, previewColor, counts, Optional.empty(), false, 0.0F, Handling.DEFAULT);
    }

    /** The options with the hand's handling as it is by default, as before {@code handling} existed. */
    public PlacingOptions(String group, List<PlaceSurface> surfaces, float itemSize, float turnStep, float[] scaleRange, float scaleStep,
            float turnSpeed, boolean stacking, boolean blockModels, boolean itemLight, float lightRadius, int lightColor, boolean shadows,
            float snap, int previewColor, SlotBinding.Counts counts, Optional<PlacingPhysics> physics, boolean framePose, float hoverLift) {
        this(group, surfaces, itemSize, turnStep, scaleRange, scaleStep, turnSpeed, stacking, blockModels, itemLight, lightRadius, lightColor,
                shadows, snap, previewColor, counts, physics, framePose, hoverLift, Handling.DEFAULT);
    }

    private static DataResult<PlacingOptions> validate(PlacingOptions placing) {
        if (placing.surfaces.isEmpty()) {
            return DataResult.error(() -> "\"placing\" needs at least one surface");
        }
        if (placing.scaleRange[0] <= 0.0F || placing.scaleRange[0] > placing.scaleRange[1]) {
            return DataResult.error(() -> "\"scale_range\" needs 0 < smallest <= largest");
        }
        return DataResult.success(placing);
    }

    /** Starts the options in code, for the slot group {@code group}. */
    public static Builder builder(String group) {
        return new Builder(group);
    }

    /** Builds the options in code. */
    public static final class Builder {
        private final String group;
        private final List<PlaceSurface> surfaces = new ArrayList<>();
        private float itemSize = 6.0F;
        private float turnStep = 15.0F;
        private float[] scaleRange = {0.5F, 1.5F};
        private float scaleStep = 0.1F;
        private float turnSpeed = 1.2F;
        private boolean stacking = true;
        private boolean blockModels = true;
        private boolean itemLight = true;
        private float lightRadius = 11.0F;
        private int lightColor = 0xC0FFC888;
        private boolean shadows = true;
        private float snap = 0.25F;
        private int previewColor = 0xA0FFFFFF;
        private SlotBinding.Counts counts = SlotBinding.Counts.NEVER;
        private PlacingPhysics physics;
        private boolean framePose;
        private float hoverLift;
        private Handling handling = Handling.DEFAULT;

        private Builder(String group) {
            this.group = group;
        }

        public Builder surface(float fromX, float fromZ, float toX, float toZ, float height, float clearance, float aimHeight) {
            surfaces.add(new PlaceSurface(new Vector2f(fromX, fromZ), new Vector2f(toX, toZ), height, clearance, aimHeight, Optional.empty()));
            return this;
        }

        /** As {@link #surface}, with a wall behind it at {@code wallZ} that the light in the room falls on. */
        public Builder surface(float fromX, float fromZ, float toX, float toZ, float height, float clearance, float aimHeight, float wallZ) {
            surfaces.add(new PlaceSurface(new Vector2f(fromX, fromZ), new Vector2f(toX, toZ), height, clearance, aimHeight, Optional.of(wallZ)));
            return this;
        }

        public Builder itemSize(float pixels) {
            itemSize = pixels;
            return this;
        }

        public Builder turnStep(float degrees) {
            turnStep = degrees;
            return this;
        }

        public Builder scaleRange(float smallest, float largest) {
            scaleRange = new float[] {smallest, largest};
            return this;
        }

        public Builder scaleStep(float value) {
            scaleStep = value;
            return this;
        }

        public Builder turnSpeed(float degreesPerPixel) {
            turnSpeed = degreesPerPixel;
            return this;
        }

        public Builder stacking(boolean value) {
            stacking = value;
            return this;
        }

        public Builder blockModels(boolean value) {
            blockModels = value;
            return this;
        }

        public Builder itemLight(boolean value) {
            itemLight = value;
            return this;
        }

        public Builder lightRadius(float pixels) {
            lightRadius = pixels;
            return this;
        }

        public Builder lightColor(int argb) {
            lightColor = argb;
            return this;
        }

        public Builder shadows(boolean value) {
            shadows = value;
            return this;
        }

        public Builder snap(float pixels) {
            snap = pixels;
            return this;
        }

        public Builder previewColor(int argb) {
            previewColor = argb;
            return this;
        }

        public Builder counts(SlotBinding.Counts value) {
            counts = value;
            return this;
        }

        /** Items fall, tip over and push each other. */
        public Builder physics(PlacingPhysics value) {
            physics = value;
            return this;
        }

        /** Every item stands as an item frame shows it, 3D models too. */
        public Builder framePose(boolean value) {
            framePose = value;
            return this;
        }

        /** Pixels an item rises while the cursor is on it. */
        public Builder hoverLift(float pixels) {
            hoverLift = pixels;
            return this;
        }

        /** How the hand stacks, turns and picks items. */
        public Builder handling(Handling value) {
            handling = value;
            return this;
        }

        public PlacingOptions build() {
            PlacingOptions placing = new PlacingOptions(group, surfaces, itemSize, turnStep, scaleRange, scaleStep, turnSpeed, stacking,
                    blockModels, itemLight, lightRadius, lightColor, shadows, snap, previewColor, counts, Optional.ofNullable(physics), framePose,
                    hoverLift, handling);
            return validate(placing).getOrThrow();
        }
    }
}
