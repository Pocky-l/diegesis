package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The library's own render types (a subclass only to reach the protected render state shards). */
final class DiegesisRenderTypes extends RenderType {
    /**
     * Light falling on a surface: the colour drawn multiplies what is already there and adds it back
     * ({@code dst * (1 + src)}), so wood gets brighter and warmer with its grain intact instead of hazed over.
     */
    private static final TransparencyStateShard LIGHTEN = new TransparencyStateShard("diegesis_lighten", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    private DiegesisRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling, boolean sort,
            Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    /** A render type from state shards, built without {@code RenderType.create} (not public in plain Minecraft). */
    private static RenderType of(String name, VertexFormat format, boolean sort, List<RenderStateShard> shards) {
        return new DiegesisRenderTypes(name, format, VertexFormat.Mode.QUADS, 256, false, sort,
                () -> shards.forEach(RenderStateShard::setupRenderState), () -> shards.forEach(RenderStateShard::clearRenderState));
    }

    /** A pool of light on a surface; the texture's colour is the light's falloff (black outside). */
    static RenderType lightPool(ResourceLocation texture) {
        return of("diegesis_light_pool", DefaultVertexFormat.NEW_ENTITY, false, List.of(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER,
                new RenderStateShard.TextureStateShard(texture, false, false), LIGHTEN, NO_CULL, LEQUAL_DEPTH_TEST, COLOR_WRITE,
                POLYGON_OFFSET_LAYERING));
    }

    /**
     * An entity fading out of the way of the camera: like the translucent entity type, but it writes no depth, so that what is drawn
     * after it behind it (a scene) is not cut away by something nobody should see any more.
     */
    static RenderType ghostEntity(ResourceLocation texture) {
        return of("diegesis_ghost", DefaultVertexFormat.NEW_ENTITY, true, List.of(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER,
                new RenderStateShard.TextureStateShard(texture, false, false), TRANSLUCENT_TRANSPARENCY, CULL, LIGHTMAP, OVERLAY, LEQUAL_DEPTH_TEST,
                COLOR_WRITE));
    }

    /**
     * See-through things that stand in each other: glass, the liquid in it, a stream running through. Like the block atlas'
     * translucent entity type, sorted back to front, but it writes no depth: a big pane cannot cut away what is sorted
     * after it, however the two lie. A polygon offset settles it for what lies on something solid, a glass standing on a table
     * or a liquid against the walls of a sink: the depth test would flicker between the two, now the see-through layer wins.
     */
    static RenderType clearBlockSheet() {
        return of("diegesis_clear", DefaultVertexFormat.NEW_ENTITY, true, List.of(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER,
                new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false), TRANSLUCENT_TRANSPARENCY, CULL, LIGHTMAP,
                OVERLAY, LEQUAL_DEPTH_TEST, COLOR_WRITE, POLYGON_OFFSET_LAYERING));
    }
}
