package com.pockyl.diegesis.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.debug.DebugView;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import com.pockyl.diegesis.impl.client.scene.SceneLibrary;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
        WorldRenderEvents.AFTER_ENTITIES.register(context -> SceneRenderer.render(context.matrixStack(), context.projectionMatrix(), context.camera()));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SceneReloadListener());
        PreparableModelLoadingPlugin.register(
                (ResourceManager manager, Executor executor) -> CompletableFuture.supplyAsync(() -> SceneLibrary.modelsToLoad(manager), executor),
                (Set<ResourceLocation> models, net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.Context context) -> {
                    context.addModels(models);
                    Set<ResourceLocation> hideable = new java.util.HashSet<>();
                    for (com.pockyl.diegesis.api.station.StationType type : com.pockyl.diegesis.impl.StationRegistry.all()) {
                        if (type.hidesBlock()) {
                            java.util.stream.Stream.concat(type.blocks().stream(), type.lateBlocks().stream())
                                    .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                                    .map(net.minecraft.client.renderer.block.BlockModelShaper::stateToModelLocation)
                                    .forEach(hideable::add);
                        }
                    }
                    context.modifyModelAfterBake().register((model, modify) ->
                            model != null && hideable.contains(modify.id()) ? new HideableFabricModel(model) : model);
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
