package com.pockyl.diegesis.impl.client.focus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.pockyl.diegesis.api.client.DiegesisSounds;
import com.pockyl.diegesis.api.scene.fx.FeedbackOptions.Cue;
import com.pockyl.diegesis.api.scene.fx.SoundCue;

/** The quiet foley of working at a station: picking up, putting down, levers and knobs. */
public final class UiSounds {
    private UiSounds() {
    }

    static void pickUp(Vec3 at) {
        play(Cue.PICK_UP, null, DiegesisSounds.PICK_UP, at, 0.5F, 1.1F);
    }

    public static void putDown(Vec3 at) {
        play(Cue.PUT_DOWN, null, DiegesisSounds.PUT_DOWN, at, 0.5F, 0.95F);
    }

    static void grab(String node, Vec3 at) {
        play(Cue.GRAB, node, DiegesisSounds.GRAB, at, 0.5F, 1.3F);
    }

    static void setDown(String node, Vec3 at) {
        play(Cue.SET_DOWN, node, DiegesisSounds.SET_DOWN, at, 0.5F, 1.4F);
    }

    static void toggle(String node, Vec3 at, boolean on) {
        play(Cue.TOGGLE, node, DiegesisSounds.TOGGLE, at, 0.5F, on ? 0.65F : 0.55F);
    }

    static void button(String node, Vec3 at) {
        play(Cue.BUTTON, node, DiegesisSounds.BUTTON, at, 0.5F, 0.9F);
    }

    static void detent(String node, Vec3 at, float position) {
        play(Cue.DETENT, node, DiegesisSounds.DETENT, at, 0.4F, 1.6F + position * 0.3F);
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
            play(Cue.LANDING, null, sound.getPlaceSound(), at, 0.25F + 0.2F * hard, sound.getPitch() * 0.8F);
            return;
        }
        if (flat) {
            play(Cue.LANDING, null, DiegesisSounds.SET_DOWN, at, 0.3F + 0.2F * hard, 1.5F);
            return;
        }
        float heavy = net.minecraft.util.Mth.clamp((float) (Math.log(Math.max(mass, 0.3F) / 0.3F) / Math.log(100.0)), 0.0F, 1.0F);
        play(Cue.LANDING, null, DiegesisSounds.PUT_DOWN, at, 0.35F + 0.25F * hard + 0.1F * heavy, 1.35F - 0.55F * heavy);
    }

    /** A vessel sounds higher the fuller it gets, as any filling bottle does. */
    static void pour(String node, Vec3 at, float flow, float fill) {
        play(Cue.POUR, node, DiegesisSounds.POUR, at, 0.2F + 0.35F * flow, 0.95F + 0.55F * fill);
    }

    /**
     * Plays one of the library's sounds as the focused station wants it: the node's {@code feedback.sounds}, else the scene's,
     * else as it is; {@code false} there silences it.
     */
    private static void play(Cue cue, String node, SoundEvent sound, Vec3 at, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !FocusController.get().options().sounds()) {
            return;
        }
        SoundCue wanted = wanted(cue, node);
        if (wanted != null) {
            if (wanted.silent()) {
                return;
            }
            sound = wanted.sound().map(SoundEvent::createVariableRangeEvent).orElse(sound);
            volume *= wanted.volume();
            pitch *= wanted.pitch();
        }
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.BLOCKS, volume, pitch,
                minecraft.level.getRandom(), at.x, at.y, at.z));
    }

    private static SoundCue wanted(Cue cue, String node) {
        com.pockyl.diegesis.impl.client.ClientStation station = FocusController.get().station();
        com.pockyl.diegesis.impl.client.scene.SceneInstance scene = station == null ? null : station.scene();
        if (scene == null) {
            return null;
        }
        if (node != null) {
            com.pockyl.diegesis.impl.client.scene.NodeInstance instance = scene.node(node);
            SoundCue own = instance == null ? null : instance.definition().feedback().sounds().get(cue);
            if (own != null) {
                return own;
            }
        }
        return scene.definition().feedback().sounds().get(cue);
    }
}
