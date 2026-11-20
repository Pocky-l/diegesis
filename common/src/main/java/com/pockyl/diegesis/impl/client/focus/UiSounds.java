package com.pockyl.diegesis.impl.client.focus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.pockyl.diegesis.api.client.DiegesisSounds;

/** The quiet foley of working at a station: picking up, putting down, levers and knobs. */
public final class UiSounds {
    private UiSounds() {
    }

    static void pickUp(Vec3 at) {
        play(DiegesisSounds.PICK_UP, at, 0.5F, 1.1F);
    }

    public static void putDown(Vec3 at) {
        play(DiegesisSounds.PUT_DOWN, at, 0.5F, 0.95F);
    }

    static void grab(Vec3 at) {
        play(DiegesisSounds.GRAB, at, 0.5F, 1.3F);
    }

    static void setDown(Vec3 at) {
        play(DiegesisSounds.SET_DOWN, at, 0.5F, 1.4F);
    }

    static void toggle(Vec3 at, boolean on) {
        play(DiegesisSounds.TOGGLE, at, 0.5F, on ? 0.65F : 0.55F);
    }

    static void button(Vec3 at) {
        play(DiegesisSounds.BUTTON, at, 0.5F, 0.9F);
    }

    static void detent(Vec3 at, float position) {
        play(DiegesisSounds.DETENT, at, 0.4F, 1.6F + position * 0.3F);
    }

    /** Landing speeds (pixels a second) between which a landing goes from soft to its loudest. */
    private static final float SOFT = 3.0F;
    private static final float HARD = 40.0F;

    /**
     * A thing put down touches what it lands on: a block of its material (its place sound), a flat thing a light tick, anything
     * else the put-down sound, lower the heavier it is (pitch 1.35 at a weight of 0.3 down to 0.8 at 30); louder the faster.
     */
    public static void landed(Vec3 at, net.minecraft.world.item.ItemStack stack, float speed, float mass, boolean flat) {
        float hard = net.minecraft.util.Mth.clamp((speed - SOFT) / (HARD - SOFT), 0.0F, 1.0F);
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem block) {
            net.minecraft.world.level.block.SoundType sound = block.getBlock().defaultBlockState().getSoundType();
            play(sound.getPlaceSound(), at, 0.25F + 0.2F * hard, sound.getPitch() * 0.8F);
            return;
        }
        if (flat) {
            play(DiegesisSounds.SET_DOWN, at, 0.3F + 0.2F * hard, 1.5F);
            return;
        }
        float heavy = net.minecraft.util.Mth.clamp((float) (Math.log(Math.max(mass, 0.3F) / 0.3F) / Math.log(100.0)), 0.0F, 1.0F);
        play(DiegesisSounds.PUT_DOWN, at, 0.35F + 0.25F * hard + 0.1F * heavy, 1.35F - 0.55F * heavy);
    }

    /** A vessel sounds higher the fuller it gets, as any filling bottle does. */
    static void pour(Vec3 at, float flow, float fill) {
        play(DiegesisSounds.POUR, at, 0.2F + 0.35F * flow, 0.95F + 0.55F * fill);
    }

    private static void play(SoundEvent sound, Vec3 at, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !FocusController.get().options().sounds()) {
            return;
        }
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.BLOCKS, volume, pitch,
                minecraft.level.getRandom(), at.x, at.y, at.z));
    }
}
