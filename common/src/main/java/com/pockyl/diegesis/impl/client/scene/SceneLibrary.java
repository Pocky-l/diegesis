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
@SuppressWarnings("removal")
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
            ResourceLocation id = new ResourceLocation(file.getNamespace(), path);
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
            ResourceLocation id = new ResourceLocation(file.getNamespace(),
                    file.getPath().substring(dir.length() + 1, file.getPath().length() - ".json".length()));
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
     * The block models every scene may use without registering them: those of {@code models/diegesis/} whose textures and
     * parents are there, plus the ones registered in code. Read while the models load (before the scenes).
     *
     * <p>An addon keeps the models of stations for many other mods there; a model naming the textures of a mod that is not
     * here would only fill the log with missing textures and parents, and its station never comes. So a file is left out when
     * a texture or a parent model it names, or a parent of it in the same folder names, is in no pack. Asked of the files
     * themselves, not of the namespaces: an addon may serve a file or two of another mod's namespace (a blockstate) while that
     * mod is away. Each file is judged by what it names, not by the station it is for: a model that stations of several mods
     * share, made of the game's textures, always loads.
     */
    public static Set<ResourceLocation> modelsToLoad(ResourceManager manager) {
        Map<ResourceLocation, ModelNames> files = new HashMap<>();
        Set<ResourceLocation> unread = new HashSet<>();
        manager.listResources(MODEL_DIR, path -> path.getPath().endsWith(".json")).forEach((file, resource) -> {
            ResourceLocation id = new ResourceLocation(file.getNamespace(),
                    file.getPath().substring("models/".length(), file.getPath().length() - ".json".length()));
            ModelNames names = ModelNames.read(resource);
            if (names != null) {
                files.put(id, names);
            } else {
                unread.add(id);
            }
        });
        Map<ResourceLocation, Boolean> found = new HashMap<>();
        Set<ResourceLocation> models = new HashSet<>(unread);
        for (ResourceLocation id : files.keySet()) {
            if (canLoad(id, files, manager, found, new HashSet<>())) {
                models.add(id);
            } else {
                DiegesisMod.LOGGER.debug("Diegesis model {} names a texture or a model that is not here; not loaded", id);
            }
        }
        int total = files.size() + unread.size();
        if (models.size() < total) {
            DiegesisMod.LOGGER.info("Left out {} of {} Diegesis models: they name textures or models of mods that are not here",
                    total - models.size(), total);
        }
        models.addAll(ClientRegistry.extraModels());
        return models;
    }

    /** Whether the textures and parents model {@code id} names are there, and those of its parents in the folder. */
    private static boolean canLoad(ResourceLocation id, Map<ResourceLocation, ModelNames> files, ResourceManager manager,
            Map<ResourceLocation, Boolean> found, Set<ResourceLocation> seen) {
        ModelNames names = files.get(id);
        if (names == null) {
            // Outside the folder (or a file that cannot be read, which loads as before and the game reports).
            return id.getPath().startsWith("builtin/") || there(manager, id.withPrefix("models/").withSuffix(".json"), found);
        }
        // A parent named twice in a chain is the game's to report.
        if (!seen.add(id)) {
            return true;
        }
        for (ResourceLocation texture : names.textures()) {
            if (!there(manager, texture.withPrefix("textures/").withSuffix(".png"), found)) {
                return false;
            }
        }
        for (ResourceLocation parent : names.parents()) {
            if (!canLoad(parent, files, manager, found, seen)) {
                return false;
            }
        }
        return true;
    }

    private static boolean there(ResourceManager manager, ResourceLocation file, Map<ResourceLocation, Boolean> found) {
        return found.computeIfAbsent(file, key -> manager.getResource(key).isPresent());
    }

    /** The textures a model file names, and its parents, in the file and in models nested in it. */
    private record ModelNames(Set<ResourceLocation> textures, Set<ResourceLocation> parents) {
        /** Null when the file cannot be read: it is loaded as before. */
        static ModelNames read(Resource resource) {
            try (Reader reader = resource.openAsReader()) {
                ModelNames names = new ModelNames(new HashSet<>(), new HashSet<>());
                names.collect(JsonParser.parseReader(reader));
                return names;
            } catch (Exception e) {
                return null;
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
            JsonObject object = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (entry.getKey().equals("parent") && isString(value)) {
                    ResourceLocation parent = ResourceLocation.tryParse(value.getAsString());
                    if (parent != null) {
                        parents.add(parent);
                    }
                } else if (entry.getKey().equals("textures") && value.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> texture : value.getAsJsonObject().entrySet()) {
                        // "#name" points at another texture of the model.
                        if (isString(texture.getValue()) && !texture.getValue().getAsString().startsWith("#")) {
                            ResourceLocation location = ResourceLocation.tryParse(texture.getValue().getAsString());
                            if (location != null) {
                                textures.add(location);
                            }
                        }
                    }
                } else {
                    collect(value);
                }
            }
        }

        private static boolean isString(JsonElement json) {
            return json.isJsonPrimitive() && json.getAsJsonPrimitive().isString();
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
