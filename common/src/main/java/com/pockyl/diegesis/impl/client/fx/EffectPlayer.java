package com.pockyl.diegesis.impl.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.ParticleStyle;
import com.pockyl.diegesis.api.scene.Box;
import com.pockyl.diegesis.api.scene.fx.EffectDefinition;
import com.pockyl.diegesis.api.scene.fx.EffectStep;
import com.pockyl.diegesis.impl.client.ClientRegistry;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;

/**
 * Plays an {@link EffectDefinition} at a station through the station's own particle, sound and node calls: no drawing of its
 * own. The event's other node, point and speed come in the effect's data ({@link #OTHER}, {@link #X}/{@link #Y}/{@link #Z},
 * {@link #SPEED}), so an effect sent by the server or shared by another player plays the same.
 */
public final class EffectPlayer {
    /** Data key: the other node of the event (what was touched, landed on). */
    public static final String OTHER = "other";
    /** Data keys: the event's point in scene pixels. */
    public static final String X = "x";
    public static final String Y = "y";
    public static final String Z = "z";
    /** Data key: the event's speed, pixels a second. */
    public static final String SPEED = "speed";

    private EffectPlayer() {
    }

    /** Effect data carrying an event's other node, point and speed; any of them may be missing. */
    public static CompoundTag data(String other, Vector3fc point, float speed) {
        CompoundTag data = new CompoundTag();
        if (other != null) {
            data.putString(OTHER, other);
        }
        if (point != null) {
            data.putFloat(X, point.x());
            data.putFloat(Y, point.y());
            data.putFloat(Z, point.z());
        }
        if (speed > 0.0F) {
            data.putFloat(SPEED, speed);
        }
        return data;
    }

    public static void play(ClientStation station, EffectDefinition effect, String node, CompoundTag data) {
        if (station.scene() == null) {
            return;
        }
        String other = data.contains(OTHER, Tag.TAG_STRING) ? data.getString(OTHER) : null;
        Vector3f point = data.contains(X, Tag.TAG_ANY_NUMERIC) ? new Vector3f(data.getFloat(X), data.getFloat(Y), data.getFloat(Z)) : null;
        float speed = data.getFloat(SPEED);
        for (EffectStep step : effect.steps()) {
            String target = target(step, node, other);
            if (step instanceof EffectStep.Particles particles) {
                particles(station, particles, target, point, data);
            } else if (step instanceof EffectStep.Sound sound) {
                sound(station, sound, target, point, speed);
            } else if (step instanceof EffectStep.Light light) {
                NodeInstance instance = node(station, target);
                if (instance != null) {
                    instance.flare(light.level(), light.seconds());
                }
            } else if (step instanceof EffectStep.Shake shake) {
                NodeInstance instance = node(station, target);
                if (instance != null) {
                    instance.shake(shake.pixels(), shake.seconds());
                }
            } else if (step instanceof EffectStep.Emit emit) {
                NodeInstance instance = node(station, target);
                if (instance != null) {
                    instance.emitFor(emit.seconds());
                }
            } else if (step instanceof EffectStep.Ripple ripple) {
                ripple(station, ripple, target, point);
            }
        }
    }

    /** The node a step acts on: the effect's node, or the event's other one. */
    private static String target(EffectStep step, String node, String other) {
        EffectStep.At at;
        if (step instanceof EffectStep.Particles particles) {
            at = particles.at();
        } else if (step instanceof EffectStep.Sound sound) {
            at = sound.at();
        } else if (step instanceof EffectStep.Light light) {
            at = light.at();
        } else if (step instanceof EffectStep.Shake shake) {
            at = shake.at();
        } else if (step instanceof EffectStep.Emit emit) {
            at = emit.at();
        } else {
            at = ((EffectStep.Ripple) step).at();
        }
        return at == EffectStep.At.OTHER && other != null ? other : node;
    }

    private static NodeInstance node(ClientStation station, String id) {
        return id == null || station.scene() == null ? null : station.scene().node(id);
    }

    /** Where a step happens in scene pixels: the event's point for {@code at: point}, else the node, else the station's middle. */
    private static Vector3f place(ClientStation station, EffectStep.At at, String target, Vector3fc point) {
        if (at == EffectStep.At.POINT && point != null) {
            return new Vector3f(point);
        }
        NodeInstance instance = node(station, target);
        if (instance != null) {
            return instance.matrix().getTranslation(new Vector3f());
        }
        return new Vector3f(8.0F, station.scene().definition().workPlane(), 8.0F);
    }

    private static void particles(ClientStation station, EffectStep.Particles step, String target, Vector3fc point, CompoundTag data) {
        int color = step.color().orElse(data.contains("color", Tag.TAG_ANY_NUMERIC) ? data.getInt("color") : 0);
        Vector3f velocity = step.velocity().map(Vector3f::new).orElse(null);
        switch (step.where()) {
            case IN_LIQUID -> station.spawnInLiquid(step.style(), target, step.count(), color, velocity != null ? velocity : new Vector3f());
            case ON_LIQUID -> station.spawnOnLiquid(step.style(), target, step.count(), color, velocity != null ? velocity : new Vector3f(0, 2, 0));
            case LIQUID_EDGE -> station.spawnAtLiquidEdge(step.style(), target, step.count(), color, 1.0F);
            default -> {
                Vector3f at = place(station, step.at(), target, point).add(step.offset());
                if (velocity == null) {
                    // As a burst at a node: falling kinds (sparks, drops) burst upwards and arc; light ones drift.
                    ParticleStyle style = ClientRegistry.particle(step.style());
                    velocity = new Vector3f(0.0F, style != null && style.gravity() > 20.0F ? 16.0F : 2.0F, 0.0F);
                }
                station.spawnParticles(step.style(), at, velocity, step.spread(), step.count(), color);
            }
        }
    }

    private static void sound(ClientStation station, EffectStep.Sound step, String target, Vector3fc point, float speed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vector3f at = place(station, step.at(), target, point);
        Vec3 world = station.toWorld(at.x, at.y, at.z);
        float pitch = Math.max(0.1F, step.pitch() * (1.0F + step.pitchBySpeed() * speed / 100.0F));
        minecraft.getSoundManager().play(new SimpleSoundInstance(SoundEvent.createVariableRangeEvent(step.sound()), SoundSource.BLOCKS,
                step.volume(), pitch, minecraft.level.getRandom(), world.x, world.y, world.z));
    }

    private static void ripple(ClientStation station, EffectStep.Ripple step, String target, Vector3fc point) {
        NodeInstance instance = node(station, target);
        if (instance == null || instance.definition().tank().isEmpty()) {
            return;
        }
        Box inner = instance.definition().tank().get().inner();
        float x = (inner.from().x() + inner.to().x()) * 0.5F;
        float z = (inner.from().z() + inner.to().z()) * 0.5F;
        if (point != null) {
            Vector3f local = new Matrix4f(instance.matrix()).invert().transformPosition(new Vector3f(point));
            x = local.x;
            z = local.z;
        }
        instance.ripple(x, z, step.strength());
    }
}
