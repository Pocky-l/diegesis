package com.pockyl.diegesis.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.api.client.ParticleStyle;
import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.scene.fx.EffectDefinition;
import com.pockyl.diegesis.api.scene.fx.EffectStep;
import com.pockyl.diegesis.api.scene.fx.FeedbackOptions;
import com.pockyl.diegesis.api.scene.fx.Marks;
import com.pockyl.diegesis.api.scene.fx.Reaction;
import com.pockyl.diegesis.api.scene.fx.SoundCue;
import com.pockyl.diegesis.impl.client.fx.FxLibrary;

import java.util.List;

/** Effect, particle and feedback files: what parses, what is an error, and that old scenes stay as they were. */
public final class FeedbackGameTests {
    private FeedbackGameTests() {
    }

    @GameTest(template = "empty")
    public static void effectsAndParticleStylesParse(GameTestHelper helper) {
        DataResult<EffectDefinition> effect = parse(EffectDefinition.CODEC, """
                {"steps": [
                  {"type": "particles", "style": "diegesis:dust", "count": 6, "spread": 1.0, "at": "point"},
                  {"type": "particles", "style": "diegesis:bubble", "where": "in_liquid", "at": "other"},
                  {"type": "sound", "id": "minecraft:block.stone.hit", "volume": 0.3, "pitch": 1.2, "pitch_by_speed": 0.3},
                  {"type": "light", "level": 12, "seconds": 0.3},
                  {"type": "shake", "pixels": 0.5},
                  {"type": "emit", "seconds": 2},
                  {"type": "ripple", "strength": 0.4}]}""");
        check(helper, effect.error().isEmpty(), "good effect: " + effect.error().map(Object::toString).orElse(""));
        List<EffectStep> steps = effect.result().orElseThrow().steps();
        check(helper, steps.size() == 7 && steps.get(0) instanceof EffectStep.Particles particles && particles.count() == 6
                && particles.at() == EffectStep.At.POINT && steps.get(6) instanceof EffectStep.Ripple, "steps read in order: " + steps);
        check(helper, parse(EffectDefinition.CODEC, "{\"steps\": [{\"type\": \"explode\"}]}").error().isPresent(), "unknown step type");
        check(helper, parse(EffectDefinition.CODEC, "{\"steps\": [{\"style\": \"diegesis:dust\"}]}").error().isPresent(), "step without type");
        check(helper, parse(EffectDefinition.CODEC, "{\"steps\": [{\"type\": \"sound\", \"id\": \"a:b\", \"volum\": 1}]}").error().isPresent(),
                "a typo in a step is an error");
        check(helper, parse(EffectDefinition.CODEC, "{\"steps\": [{\"type\": \"light\", \"level\": 20}]}").error().isPresent(), "light over 15");

        DataResult<ParticleStyle> style = parse(FxLibrary.PARTICLE_CODEC, """
                {"sprites": ["minecraft:flame"], "color": "#FFFF9040", "size": [0.2, 0.4], "life": [0.4, 0.9],
                 "gravity": -8.0, "drag": 0.9, "emissive": true, "wobble": 0.2}""");
        check(helper, style.error().isEmpty(), "good style: " + style.error().map(Object::toString).orElse(""));
        check(helper, style.result().map(s -> s.emissive() && s.fade() && s.color() == 0xFFFF9040).orElse(false), "style fields and defaults");
        DataResult<ParticleStyle> frames = parse(FxLibrary.PARTICLE_CODEC, "{\"sprites\": {\"base\": \"minecraft:spark\", \"count\": 8}}");
        check(helper, frames.result().map(s -> s.sprites().size() == 8
                && s.sprites().get(7).equals(Identifier.fromNamespaceAndPath("minecraft", "spark_7"))).orElse(false), "frames expand");
        check(helper, parse(FxLibrary.PARTICLE_CODEC, "{\"sprites\": [], \"gravity\": 1}").error().isPresent(), "no sprites");
        check(helper, parse(FxLibrary.PARTICLE_CODEC, "{\"sprites\": [\"minecraft:flame\"], \"glow\": true}").error().isPresent(),
                "unknown style field");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void feedbackParsesAndChangesNothingByDefault(GameTestHelper helper) {
        SceneDefinition old = parse(SceneDefinition.CODEC, "{\"nodes\": [{\"id\": \"a\"}]}").result().orElseThrow();
        check(helper, old.feedback().equals(FeedbackOptions.DEFAULT), "a scene without feedback has the defaults");
        check(helper, old.nodes().get(0).feedback().sounds().isEmpty() && old.nodes().get(0).feedback().marks().isEmpty(),
                "a node without feedback has none");

        DataResult<SceneDefinition> scene = parse(SceneDefinition.CODEC, """
                {"nodes": [{"id": "flame", "collider": {"from": [7, 9, 7], "to": [9, 12, 9]}, "trigger": true,
                            "feedback": {"marks": ["diegesis:fire"], "sounds": {"grab": false}}}],
                 "feedback": {
                   "sounds": {"put_down": "mymod:station.clink", "grab": {"id": "minecraft:block.glass.hit", "volume": 0.3, "pitch": 1.4},
                              "landing": false, "pour": {"pitch": 0.8}},
                   "impact_min_speed": 30,
                   "reactions": [
                     {"on": "exposed", "mark": "diegesis:fire", "effect": "mymod:ignite", "shared": true},
                     {"on": "contact", "other": "flame", "steps": [{"type": "particles", "style": "diegesis:flame"}], "cooldown": 0.5}]}}""");
        check(helper, scene.error().isEmpty(), "good feedback: " + scene.error().map(Object::toString).orElse(""));
        FeedbackOptions feedback = scene.result().orElseThrow().feedback();
        check(helper, feedback.sounds().get(FeedbackOptions.Cue.LANDING).silent(), "false silences");
        check(helper, feedback.sounds().get(FeedbackOptions.Cue.PUT_DOWN).sound()
                .filter(Identifier.fromNamespaceAndPath("mymod", "station.clink")::equals).isPresent(), "a sound id swaps");
        SoundCue pour = feedback.sounds().get(FeedbackOptions.Cue.POUR);
        check(helper, pour.sound().isEmpty() && pour.pitch() == 0.8F && pour.volume() == 1.0F, "an object without id only changes it");
        check(helper, feedback.impactMinSpeed() == 30.0F, "impact threshold");
        check(helper, feedback.reactions().size() == 2 && feedback.reactions().get(0).on() == Reaction.On.EXPOSED
                && feedback.reactions().get(1).cooldown() == 0.5F, "reactions: " + feedback.reactions());
        NodeDefinition flame = scene.result().orElseThrow().nodes().get(0);
        check(helper, flame.feedback().marks().equals(List.of(Marks.FIRE)) && flame.feedback().sounds().get(FeedbackOptions.Cue.GRAB).silent(),
                "node feedback");

        check(helper, parse(SceneDefinition.CODEC, "{\"feedback\": {\"sounds\": {\"grap\": false}}}").error().isPresent(), "unknown cue");
        check(helper, parse(SceneDefinition.CODEC, "{\"feedback\": {\"sounds\": {\"grab\": true}}}").error().isPresent(), "true is no sound");
        check(helper, parse(SceneDefinition.CODEC, "{\"feedback\": {\"reactions\": [{\"on\": \"click\"}]}}").error().isPresent(),
                "a reaction without an effect");
        check(helper, parse(SceneDefinition.CODEC, """
                {"feedback": {"reactions": [{"on": "click", "effect": "a:b", "steps": [{"type": "shake"}]}]}}""").error().isPresent(),
                "a reaction with both an effect and steps");
        check(helper, parse(SceneDefinition.CODEC, """
                {"feedback": {"reactions": [{"on": "click", "shared": true, "steps": [{"type": "shake"}]}]}}""").error().isPresent(),
                "a shared reaction names its effect");
        check(helper, parse(SceneDefinition.CODEC, "{\"feedback\": {\"reactions\": [{\"on\": \"explode\", \"effect\": \"a:b\"}]}}")
                .error().isPresent(), "unknown event");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void marksComeFromTheNodeAndItsLiquid(GameTestHelper helper) {
        SceneDefinition scene = parse(SceneDefinition.CODEC, """
                {"nodes": [
                  {"id": "flame", "collider": {"from": [7, 9, 7], "to": [9, 12, 9]}, "trigger": true,
                   "feedback": {"marks": ["diegesis:fire", "mymod:blessed"]}},
                  {"id": "flask", "tank": {"tank": "flask", "inner": {"from": [6, 1, 6], "to": [10, 8, 10]}},
                   "feedback": {"marks": ["diegesis:hot"]}},
                  {"id": "plain"}]}""").result().orElseThrow();
        NodeDefinition flame = scene.nodes().get(0);
        NodeDefinition flask = scene.nodes().get(1);
        NodeDefinition plain = scene.nodes().get(2);
        Identifier lava = Identifier.fromNamespaceAndPath("minecraft", "lava");
        Identifier water = Identifier.fromNamespaceAndPath("minecraft", "water");
        check(helper, Marks.of(flame, null).equals(java.util.Set.of(Marks.FIRE, Identifier.fromNamespaceAndPath("mymod", "blessed"))), "own marks");
        check(helper, Marks.of(flask, com.pockyl.diegesis.api.state.TankContents.EMPTY).equals(java.util.Set.of(Marks.HOT)),
                "an empty tank is not wet");
        check(helper, Marks.of(flask, com.pockyl.diegesis.api.state.TankContents.of(water, 100).plus(
                com.pockyl.diegesis.api.state.TankContents.of(lava, 10))).equals(java.util.Set.of(Marks.HOT, Marks.WET, water, lava)),
                "a filled tank is wet with each of its liquids");
        check(helper, Marks.of(plain, com.pockyl.diegesis.api.state.TankContents.of(water, 100)).isEmpty(), "no tank, no liquid marks");
        helper.succeed();
    }

    private static <T> DataResult<T> parse(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static void check(GameTestHelper helper, boolean condition, String what) {
        if (!condition) {
            helper.fail(what);
        }
    }
}
