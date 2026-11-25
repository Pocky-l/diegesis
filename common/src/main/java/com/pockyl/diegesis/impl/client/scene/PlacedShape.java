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
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
public final class PlacedShape implements Placing.Shape {
    /** Integers per vertex in a baked quad (position, colour, uv, light, normal). */
    private static final int VERTEX_STRIDE = 8;
    private static final float THINNEST = 0.012F;
    /** A block's longest side counts for less than a sword's: it fills all of its box. */
    private static final float SOLID_SIZE = 0.8F;
    private static final Map<BakedModel, PlacedShape> CACHE = new ConcurrentHashMap<>();
    /** Shapes of 3D item models standing as modelled (no frame transform). */
    private static final Map<BakedModel, PlacedShape> MODELLED = new ConcurrentHashMap<>();
    /** Whether a block model draws any pixel. */
    private static final Map<BakedModel, Boolean> SHOWS = new ConcurrentHashMap<>();

    private final BakedModel model;
    private final BlockState block;
    private final boolean translucent;
    private final Vector3f center;
    private final Vector3f half;
    private final float unit;
    private final int light;
    private final boolean flame;
    private final boolean custom;
    /** How an item model is drawn: as an item frame shows it, or as it is modelled. */
    private final ItemDisplayContext context;
    /**
     * The hit box, round the same middle as {@link #half}: for a flat icon drawn on the slant (a carrot, a sword) the narrowest
     * box round its pixels, turned in the icon's plane by {@link #hitTurn}; otherwise the model's box itself.
     */
    private final Vector3f hitHalf;
    private final Quaternionf hitTurn;

    private PlacedShape(BakedModel model, BlockState block, Box3 box, int light, boolean flame, ItemDisplayContext context) {
        this(model, block, box, null, light, flame, context);
    }

    private PlacedShape(BakedModel model, BlockState block, Box3 box, Fit fit, int light, boolean flame, ItemDisplayContext context) {
        this.context = context;
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
        this.hitHalf = fit == null ? new Vector3f(half) : new Vector3f(fit.half()).max(new Vector3f(THINNEST));
        this.hitTurn = fit == null ? new Quaternionf() : new Quaternionf(fit.turn());
    }

    /** A turned hit box: how it is turned in the model, and half its size along its own sides. */
    private record Fit(Quaternionf turn, Vector3f half) {
    }

    /** Degrees between the turns tried for a flat icon's hit box. */
    private static final int FIT_STEP = 1;
    /** A turned box is kept only when it is at least this much smaller than the model's own box. */
    private static final float FIT_GAIN = 0.9F;
    /** How far a turned hit box may reach past the model's box, as a factor of it. */
    private static final float FIT_OVER = 1.03F;

    /** Models are new after a resource reload. */
    static void clearCache() {
        CACHE.clear();
        MODELLED.clear();
        SHOWS.clear();
    }

    /** As {@link #of(ItemStack, ClientLevel, boolean, boolean)}, every item as an item frame shows it. */
    public static PlacedShape of(ItemStack stack, ClientLevel level, boolean blockModels) {
        return of(stack, level, blockModels, true);
    }

    /**
     * @param framePose 3D item models too stand as an item frame shows them; otherwise as they are modelled (a frame lays a can on
     *                  its back so that it faces out of the wall)
     */
    public static PlacedShape of(ItemStack stack, ClientLevel level, boolean blockModels, boolean framePose) {
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
            return CACHE.computeIfAbsent(model, key -> new PlacedShape(key, shown, blockBox(key, shown), light, flame, ItemDisplayContext.FIXED));
        }
        if (!framePose && icon.isGui3d() && !icon.isCustomRenderer()) {
            return MODELLED.computeIfAbsent(icon, key -> new PlacedShape(key, null, itemBox(key, ItemDisplayContext.NONE), light, flame,
                    ItemDisplayContext.NONE));
        }
        return CACHE.computeIfAbsent(icon, key -> {
            Box3 box = itemBox(key, ItemDisplayContext.FIXED);
            return new PlacedShape(key, null, box, key.isGui3d() ? null : flatFit(key, ItemDisplayContext.FIXED, box), light, flame,
                    ItemDisplayContext.FIXED);
        });
    }

    /** Seeds, string and the like are items of a block that looks like nothing the player holds; doors are half a door. */
    private static boolean standsAsBlock(ItemStack stack, BakedModel icon, BlockState state) {
        return state != null && !icon.isGui3d() && !(stack.getItem() instanceof ItemNameBlockItem) && state.getRenderShape() == RenderShape.MODEL
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && shows(Minecraft.getInstance().getBlockRenderer().getBlockModel(state), state);
    }

    /**
     * Whether a block model draws anything: some mods give a block a fully transparent model and draw it with a block entity
     * renderer (Vinery's wine bottles), and such an item is shown by its icon.
     */
    private static boolean shows(BakedModel model, BlockState state) {
        return SHOWS.computeIfAbsent(model, key -> {
            RandomSource random = RandomSource.create(42L);
            java.util.Set<TextureAtlasSprite> seen = new java.util.HashSet<>();
            for (Direction side : new Direction[] {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                for (BakedQuad quad : key.getQuads(state, side, random)) {
                    if (seen.add(quad.getSprite()) && drawn(quad.getSprite())) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    /** Whether a sprite has a pixel that is not fully transparent. */
    private static boolean drawn(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        for (int y = 0; y < contents.height(); y++) {
            for (int x = 0; x < contents.width(); x++) {
                if (!contents.isTransparent(0, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Box3 blockBox(BakedModel model, BlockState state) {
        Box3 box = new Box3();
        quads(model, state, false, box::add);
        return box.orElse(0.0F, 1.0F);
    }

    /** The transform of {@code context} (the item frame's, or none) from the model into the space {@link #draw} starts from. */
    private static Matrix4f transformOf(BakedModel model, ItemDisplayContext context) {
        ItemTransform fixed = model.getTransforms().getTransform(context);
        return new Matrix4f().translate(fixed.translation)
                .rotateXYZ((float) Math.toRadians(fixed.rotation.x()), (float) Math.toRadians(fixed.rotation.y()), (float) Math.toRadians(fixed.rotation.z()))
                .scale(fixed.scale).translate(-0.5F, -0.5F, -0.5F);
    }

    /**
     * The narrowest box round a flat icon's pixels, round the middle of its {@code box} and turned about its thin side, when that
     * is clearly smaller than the box: most food is drawn on the slant, and its square box would keep things a pixel or two
     * apart where they do not touch. Null for an icon drawn square.
     */
    private static Fit flatFit(BakedModel model, ItemDisplayContext context, Box3 box) {
        Matrix4f transform = transformOf(model, context);
        java.util.List<Vector3f> points = new java.util.ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        for (Direction side : new Direction[] {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                if (quad.getDirection().getAxis() == Direction.Axis.Z) {
                    // The two big faces span the whole icon; the strips along the pixels' edges trace what is there.
                    continue;
                }
                int[] vertices = quad.getVertices();
                for (int i = 0; i + 2 < vertices.length; i += VERTEX_STRIDE) {
                    points.add(transform.transformPosition(new Vector3f(Float.intBitsToFloat(vertices[i]), Float.intBitsToFloat(vertices[i + 1]),
                            Float.intBitsToFloat(vertices[i + 2]))));
                }
            }
        }
        if (points.size() < 3 || box.isEmpty()) {
            return null;
        }
        Vector3f size = new Vector3f(box.max).sub(box.min);
        Vector3f middle = new Vector3f(box.min).add(box.max).mul(0.5F);
        int thin = size.x <= size.y && size.x <= size.z ? 0 : size.y <= size.z ? 1 : 2;
        int a = thin == 0 ? 1 : 0;
        int b = thin == 2 ? 1 : 2;
        float squareArea = size.get(a) * size.get(b) / 4.0F;
        float bestArea = squareArea;
        float bestAngle = 0.0F;
        float bestU = size.get(a) / 2.0F;
        float bestV = size.get(b) / 2.0F;
        for (int degrees = FIT_STEP; degrees < 90; degrees += FIT_STEP) {
            float angle = (float) Math.toRadians(degrees);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float most = 0.0F;
            float mostV = 0.0F;
            for (Vector3f point : points) {
                float du = point.get(a) - middle.get(a);
                float dv = point.get(b) - middle.get(b);
                most = Math.max(most, Math.abs(cos * du + sin * dv));
                mostV = Math.max(mostV, Math.abs(-sin * du + cos * dv));
            }
            if (most * mostV < bestArea) {
                bestArea = most * mostV;
                bestAngle = angle;
                bestU = most;
                bestV = mostV;
            }
        }
        if (bestAngle == 0.0F || bestArea > squareArea * FIT_GAIN) {
            return null;
        }
        // Its corners must not stick out of the icon's own box below or above, or it would stand on them a hair over the shelf: its long side
        // is cut shorter (the tips of the icon may reach a little past it) and, if still needed, its short side.
        float cos = (float) Math.cos(bestAngle);
        float sin = (float) Math.sin(bestAngle);
        // Only up and down: sideways a corner past the icon's box costs nothing.
        float roomA = a == 1 ? size.get(a) / 2.0F * FIT_OVER : Float.MAX_VALUE;
        float roomB = b == 1 ? size.get(b) / 2.0F * FIT_OVER : Float.MAX_VALUE;
        if (bestU >= bestV) {
            bestU = Math.max(0.0F, Math.min(bestU, Math.min((roomA - sin * bestV) / cos, (roomB - cos * bestV) / sin)));
            bestV = Math.max(0.0F, Math.min(bestV, Math.min((roomA - cos * bestU) / sin, (roomB - sin * bestU) / cos)));
        } else {
            bestV = Math.max(0.0F, Math.min(bestV, Math.min((roomA - cos * bestU) / sin, (roomB - sin * bestU) / cos)));
            bestU = Math.max(0.0F, Math.min(bestU, Math.min((roomA - sin * bestV) / cos, (roomB - cos * bestV) / sin)));
        }
        if (bestU <= 0.0F || bestV <= 0.0F) {
            return null;
        }
        Vector3f half = new Vector3f();
        half.setComponent(a, bestU);
        half.setComponent(b, bestV);
        half.setComponent(thin, size.get(thin) / 2.0F);
        // The box's own sides: its first side along (cos, sin) in the icon's plane, its second square to it, the thin side as is.
        org.joml.Matrix3f sides = new org.joml.Matrix3f();
        Vector3f first = new Vector3f();
        first.setComponent(a, (float) Math.cos(bestAngle));
        first.setComponent(b, (float) Math.sin(bestAngle));
        Vector3f second = new Vector3f();
        second.setComponent(a, (float) -Math.sin(bestAngle));
        second.setComponent(b, (float) Math.cos(bestAngle));
        Vector3f across = new Vector3f();
        across.setComponent(thin, 1.0F);
        sides.setColumn(a, first);
        sides.setColumn(b, second);
        sides.setColumn(thin, across);
        if (sides.determinant() < 0.0F) {
            // Kept a turn, not a mirror.
            sides.setColumn(thin, across.negate());
        }
        return new Fit(new Quaternionf().setFromNormalized(sides), half);
    }

    /** The model's box after the transform of {@code context} (the item frame's, or none), in the space {@link #draw} starts from. */
    private static Box3 itemBox(BakedModel model, ItemDisplayContext context) {
        Matrix4f transform = transformOf(model, context);
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

    /** Whether the item is an icon a pixel thin (bread, a carrot), not a thing with a body (a block, a 3D model). */
    @Override
    public boolean isFlat() {
        float longest = Math.max(half.x, Math.max(half.y, half.z));
        return block == null && Math.min(half.x, Math.min(half.y, half.z)) < longest * 0.2F;
    }

    /** Half the item's box in the model, for its hit box. */
    public Vector3fc half() {
        return half;
    }

    /** Half the hit box in the model, along its own sides (see {@link #hitTurn()}). */
    public Vector3fc hitHalf() {
        return hitHalf;
    }

    /** How the hit box is turned in the model: about the thin side of a flat icon drawn on the slant, else not at all. */
    public Quaternionfc hitTurn() {
        return hitTurn;
    }

    /** Its size for the physics: the hit box, and the factor from the model to pixels per pixel of size. */
    public PlacedPhysics.Solid solid() {
        return new PlacedPhysics.Solid(hitHalf, hitTurn, unit, isFlat());
    }

    /** Half the box round the item in scene pixels, turned as given. */
    @Override
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
            minecraft.getItemRenderer().renderStatic(stack, context, packedLight, OverlayTexture.NO_OVERLAY, poseStack,
                    com.pockyl.diegesis.impl.client.render.SceneRenderer.solidItems(buffers), level, seed);
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
