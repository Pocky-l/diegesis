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
     * The depth of a block fading out of the camera's way ({@code ClearBlocks}), drawn before its colour: only its nearest faces
     * then show, as a whole thing turning see-through rather than a heap of faces seen through each other. Cut-out holes (leaves,
     * a trapdoor's bars) stay holes.
     */
    static RenderType fadingBlockDepth() {
        return FADING_BLOCK_DEPTH;
    }

    /** The colour of a block fading out of the camera's way, on its own depth ({@link #fadingBlockDepth}); alpha from the vertices. */
    static RenderType fadingBlock() {
        return FADING_BLOCK;
    }

    /**
     * A block fading out of the camera's way under a shader pack: the block atlas' sorted translucent entity type. Drawn with the
     * terrain's translucent program, the packs give the faces a reflection that grows towards the edges and lift their alpha
     * with it (Complementary, BSL): a block nearly gone came back as a dark mirror before the camera. Their entity program
     * keeps the alpha, as it does for the entities that fade there.
     */
    static RenderType fadingBlockUnderPack() {
        return FADING_BLOCK_UNDER_PACK;
    }

    private static final RenderType FADING_BLOCK_UNDER_PACK = entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType FADING_BLOCK_DEPTH = of("diegesis_fading_block_depth", DefaultVertexFormat.BLOCK, false,
            List.of(RENDERTYPE_CUTOUT_SHADER, BLOCK_SHEET_MIPPED, NO_TRANSPARENCY, CULL, LIGHTMAP, LEQUAL_DEPTH_TEST, DEPTH_WRITE));
    private static final RenderType FADING_BLOCK = of("diegesis_fading_block", DefaultVertexFormat.BLOCK, false,
            List.of(RENDERTYPE_TRANSLUCENT_SHADER, BLOCK_SHEET_MIPPED, TRANSLUCENT_TRANSPARENCY, CULL, LIGHTMAP, LEQUAL_DEPTH_TEST,
                    COLOR_WRITE));

    /**
     * Items in a scene: the block atlas' translucent entity type the item renderer asks for, which writes depth as the game's
     * does. The scene draws that type as {@link #clearBlockSheet()}, where an item's own faces have no depth between them.
     */
    static RenderType itemSheet() {
        return of("diegesis_item", DefaultVertexFormat.NEW_ENTITY, true, List.of(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER,
                new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false), TRANSLUCENT_TRANSPARENCY, CULL, LIGHTMAP,
                OVERLAY, LEQUAL_DEPTH_TEST, COLOR_DEPTH_WRITE));
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
