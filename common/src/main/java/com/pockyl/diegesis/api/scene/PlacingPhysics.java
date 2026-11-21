package com.pockyl.diegesis.api.scene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import com.pockyl.diegesis.impl.codec.StrictCodecs;

/**
 * Items placed freely fall, tip over and push each other as solid things do: one let go of on its edge falls onto a side
 * unless something holds it up, one put down where another stands shoves it aside, and what stood on an item taken away comes
 * down. Written as {@code "physics"} inside {@code "placing"}; without it items stand exactly as they are put.
 *
 * <p>The grip is exact, the body is physical. The stack in the hand is held by its top: the point held follows the cursor
 * closely and alone decides where it lands. Below the grip it is a weight: it floats a little over the surface, sways a little
 * as it is carried and hangs still once the hand stops, and rises onto what it comes near where there is room above. It
 * disturbs nothing on its way: where it cannot rise, it keeps beside what stands there, so it lands where it is seen (with
 * {@code hand_pushes} it is solid instead and, swept in fast, knocks things aside). Going to another surface (a shelf, a door's
 * bin) it hops over in a short arc. Let go of, it drops under the grip and rocks. Things move only when the hand means them
 * to: items dragged or turned by hand hit what is in their way. Flat items (food icons) stand as they were turned and rock when
 * bumped, as a roly-poly toy does.
 *
 * <p>The player who moves things works it out on their side and sends where everything came to rest; everyone else sees the
 * items glide there.
 *
 * @param gravity     how fast things fall, pixels per second squared; 157 is the real one (9.81 m/s^2) at 16 pixels a metre, a
 *                    block a metre; for a scene drawn at another scale, 9.81 times its pixels per metre
 * @param friction    0..1, how hard a thing grips what it stands on or touches, as a share of its weight: pushed less than that
 *                    it stays put, pushed harder it slides and slows down by a little less
 * @param uprightFlat flat items (an icon: bread, a carrot) standing straight (within 10 degrees) keep standing and rock back
 *                    when bumped (up to {@code rock}); at a lean or lying they are thin rigid plates; false: thin rigid cards
 *                    that fall over also standing straight
 * @param rock        degrees a flat item rocks at most when bumped; 0 keeps it rigidly upright
 * @param flatDepth   pixels of depth a flat item collides with at least; 0: as thin as it is drawn, so it lies on what is under
 *                    it and stands against its neighbours with nothing between
 * @param hover       pixels the stack in the hand floats over what is under it; it drops that far when let go
 * @param carryLag    seconds the point held takes to close half the way to the cursor; 0 follows at once
 * @param reach       pixels around the stack in the hand within which it rises onto an item
 * @param climb       pixels per second the stack in the hand rises onto an item it comes near; it comes down faster
 * @param swing       the stack in the hand hangs from its top and swings as it is carried; false keeps it upright
 * @param swingAngle  degrees the stack in the hand swings out at most
 * @param swingCalm   seconds the swing takes to die down once the hand is still
 * @param hopTime     seconds the stack in the hand takes to hop over to another surface; 0 jumps there at once
 * @param toss        0..1, how much of the hand's speed a stack let go of while moving keeps (a short slide), and of its turning
 *                    an item let go of while turned by hand (it spins on); 0 stops each where it is let go
 * @param handForce   how hard the hand pulls an item it drags or turns (weight times pixels a second squared; weight is size
 *                    in cubic pixels times density): a heavy thing comes slower, and what does not give way stops it
 * @param handPushes  the stack in the hand is solid: it rises onto things only when brought up gently and, swept in fast,
 *                    shoves and tips them; false: it passes them by, keeping beside what it cannot rise onto
 * @param turnPower   how much quicker an item turns by hand with physics than {@code placing.turn_speed} says
 * @param fallOff     what may go over an open edge: {@code held} only what the hand drags over it, {@code any} anything
 *                    pushed or bumped there too
 * @param maxSpeed    pixels per second a thing moves at most: a push never flings it far
 * @param bounce      0..0.5, how much of the speed a thing hits something with it bounces back with; 0: it stops dead where it
 *                    lands or hits, as kitchen things do
 * @param flatPoses   how a flat thing (an icon) may be turned: {@code free}, to any angle, resting as it falls (standing
 *                    straight, lying, or leaning on what is beside it); {@code two}, standing up or lying down only, a tilt
 *                    of the wheel or a turn by hand going over to the other or settling on the nearer
 * @param flush       pixels: put down this close to a neighbour or a wall, a thing lands touching it, with no sliver of a gap
 *                    (never more than this along each way, never into anything); 0: where it was let go
 * @param landingSound a thing put down sounds when it touches what it lands on, as what it is (a block of its material, a
 *                    flat thing a light tick, a heavy thing lower); false: at the click, the same for everything
 * @param pileRides   a pile is one thing: what stands on an item goes along with it, standing as it stood, when the hand drags,
 *                    turns or pushes that item, and falls only when it tips over or a dragged thing hits the pile itself; moved
 *                    by the physics alone (settling, knocked), what stands on it comes to rest by the physics too; false: what
 *                    stands on it holds on by friction alone
 * @param pushOut     pixels per second a thing put down, carried or crowded inside another glides out of it at most, without
 *                    taking speed from that; 0: out at once
 * @param wake        what a change wakes: {@code near}, what it touches, and what touched something that then moves or goes
 *                    (a heap at rest elsewhere stays exactly as it is); {@code all}, every item of the station
 * @param fallEase    seconds a thing moved by the physics is drawn behind where the physics has it (half the way): small, so
 *                    a fall looks as fast as it is and stops dead; things set there glide as before
 */
public record PlacingPhysics(float gravity, float friction, boolean uprightFlat, float rock, float flatDepth, float hover, float carryLag,
        float reach, float climb, boolean swing, float swingAngle, float swingCalm, float hopTime, float toss, boolean handPushes,
        float handForce, float turnPower, FallOff fallOff, float maxSpeed, float bounce, boolean pileRides, FlatPoses flatPoses,
        float flush, boolean landingSound, float pushOut, Wake wake, float fallEase) {
    public static final PlacingPhysics DEFAULT = new PlacingPhysics(157.0F, 0.6F, true, 25.0F, 0.0F, 1.5F, 0.035F, 4.0F, 48.0F, true,
            12.0F, 0.15F, 0.1F, 0.0F, false, 40000.0F, 1.5F, FallOff.ANY, 80.0F, 0.0F, true, FlatPoses.FREE, 0.3F, true, 12.0F, Wake.NEAR,
            0.008F);

    /** The physics as it was before {@code push_out}, {@code wake} and {@code fall_ease}, with their defaults. */
    public PlacingPhysics(float gravity, float friction, boolean uprightFlat, float rock, float flatDepth, float hover, float carryLag,
            float reach, float climb, boolean swing, float swingAngle, float swingCalm, float hopTime, float toss, boolean handPushes,
            float handForce, float turnPower, FallOff fallOff, float maxSpeed, float bounce, boolean pileRides, FlatPoses flatPoses,
            float flush, boolean landingSound) {
        this(gravity, friction, uprightFlat, rock, flatDepth, hover, carryLag, reach, climb, swing, swingAngle, swingCalm, hopTime, toss,
                handPushes, handForce, turnPower, fallOff, maxSpeed, bounce, pileRides, flatPoses, flush, landingSound, DEFAULT.pushOut(),
                DEFAULT.wake(), DEFAULT.fallEase());
    }

    /** The physics as it was before {@code pile_rides}, with piles riding. */
    public PlacingPhysics(float gravity, float friction, boolean uprightFlat, float rock, float flatDepth, float hover, float carryLag,
            float reach, float climb, boolean swing, float swingAngle, float swingCalm, float hopTime, float toss, boolean handPushes,
            float handForce, float turnPower, FallOff fallOff, float maxSpeed, float bounce) {
        this(gravity, friction, uprightFlat, rock, flatDepth, hover, carryLag, reach, climb, swing, swingAngle, swingCalm, hopTime, toss,
                handPushes, handForce, turnPower, fallOff, maxSpeed, bounce, DEFAULT.pileRides(), DEFAULT.flatPoses(),
                DEFAULT.flush(), DEFAULT.landingSound());
    }

    /** What a change at the station wakes. */
    public enum Wake implements StringRepresentable {
        /** What it touches, and on from there what moves. */
        NEAR,
        /** Every item. */
        ALL;

        public static final Codec<Wake> CODEC = StringRepresentable.fromEnum(Wake::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** How a flat thing may stand. */
    public enum FlatPoses implements StringRepresentable {
        /** Standing up or lying down, nothing between. */
        TWO,
        /** At any lean it is given. */
        FREE;

        public static final Codec<FlatPoses> CODEC = StringRepresentable.fromEnum(FlatPoses::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** What may go over an open edge of a surface. */
    public enum FallOff implements StringRepresentable {
        /** Only an item the hand drags over the edge and lets go of there. */
        HELD,
        /** Anything pushed or bumped over it too. */
        ANY;

        public static final Codec<FallOff> CODEC = StringRepresentable.fromEnum(FallOff::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The stack in the hand, read as one part: a record codec takes at most 16 fields. */
    private record Hand(float hover, float carryLag, float reach, float climb, boolean swing, float swingAngle, float swingCalm,
            float hopTime, float toss, boolean handPushes, float handForce) {
        static final MapCodec<Hand> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                StrictCodecs.optional(Codec.floatRange(0.0F, 8.0F), "hover", DEFAULT.hover()).forGetter(Hand::hover),
                StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "carry_lag", DEFAULT.carryLag()).forGetter(Hand::carryLag),
                StrictCodecs.optional(Codec.floatRange(0.0F, 8.0F), "reach", DEFAULT.reach()).forGetter(Hand::reach),
                StrictCodecs.optional(Codec.floatRange(1.0F, 1000.0F), "climb", DEFAULT.climb()).forGetter(Hand::climb),
                StrictCodecs.optional(Codec.BOOL, "swing", DEFAULT.swing()).forGetter(Hand::swing),
                StrictCodecs.optional(Codec.floatRange(0.0F, 60.0F), "swing_angle", DEFAULT.swingAngle()).forGetter(Hand::swingAngle),
                StrictCodecs.optional(Codec.floatRange(0.1F, 2.0F), "swing_calm", DEFAULT.swingCalm()).forGetter(Hand::swingCalm),
                StrictCodecs.optional(Codec.floatRange(0.0F, 0.5F), "hop_time", DEFAULT.hopTime()).forGetter(Hand::hopTime),
                StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "toss", DEFAULT.toss()).forGetter(Hand::toss),
                StrictCodecs.optional(Codec.BOOL, "hand_pushes", DEFAULT.handPushes()).forGetter(Hand::handPushes),
                StrictCodecs.optional(Codec.floatRange(1000.0F, 10000000.0F), "hand_force", DEFAULT.handForce()).forGetter(Hand::handForce)
        ).apply(instance, Hand::new));
    }

    /** How things come to rest, read as one part: the top level is full (16 fields). */
    private record Rest(float pushOut, Wake wake, float fallEase) {
        static final MapCodec<Rest> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                StrictCodecs.optional(Codec.floatRange(0.0F, 200.0F), "push_out", DEFAULT.pushOut()).forGetter(Rest::pushOut),
                StrictCodecs.optional(Wake.CODEC, "wake", DEFAULT.wake()).forGetter(Rest::wake),
                StrictCodecs.optional(Codec.floatRange(0.0F, 0.5F), "fall_ease", DEFAULT.fallEase()).forGetter(Rest::fallEase)
        ).apply(instance, Rest::new));
    }

    public static final Codec<PlacingPhysics> CODEC = StrictCodecs.record(instance -> instance.group(
            StrictCodecs.optional(Codec.floatRange(1.0F, 4000.0F), "gravity", DEFAULT.gravity()).forGetter(PlacingPhysics::gravity),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "friction", DEFAULT.friction()).forGetter(PlacingPhysics::friction),
            StrictCodecs.optional(Codec.BOOL, "upright_flat", DEFAULT.uprightFlat()).forGetter(PlacingPhysics::uprightFlat),
            StrictCodecs.optional(Codec.floatRange(0.0F, 45.0F), "rock", DEFAULT.rock()).forGetter(PlacingPhysics::rock),
            StrictCodecs.optional(Codec.floatRange(0.0F, 2.0F), "flat_depth", DEFAULT.flatDepth()).forGetter(PlacingPhysics::flatDepth),
            Hand.MAP_CODEC.forGetter(physics -> new Hand(physics.hover, physics.carryLag, physics.reach, physics.climb, physics.swing,
                    physics.swingAngle, physics.swingCalm, physics.hopTime, physics.toss, physics.handPushes, physics.handForce)),
            StrictCodecs.optional(Codec.floatRange(1.0F, 3.0F), "turn_power", DEFAULT.turnPower()).forGetter(PlacingPhysics::turnPower),
            StrictCodecs.optional(FallOff.CODEC, "fall_off", DEFAULT.fallOff()).forGetter(PlacingPhysics::fallOff),
            StrictCodecs.optional(Codec.floatRange(10.0F, 400.0F), "max_speed", DEFAULT.maxSpeed()).forGetter(PlacingPhysics::maxSpeed),
            StrictCodecs.optional(Codec.floatRange(0.0F, 0.5F), "bounce", DEFAULT.bounce()).forGetter(PlacingPhysics::bounce),
            StrictCodecs.optional(Codec.BOOL, "pile_rides", DEFAULT.pileRides()).forGetter(PlacingPhysics::pileRides),
            StrictCodecs.optional(FlatPoses.CODEC, "flat_poses", DEFAULT.flatPoses()).forGetter(PlacingPhysics::flatPoses),
            StrictCodecs.optional(Codec.floatRange(0.0F, 1.0F), "flush", DEFAULT.flush()).forGetter(PlacingPhysics::flush),
            StrictCodecs.optional(Codec.BOOL, "landing_sound", DEFAULT.landingSound()).forGetter(PlacingPhysics::landingSound),
            Rest.MAP_CODEC.forGetter(physics -> new Rest(physics.pushOut, physics.wake, physics.fallEase))
    ).apply(instance, (gravity, friction, uprightFlat, rock, flatDepth, hand, turnPower, fallOff, maxSpeed, bounce, pileRides, flatPoses,
            flush, landingSound, rest) ->
            new PlacingPhysics(gravity, friction, uprightFlat, rock, flatDepth, hand.hover(), hand.carryLag(), hand.reach(), hand.climb(),
                    hand.swing(), hand.swingAngle(), hand.swingCalm(), hand.hopTime(), hand.toss(), hand.handPushes(), hand.handForce(),
                    turnPower, fallOff, maxSpeed, bounce, pileRides, flatPoses, flush, landingSound, rest.pushOut(), rest.wake(),
                    rest.fallEase())));
}
