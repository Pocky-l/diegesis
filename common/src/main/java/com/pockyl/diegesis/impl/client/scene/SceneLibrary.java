package com.pockyl.diegesis.impl.client.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.impl.client.ClientRegistry;
import com.pockyl.diegesis.impl.client.ClientStations;
import com.pockyl.diegesis.impl.scene.NodeTemplates;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the scene files, {@code assets/<namespace>/diegesis/scenes/<path>.json}, with the resource packs: F3+T reloads
 * them while the game runs, so a scene can be tuned without restarting. A broken file is reported in the log with the
 * reason, and its station shows nothing until it is fixed.
 */
public final class SceneLibrary extends SimplePreparableReloadListener<Map<ResourceLocation, SceneDefinition>> {
    public static final SceneLibrary INSTANCE = new SceneLibrary();
    public static final ResourceLocation ID = DiegesisMod.id("scenes");
    private static final String SCENE_DIR = "diegesis/scenes";
    private static final String MODEL_DIR = "models/diegesis";

    private volatile Map<ResourceLocation, SceneDefinition> scenes = Map.of();
    private volatile Map<ResourceLocation, com.pockyl.diegesis.impl.client.mesh.Mesh> preparedMeshes = Map.of();
    private volatile com.pockyl.diegesis.impl.client.fx.FxLibrary.Prepared preparedFx;

    private SceneLibrary() {
    }

    /** The scene for an id: a file, else one registered in code. */
    public SceneDefinition scene(ResourceLocation id) {
        SceneDefinition scene = scenes.get(id);
        return scene != null ? scene : ClientRegistry.codeScene(id).orElse(null);
    }

    @Override
    protected Map<ResourceLocation, SceneDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, SceneDefinition> loaded = new HashMap<>();
        preparedMeshes = com.pockyl.diegesis.impl.client.mesh.MeshLibrary.load(manager);
        preparedFx = com.pockyl.diegesis.impl.client.fx.FxLibrary.load(manager);
        Map<ResourceLocation, JsonObject> templates = loadTemplates(manager);
        Map<ResourceLocation, Resource> files = manager.listResources(SCENE_DIR, path -> path.getPath().endsWith(".json"));
        files.forEach((file, resource) -> {
            String path = file.getPath().substring(SCENE_DIR.length() + 1, file.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                DataResult<SceneDefinition> result = NodeTemplates.expand(json, templates::get)
                        .flatMap(expanded -> SceneDefinition.CODEC.parse(JsonOps.INSTANCE, expanded));
                result.resultOrPartial(error -> DiegesisMod.LOGGER.error("Scene {} ({}) is broken: {}", id, file, error))
                        .filter(scene -> result.error().isEmpty())
                        .ifPresent(scene -> loaded.put(id, scene));
            } catch (Exception e) {
                DiegesisMod.LOGGER.error("Could not read scene {} ({})", id, file, e);
            }
        });
        return loaded;
    }

    /** The node templates, {@code assets/<namespace>/diegesis/nodes/<path>.json}. */
    private static Map<ResourceLocation, JsonObject> loadTemplates(ResourceManager manager) {
        Map<ResourceLocation, JsonObject> templates = new HashMap<>();
        String dir = NodeTemplates.DIR;
        manager.listResources(dir, path -> path.getPath().endsWith(".json")).forEach((file, resource) -> {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), file.getPath().substring(dir.length() + 1, file.getPath().length() - ".json".length()));
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (json.isJsonObject()) {
                    templates.put(id, json.getAsJsonObject());
                } else {
                    DiegesisMod.LOGGER.error("Node template {} ({}) is not a JSON object", id, file);
                }
            } catch (Exception e) {
                DiegesisMod.LOGGER.error("Could not read node template {} ({})", id, file, e);
            }
        });
        return templates;
    }

    @Override
    protected void apply(Map<ResourceLocation, SceneDefinition> loaded, ResourceManager manager, ProfilerFiller profiler) {
        scenes = Map.copyOf(loaded);
        com.pockyl.diegesis.impl.client.mesh.MeshLibrary.INSTANCE.apply(preparedMeshes);
        if (preparedFx != null) {
            com.pockyl.diegesis.impl.client.fx.FxLibrary.apply(preparedFx);
        }
        DefaultColliders.clearCache();
        PlacedShape.clearCache();
        com.pockyl.diegesis.impl.client.mesh.ItemShapes.clearCache();
        DiegesisMod.LOGGER.info("Loaded {} Diegesis scenes, {} meshes, {} effects, {} particle styles", loaded.size(), preparedMeshes.size(),
                com.pockyl.diegesis.impl.client.fx.FxLibrary.effectCount(), com.pockyl.diegesis.impl.client.fx.FxLibrary.particleCount());
        ClientStations.get().onScenesReloaded();
    }

    /**
     * The block models every scene may use without registering them: all of {@code models/diegesis/} that can be built here,
     * plus the ones registered in code. Read while the models load (before the scenes).
     *
     * <p>An addon may ship models for the blocks of mods a player may not have: such a model names those mods' textures or
     * parent models, and loaded without them it only filled the log with warnings (a station of a missing mod never draws
     * it). A file is left out when a texture or parent model it names, or that a parent among these files names, is not in
     * the resources. The files are looked for, not their namespaces: a pack may claim another mod's namespace whether that
     * mod is there or not (Hands-On Delight's block states do). A file that cannot be read is loaded as before: the game
     * reports what is wrong with it.
     */
    public static Set<ResourceLocation> modelsToLoad(ResourceManager manager) {
        Map<ResourceLocation, Names> listed = new HashMap<>();
        manager.listResources(MODEL_DIR, path -> path.getPath().endsWith(".json"))
                .forEach((file, resource) -> listed.put(modelId(file), Names.of(file, resource)));
        Map<ResourceLocation, Boolean> found = new HashMap<>();
        Set<ResourceLocation> models = new HashSet<>();
        int absent = 0;
        for (ResourceLocation model : listed.keySet()) {
            if (complete(model, listed, manager, found, new HashSet<>())) {
                models.add(model);
            } else {
                absent++;
            }
        }
        if (absent > 0) {
            DiegesisMod.LOGGER.info("Left out {} scene models whose textures or parent models are not installed", absent);
        }
        models.addAll(ClientRegistry.extraModels());
        return models;
    }

    private static ResourceLocation modelId(ResourceLocation file) {
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                file.getPath().substring("models/".length(), file.getPath().length() - ".json".length()));
    }

    /** Whether every texture and parent model {@code model} names is there, through its parents among the listed files. */
    private static boolean complete(ResourceLocation model, Map<ResourceLocation, Names> listed, ResourceManager manager,
            Map<ResourceLocation, Boolean> found, Set<ResourceLocation> seen) {
        Names names = listed.get(model);
        if (names == null || !seen.add(model)) {
            return true;
        }
        for (ResourceLocation texture : names.textures()) {
            if (!found.computeIfAbsent(texture.withPath(path -> "textures/" + path + ".png"), file -> manager.getResource(file).isPresent())) {
                return false;
            }
        }
        for (ResourceLocation parent : names.parents()) {
            boolean there = listed.containsKey(parent) ? complete(parent, listed, manager, found, seen)
                    : parent.getPath().startsWith("builtin/")
                    || found.computeIfAbsent(parent.withPath(path -> "models/" + path + ".json"), file -> manager.getResource(file).isPresent());
            if (!there) {
                return false;
            }
        }
        return true;
    }

    /** What a model file names: its textures and parent models, nested objects too. */
    private record Names(Set<ResourceLocation> textures, Set<ResourceLocation> parents) {
        private static final Names UNREAD = new Names(Set.of(), Set.of());

        static Names of(ResourceLocation file, Resource resource) {
            Names names = new Names(new HashSet<>(), new HashSet<>());
            try (Reader reader = resource.openAsReader()) {
                names.collect(JsonParser.parseReader(reader));
                return names;
            } catch (Exception e) {
                DiegesisMod.LOGGER.debug("Could not read scene model {} to see which mods it needs", file, e);
                return UNREAD;
            }
        }

        private void collect(JsonElement json) {
            if (json.isJsonArray()) {
                json.getAsJsonArray().forEach(this::collect);
                return;
            }
            if (!json.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (entry.getKey().equals("parent") && value.isJsonPrimitive()) {
                    ResourceLocation parent = ResourceLocation.tryParse(value.getAsString());
                    if (parent != null) {
                        parents.add(parent);
                    }
                } else if (entry.getKey().equals("textures") && value.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> texture : value.getAsJsonObject().entrySet()) {
                        String name = texture.getValue().isJsonPrimitive() ? texture.getValue().getAsString() : "#";
                        ResourceLocation id = name.startsWith("#") ? null : ResourceLocation.tryParse(name);
                        if (id != null) {
                            textures.add(id);
                        }
                    }
                } else {
                    collect(value);
                }
            }
        }
    }

    /** Every model id the loaded scenes use, for warnings about models that were not loaded. */
    public List<ResourceLocation> usedModels() {
        return scenes.values().stream().flatMap(scene -> {
            java.util.List<ResourceLocation> ids = new java.util.ArrayList<>();
            scene.forEachNode(node -> {
                node.visual().flatMap(v -> v.model()).ifPresent(ids::add);
                node.control().flatMap(c -> c.base()).flatMap(v -> v.model()).ifPresent(ids::add);
            });
            return ids.stream();
        }).distinct().toList();
    }
}
