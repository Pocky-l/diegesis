package com.pockyl.diegesis.impl.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What an item placed freely looks like and how big it really is. Any item goes: a block item with a flat icon stands there as
 * its block, everything else is drawn as the game draws it in an item frame. The box is measured on the model, so that an item
 * stands on its lowest point whichever way it is turned, and an apple that fills half its icon is as big as a sword.
 */
public final class PlacedShape {
    /** Integers per vertex in a baked quad (position, colour, uv, light, normal). */
    private static final int VERTEX_STRIDE = 8;
    private static final float THINNEST = 0.012F;
    /** A block's longest side counts for less than a sword's: it fills all of its box. */
    private static final float SOLID_SIZE = 0.8F;
    private static final Map<BakedModel, PlacedShape> CACHE = new ConcurrentHashMap<>();

    private final BakedModel model;
    private final BlockState block;
    private final boolean translucent;
    private final Vector3f center;
    private final Vector3f half;
    private final float unit;
    private final int light;
    private final boolean flame;
    private final boolean custom;

    private PlacedShape(BakedModel model, BlockState block, Box3 box, int light, boolean flame) {
        this.model = model;
        this.block = block;
        this.translucent = block != null && ItemBlockRenderTypes.getChunkRenderType(block) == RenderType.translucent();
        this.center = new Vector3f(box.min).add(box.max).mul(0.5F);
        this.half = new Vector3f(box.max).sub(box.min).mul(0.5F).max(new Vector3f(THINNEST));
        float longest = Math.max(half.x, Math.max(half.y, half.z));
        boolean solid = Math.min(half.x, Math.min(half.y, half.z)) > longest * 0.6F;
        this.unit = (solid ? SOLID_SIZE : 1.0F) * 0.5F / longest;
        this.light = light;
        this.flame = flame;
        this.custom = model.isCustomRenderer();
    }

    /** Models are new after a resource reload. */
    static void clearCache() {
        CACHE.clear();
    }

    public static PlacedShape of(ItemStack stack, ClientLevel level, boolean blockModels) {
        BakedModel icon = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
        BlockState state = stack.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : null;
        if (state != null && state.hasProperty(BlockStateProperties.LIT)) {
            state = state.setValue(BlockStateProperties.LIT, true);
        }
        int light = state == null ? 0 : state.getLightEmission();
        boolean flame = state != null && (state.getBlock() instanceof TorchBlock || state.getBlock() instanceof AbstractCandleBlock);
        if (blockModels && standsAsBlock(stack, icon, state)) {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            BlockState shown = state;
            return CACHE.computeIfAbsent(model, key -> new PlacedShape(key, shown, blockBox(key, shown), light, flame));
        }
        return CACHE.computeIfAbsent(icon, key -> new PlacedShape(key, null, itemBox(key), light, flame));
    }

    /** Seeds, string and the like are items of a block that looks like nothing the player holds; doors are half a door. */
    private static boolean standsAsBlock(ItemStack stack, BakedModel icon, BlockState state) {
        return state != null && !icon.isGui3d() && !(stack.getItem() instanceof ItemNameBlockItem) && state.getRenderShape() == RenderShape.MODEL
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    private static Box3 blockBox(BakedModel model, BlockState state) {
        Box3 box = new Box3();
        quads(model, state, false, box::add);
        return box.orElse(0.0F, 1.0F);
    }

    /** The model's box after the item frame's transform, in the space {@link #draw} starts from. */
    private static Box3 itemBox(BakedModel model) {
        ItemTransform fixed = model.getTransforms().getTransform(ItemDisplayContext.FIXED);
        Matrix4f transform = new Matrix4f().translate(fixed.translation)
                .rotateXYZ((float) Math.toRadians(fixed.rotation.x()), (float) Math.toRadians(fixed.rotation.y()), (float) Math.toRadians(fixed.rotation.z()))
                .scale(fixed.scale).translate(-0.5F, -0.5F, -0.5F);
        Box3 model3 = new Box3();
        // A flat item's two big quads span the whole icon; the strips along its pixels' edges say what is really there.
        quads(model, null, !model.isGui3d(), model3::add);
        Box3 local = model3.orElse(0.0F, 1.0F);
        Box3 box = new Box3();
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = new Vector3f((corner & 1) == 0 ? local.min.x : local.max.x, (corner & 2) == 0 ? local.min.y : local.max.y,
                    (corner & 4) == 0 ? local.min.z : local.max.z);
            box.add(transform.transformPosition(point));
        }
        return box;
    }

    private static void quads(BakedModel model, BlockState state, boolean edgesOnly, java.util.function.Consumer<Vector3f> points) {
        RandomSource random = RandomSource.create(42L);
        Box3 edges = new Box3();
        Box3 all = new Box3();
        for (Direction side : new Direction[] {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (BakedQuad quad : model.getQuads(state, side, random)) {
                int[] vertices = quad.getVertices();
                boolean big = quad.getDirection().getAxis() == Direction.Axis.Z;
                for (int i = 0; i + 2 < vertices.length; i += VERTEX_STRIDE) {
                    Vector3f point = new Vector3f(Float.intBitsToFloat(vertices[i]), Float.intBitsToFloat(vertices[i + 1]), Float.intBitsToFloat(vertices[i + 2]));
                    all.add(point);
                    if (!big) {
                        edges.add(point);
                    }
                }
            }
        }
        Box3 used = edgesOnly && !edges.isEmpty() ? edges : all;
        if (!used.isEmpty()) {
            points.accept(used.min);
            points.accept(used.max);
        }
    }

    /** Light the item gives, like a block's. */
    public int light() {
        return light;
    }

    /** Whether the item burns at its top: a torch, a candle. */
    public boolean hasFlame() {
        return flame;
    }

    /** Whether the item can be flattened into a shadow: what an entity renderer draws is on textures of its own. */
    public boolean castsShadow() {
        return !custom;
    }

    /** The factor from the model to pixels for an item of {@code size} pixels. */
    public float modelScale(float size) {
        return size * unit;
    }

    /** Half the item's box in the model, for its hit box. */
    public Vector3fc half() {
        return half;
    }

    /** Half the box round the item in scene pixels, turned as given. */
    public Vector3f extents(Quaternionfc rotation, float size) {
        Matrix4f turn = new Matrix4f().rotation(new Quaternionf(rotation));
        float scale = modelScale(size);
        return new Vector3f(
                Math.abs(turn.m00()) * half.x + Math.abs(turn.m10()) * half.y + Math.abs(turn.m20()) * half.z,
                Math.abs(turn.m01()) * half.x + Math.abs(turn.m11()) * half.y + Math.abs(turn.m21()) * half.z,
                Math.abs(turn.m02()) * half.x + Math.abs(turn.m12()) * half.y + Math.abs(turn.m22()) * half.z).mul(scale);
    }

    /** Draws the item with its middle at the pose stack's origin; the stack is in model units. */
    public void draw(ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int packedLight, ClientLevel level, int seed) {
        Minecraft minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(-center.x, -center.y, -center.z);
        if (block != null) {
            VertexConsumer consumer = buffers.getBuffer(translucent ? Sheets.translucentCullBlockSheet() : Sheets.cutoutBlockSheet());
            int color = minecraft.getBlockColors().getColor(block, null, null, 0);
            float red = color == -1 ? 1.0F : (color >> 16 & 0xFF) / 255.0F;
            float green = color == -1 ? 1.0F : (color >> 8 & 0xFF) / 255.0F;
            float blue = color == -1 ? 1.0F : (color & 0xFF) / 255.0F;
            minecraft.getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), consumer, block, model, red, green, blue, packedLight,
                    OverlayTexture.NO_OVERLAY);
        } else {
            minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                    level, seed);
        }
        poseStack.popPose();
    }

    /** A growing box. */
    private static final class Box3 {
        final Vector3f min = new Vector3f(Float.MAX_VALUE);
        final Vector3f max = new Vector3f(-Float.MAX_VALUE);

        void add(Vector3fc point) {
            min.min(point);
            max.max(point);
        }

        boolean isEmpty() {
            return min.x > max.x;
        }

        Box3 orElse(float low, float high) {
            if (isEmpty()) {
                add(new Vector3f(low));
                add(new Vector3f(high));
            }
            return this;
        }
    }
}
