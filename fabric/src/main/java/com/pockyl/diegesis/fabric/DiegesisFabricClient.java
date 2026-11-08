package com.pockyl.diegesis.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.diegesis.impl.StationRegistry;
import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public final class DiegesisFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DiegesisClientRuntime.init();
        FabricNetwork.registerClient();
        ClientTickEvents.END_CLIENT_TICK.register(client -> DiegesisClientRuntime.tick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommandManager.literal("diegesis").then(ClientCommandManager.literal("debug").executes(command -> {
                    DebugView.toggle();
                    return 1;
                }))));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> SceneRenderer.render(context.matrixStack(), context.positionMatrix(),
                context.projectionMatrix(), context.camera()));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SceneReloadListener());
        PreparableModelLoadingPlugin.register(
                (ResourceManager manager, Executor executor) -> CompletableFuture.supplyAsync(() -> SceneLibrary.modelsToLoad(manager), executor),
                (Set<ResourceLocation> models, ModelLoadingPlugin.Context context) -> {
                    context.addModels(models);
                    Set<ModelResourceLocation> hideable = new HashSet<>();
                    for (StationType type : StationRegistry.all()) {
                        if (type.hidesBlock()) {
                            Stream.concat(type.blocks().stream(), type.lateBlocks().stream())
                                    .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                                    .map(BlockModelShaper::stateToModelLocation)
                                    .forEach(hideable::add);
                        }
                    }
                    context.modifyModelAfterBake().register((model, modify) -> model != null && modify.topLevelId() != null
                            && hideable.contains(modify.topLevelId()) ? new HideableFabricModel(model) : model);
                });
    }

    /** The scene library under Fabric's reload listener id. */
    private static final class SceneReloadListener implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return SceneLibrary.ID;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller preparationsProfiler,
                ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
            return SceneLibrary.INSTANCE.reload(barrier, manager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor);
        }

        @Override
        public String getName() {
            return SceneLibrary.ID.toString();
        }
    }
}
