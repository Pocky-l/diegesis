package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import com.pockyl.diegesis.api.client.SceneBuffers;

import java.util.ArrayList;
import java.util.List;

/**
 * What a scene draws that the game no longer draws into a buffer of the caller's own since Minecraft 26.x: items, block models and
 * text, written into {@link SceneBuffers} the way the game's own renderers write them.
 */
public final class SceneDraw {
    private static final int[] NO_TINTS = new int[0];
    /** The item's look, filled anew for each item drawn (the render thread only). */
    private static final ItemStackRenderState ITEM = new ItemStackRenderState();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<BlockStateModelPart> PARTS = new ArrayList<>();

    private SceneDraw() {
    }

    /** A sprite of the block atlas, by its id ({@code minecraft:block/stone}); the missing sprite when there is none. */
    public static net.minecraft.client.renderer.texture.TextureAtlasSprite blockSprite(net.minecraft.resources.Identifier id) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getSprite(id);
    }

    /**
     * A texture a scene names for a shape or a mesh, by its id: the block atlas', or, since Minecraft 26.x keeps item textures in an
     * atlas of their own, the item atlas' ({@code minecraft:item/apple}); the missing sprite when neither has it.
     */
    public static net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(net.minecraft.resources.Identifier id) {
        var atlases = Minecraft.getInstance().getAtlasManager();
        var blocks = atlases.getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
        var sprite = blocks.getSprite(id);
        if (sprite != blocks.missingSprite()) {
            return sprite;
        }
        var items = atlases.getAtlasOrThrow(net.minecraft.data.AtlasIds.ITEMS);
        var item = items.getSprite(id);
        return item != items.missingSprite() ? item : sprite;
    }

    /**
     * The render type to draw {@code sprite} with like an entity: the block sheet's own types (cut out, or see-through and culled) for
     * the block atlas, the same kinds of the item atlas for an item texture.
     */
    public static net.minecraft.client.renderer.rendertype.RenderType sheet(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            boolean translucent) {
        net.minecraft.resources.Identifier atlas = sprite.atlasLocation();
        if (atlas.equals(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)) {
            return translucent ? DiegesisRenderTypes.translucentCullBlockSheet() : DiegesisRenderTypes.cutoutBlockSheet();
        }
        return translucent ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentCull(atlas)
                : net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(atlas);
    }

    /** A block state's model, as the chunks draw it. */
    public static BlockStateModel blockModel(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }

    /** The model drawn where a model is missing. */
    public static BlockStateModel missingModel() {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().missingModel();
    }

    /** Whether any face of the model is see-through (the chunks would draw it with the translucent layer). */
    public static boolean isTranslucent(BlockStateModel model) {
        return model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT);
    }

    /** The model's parts, as the chunks pick them (the same seed for every block: the scene's copy looks the same each frame). */
    public static List<BlockStateModelPart> parts(BlockStateModel model) {
        PARTS.clear();
        RANDOM.setSeed(42L);
        model.collectParts(RANDOM, PARTS);
        return PARTS;
    }

    /** Every quad of the model's parts, each side and the rest. */
    public static void forEachQuad(BlockStateModel model, java.util.function.Consumer<BakedQuad> action) {
        for (BlockStateModelPart part : parts(model)) {
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                part.getQuads(side).forEach(action);
            }
            part.getQuads(null).forEach(action);
        }
    }

    /** A block model in white light: its quads with the given light, as a block entity draws a block. */
    public static void model(PoseStack.Pose pose, VertexConsumer consumer, BlockStateModel model, int light, int overlay) {
        BufferSubmits.putBlockModel(pose, consumer, parts(model), NO_TINTS, -1, light, overlay);
    }

    /** An item as a frame or a held item shows it, into the scene's buffers (glint, special models and all). */
    public static void item(ItemStack stack, ItemDisplayContext context, int light, int overlay, PoseStack poseStack, SceneBuffers buffers,
            @Nullable Level level, int seed) {
        if (stack.isEmpty()) {
            return;
        }
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(ITEM, stack, context, level, null, seed);
        ITEM.submit(poseStack, buffers.submits(), light, overlay, 0);
    }

    /** The item's look as {@link #item} draws it, for measuring it; filled anew by the next call. */
    public static ItemStackRenderState itemState(ItemStack stack, ItemDisplayContext context, @Nullable Level level, int seed) {
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(ITEM, stack, context, level, null, seed);
        return ITEM;
    }

    /**
     * Whether the item is a thing rather than a flat icon, as {@code BakedModel.isGui3d} said before Minecraft 1.21.4: a model lit as a
     * block, or a special model (a banner, a shield), which was a built-in entity model then. Lighting alone calls a banner flat.
     */
    public static boolean isGui3d(ItemStack stack, ItemDisplayContext context, @Nullable Level level, int seed) {
        if (stack.isEmpty()) {
            return false;
        }
        // A fresh state: the tracking one keeps adding to its identity over updates.
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
        return state.usesBlockLight() || isSpecial(state);
    }

    /** Whether an item's look comes from a special model renderer (a banner, a shield, a chest), from its model identity. */
    public static boolean isSpecial(TrackingItemStackRenderState state) {
        for (Object element : (List<?>) state.getModelIdentity()) {
            if (element instanceof SpecialModelWrapper<?>) {
                return true;
            }
        }
        return false;
    }

    /** A line of text, as {@code Font.drawInBatch} drew it into a buffer source before Minecraft 26.x. */
    public static void text(SceneBuffers buffers, String text, float x, float y, int color, Matrix4fc matrix, Font.DisplayMode mode,
            int light) {
        text(buffers, text, x, y, color, matrix, mode, 0, light);
    }

    /** A line of text over a background of {@code background} (ARGB; 0 for none), as a name tag has. */
    public static void text(SceneBuffers buffers, String text, float x, float y, int color, Matrix4fc matrix, Font.DisplayMode mode,
            int background, int light) {
        Minecraft.getInstance().font.prepareText(text, x, y, color, false, background).visit(new Font.GlyphVisitor() {
            @Override
            public void acceptRenderable(TextRenderable renderable) {
                renderable.render(matrix, buffers.getBuffer(renderable.renderType(mode)), light, false);
            }
        });
    }
}
