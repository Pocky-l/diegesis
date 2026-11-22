package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import com.pockyl.diegesis.mixin.client.RenderPipelinesAccessor;
import com.pockyl.diegesis.mixin.client.RenderTypeAccessor;

import java.util.function.Function;

/**
 * The library's own render types, and the game's it draws scenes with. Since Minecraft 26.x a render type is a pipeline (shaders,
 * blending, depth) and a setup (textures, lightmap, layering); the library's pipelines are the game's entity pipelines with a
 * change or two. Minecraft 26.1.2 has no improved transparency (order-independent pipelines): see-through types sort on upload.
 */
public final class DiegesisRenderTypes {
    /** The entity pipelines' depth test (Minecraft 26.1.2 keeps depth the classic way: nearer is less; 26.2 reversed it). */
    private static final CompareOp DEPTH_TEST = CompareOp.LESS_THAN_OR_EQUAL;

    /**
     * Light falling on a surface: the colour drawn multiplies what is already there and adds it back
     * ({@code dst * (1 + src)}), so wood gets brighter and warmer with its grain intact instead of hazed over. Drawn on both sides,
     * tested against the depth but writing none.
     */
    private static final RenderPipeline LIGHT_POOL = RenderPipeline.builder(RenderPipelinesAccessor.diegesis$entityEmissiveSnippet())
            .withLocation(Identifier.fromNamespaceAndPath("diegesis", "pipeline/light_pool"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ONE)))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(DEPTH_TEST, false))
            .build();
    /** The translucent entity pipeline (culled), writing no depth. */
    private static final RenderPipeline TRANSLUCENT_CULL_NO_DEPTH = RenderPipeline.builder(RenderPipelinesAccessor.diegesis$entitySnippet())
            .withLocation(Identifier.fromNamespaceAndPath("diegesis", "pipeline/entity_translucent_cull_no_depth"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DEPTH_TEST, false))
            .build();
    /** The translucent entity pipeline (not culled), writing no depth. */
    private static final RenderPipeline TRANSLUCENT_NO_DEPTH = RenderPipeline.builder(RenderPipelinesAccessor.diegesis$entitySnippet())
            .withLocation(Identifier.fromNamespaceAndPath("diegesis", "pipeline/entity_translucent_no_depth"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(DEPTH_TEST, false))
            .build();

    private static final Function<Identifier, RenderType> LIGHT_POOL_TYPE = Util.memoize(texture -> create("diegesis_light_pool",
            RenderSetup.builder(LIGHT_POOL)
                    .withTexture("Sampler0", texture)
                    .useOverlay()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()));
    private static final Function<Identifier, RenderType> GHOST_TYPE = Util.memoize(texture -> create("diegesis_ghost",
            RenderSetup.builder(TRANSLUCENT_NO_DEPTH)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup()));
    /** The game's translucent culled entity type as 26.2 and later have it (26.1.2 has it for the item target only). */
    private static final Function<Identifier, RenderType> TRANSLUCENT_CULL_TYPE = Util.memoize(texture -> create(
            "diegesis_entity_translucent_cull", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));
    private static final RenderType ITEM = create("diegesis_item", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
            .useLightmap()
            .useOverlay()
            .sortOnUpload()
            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
            .createRenderSetup());
    private static final RenderType CLEAR = create("diegesis_clear", RenderSetup.builder(TRANSLUCENT_CULL_NO_DEPTH)
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
            .useLightmap()
            .useOverlay()
            .sortOnUpload()
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
            .createRenderSetup());

    static {
        // A shader pack draws the game's entity pipelines with its own programs; these are those pipelines with a change or two.
        ShaderPipelines.assign(LIGHT_POOL, "EMISSIVE_ENTITIES");
        ShaderPipelines.assign(TRANSLUCENT_CULL_NO_DEPTH, "ENTITIES_TRANSLUCENT");
        ShaderPipelines.assign(TRANSLUCENT_NO_DEPTH, "ENTITIES_TRANSLUCENT");
    }

    private DiegesisRenderTypes() {
    }

    private static RenderType create(String name, RenderSetup setup) {
        return RenderTypeAccessor.diegesis$create(name, setup);
    }

    /** A pool of light on a surface; the texture's colour is the light's falloff (black outside). */
    static RenderType lightPool(Identifier texture) {
        return LIGHT_POOL_TYPE.apply(texture);
    }

    /**
     * An entity fading out of the way of the camera: like the translucent entity type, but it writes no depth, so that what is drawn
     * after it behind it (a scene) is not cut away by something nobody should see any more.
     */
    public static RenderType ghostEntity(Identifier texture) {
        return GHOST_TYPE.apply(texture);
    }

    /**
     * Items in a scene: the block atlas' translucent entity type, which writes depth as the game's does. The scene draws that type
     * as {@link #clearBlockSheet()}, where an item's own faces have no depth between them.
     */
    static RenderType itemSheet() {
        return ITEM;
    }

    /**
     * See-through things that stand in each other: glass, the liquid in it, a stream running through. Like the block atlas'
     * translucent entity type, sorted back to front, but it writes no depth: a big pane cannot cut away what is sorted
     * after it, however the two lie. A polygon offset settles it for what lies on something solid, a glass standing on a table
     * or a liquid against the walls of a sink: the depth test would flicker between the two, now the see-through layer wins.
     */
    static RenderType clearBlockSheet() {
        return CLEAR;
    }

    /** Block-atlas geometry drawn like an entity, cut out: what {@code Sheets.cutoutBlockSheet()} was before Minecraft 26.x. */
    public static RenderType cutoutBlockSheet() {
        return RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    /** Block-atlas geometry drawn like an entity, see-through and culled: {@code Sheets.translucentCullBlockSheet()} before 26.x. */
    public static RenderType translucentCullBlockSheet() {
        return entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
    }

    /**
     * {@code texture} drawn like an entity, see-through and culled, into the main target: what {@code RenderTypes.entityTranslucentCull}
     * is on Minecraft 26.2 and later.
     */
    public static RenderType entityTranslucentCull(Identifier texture) {
        return TRANSLUCENT_CULL_TYPE.apply(texture);
    }

    /** Block-atlas geometry drawn like an entity, solid: {@code Sheets.solidBlockSheet()} before Minecraft 26.x. */
    public static RenderType solidBlockSheet() {
        return RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);
    }
}
