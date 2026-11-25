package com.pockyl.diegesis.impl.client.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What an item placed freely looks like and how big it really is. Any item goes: a block item with a flat icon stands there as
 * its block, everything else is drawn as the game draws it in an item frame. The box is measured on the model, so that an item
 * stands on its lowest point whichever way it is turned, and an apple that fills half its icon is as big as a sword.
 */
public final class PlacedShape implements Placing.Shape {
    private static final float THINNEST = 0.012F;
    /** A block's longest side counts for less than a sword's: it fills all of its box. */
    private static final float SOLID_SIZE = 0.8F;
    /** Shapes by what they are drawn from: a block's model, or an item's look (the game's identity of an item's models). */
    private static final Map<Object, PlacedShape> CACHE = new ConcurrentHashMap<>();
    /** Shapes of 3D item models standing as modelled (no frame transform). */
    private static final Map<Object, PlacedShape> MODELLED = new ConcurrentHashMap<>();
    /** Whether a block model draws any pixel. */
    private static final Map<BlockStateModel, Boolean> SHOWS = new ConcurrentHashMap<>();

    /** The block's model, for an item standing as its block; null for an item drawn as an item. */
    private final BlockStateModel model;
    private final BlockState block;
    private final boolean translucent;
    private final Vector3f center;
    private final Vector3f half;
    private final float unit;
    private final int light;
    private final boolean flame;
    /** How an item model is drawn: as an item frame shows it, or as it is modelled. */
    private final ItemDisplayContext context;
    /**
     * The hit box, round the same middle as {@link #half}: for a flat icon drawn on the slant (a carrot, a sword) the narrowest
     * box round its pixels, turned in the icon's plane by {@link #hitTurn}; otherwise the model's box itself.
     */
    private final Vector3f hitHalf;
    private final Quaternionf hitTurn;

    private PlacedShape(BlockStateModel model, BlockState block, Box3 box, int light, boolean flame, ItemDisplayContext context) {
        this(model, block, box, null, light, flame, context);
    }

    private PlacedShape(BlockStateModel model, BlockState block, Box3 box, Fit fit, int light, boolean flame, ItemDisplayContext context) {
        this.context = context;
        this.model = model;
        this.block = block;
        this.translucent = block != null && com.pockyl.diegesis.impl.client.render.SceneDraw.isTranslucent(model);
        this.center = new Vector3f(box.min).add(box.max).mul(0.5F);
        this.half = new Vector3f(box.max).sub(box.min).mul(0.5F).max(new Vector3f(THINNEST));
        float longest = Math.max(half.x, Math.max(half.y, half.z));
        boolean solid = Math.min(half.x, Math.min(half.y, half.z)) > longest * 0.6F;
        this.unit = (solid ? SOLID_SIZE : 1.0F) * 0.5F / longest;
        this.light = light;
        this.flame = flame;
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
        TrackingItemStackRenderState icon = new TrackingItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(icon, stack, ItemDisplayContext.FIXED, level, null, 0);
        // A 3D item model (a block's, a modelled thing's) is lit as a block; a flat icon is not. A special model (a banner, a shield)
        // is a thing too, though lit from the front: before Minecraft 1.21.4 it was a built-in entity model, 3D and custom drawn, and
        // stood as an item frame shows it; laid down as a flat icon, a banner lay upside down in the loom.
        boolean special = com.pockyl.diegesis.impl.client.render.SceneDraw.isSpecial(icon);
        boolean gui3d = icon.usesBlockLight() || special;
        BlockState state = stack.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : null;
        if (state != null && state.hasProperty(BlockStateProperties.LIT)) {
            state = state.setValue(BlockStateProperties.LIT, true);
        }
        int light = state == null ? 0 : state.getLightEmission();
        boolean flame = state != null && (state.getBlock() instanceof TorchBlock || state.getBlock() instanceof AbstractCandleBlock);
        if (blockModels && standsAsBlock(stack, gui3d, state)) {
            BlockStateModel model = com.pockyl.diegesis.impl.client.render.SceneDraw.blockModel(state);
            BlockState shown = state;
            return CACHE.computeIfAbsent(model, key -> new PlacedShape(model, shown, blockBox(model), light, flame, ItemDisplayContext.FIXED));
        }
        if (!framePose && gui3d && !special) {
            TrackingItemStackRenderState modelled = new TrackingItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(modelled, stack, ItemDisplayContext.NONE, level, null, 0);
            // An ArrayList, not List.copyOf: another mod's item model may add a null to the identity.
            return MODELLED.computeIfAbsent(new ArrayList<>((List<?>) modelled.getModelIdentity()), key -> new PlacedShape(null, null,
                    itemBox(measure(stack, ItemDisplayContext.NONE, level), false), light, flame, ItemDisplayContext.NONE));
        }
        return CACHE.computeIfAbsent(new ArrayList<>((List<?>) icon.getModelIdentity()), key -> {
            List<float[]> quads = measure(stack, ItemDisplayContext.FIXED, level);
            // A flat item's two big quads span the whole icon; the strips along its pixels' edges say what is really there.
            Box3 box = itemBox(quads, !gui3d);
            return new PlacedShape(null, null, box, gui3d ? null : flatFit(quads, box), light, flame, ItemDisplayContext.FIXED);
        });
    }

    /** Seeds, string and the like are items of a block that looks like nothing the player holds; doors are half a door. */
    private static boolean standsAsBlock(ItemStack stack, boolean gui3d, BlockState state) {
        // An item named as an item, not after its block (seeds, string), is one of those: since 1.21.4 it says so in its name.
        return state != null && !gui3d && !stack.getItem().getDescriptionId().startsWith("item.") && state.getRenderShape() == RenderShape.MODEL
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && shows(com.pockyl.diegesis.impl.client.render.SceneDraw.blockModel(state));
    }

    /**
     * Whether a block model draws anything: some mods give a block a fully transparent model and draw it with a block entity
     * renderer (Vinery's wine bottles), and such an item is shown by its icon.
     */
    private static boolean shows(BlockStateModel model) {
        return SHOWS.computeIfAbsent(model, key -> {
            boolean[] shown = {false};
            java.util.Set<TextureAtlasSprite> seen = new java.util.HashSet<>();
            com.pockyl.diegesis.impl.client.render.SceneDraw.forEachQuad(key, quad -> {
                if (!shown[0] && seen.add(quad.materialInfo().sprite()) && drawn(quad.materialInfo().sprite())) {
                    shown[0] = true;
                }
            });
            return shown[0];
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

    private static Box3 blockBox(BlockStateModel model) {
        Box3 box = new Box3();
        com.pockyl.diegesis.impl.client.render.SceneDraw.forEachQuad(model, quad -> {
            for (int vertex = 0; vertex < 4; vertex++) {
                box.add(quad.position(vertex));
            }
        });
        return box.orElse(0.0F, 1.0F);
    }

    /**
     * The item's quads as {@link #draw} draws it from an untouched pose (the space {@link #draw} starts from), each its four corners
     * and its facing: twelve coordinates and a normal.
     */
    private static List<float[]> measure(ItemStack stack, ItemDisplayContext context, ClientLevel level) {
        List<float[]> quads = new ArrayList<>();
        QuadCollector collector = new QuadCollector(quads);
        com.pockyl.diegesis.impl.client.render.SceneDraw.item(stack, context, 0, OverlayTexture.NO_OVERLAY, new PoseStack(),
                type -> collector, level, 0);
        collector.finish();
        return quads;
    }

    /**
     * Whether a measured quad is one of a flat icon's two big faces: facing as the biggest quad does. The rest are the strips along
     * the edges of its pixels, which trace what is really there.
     */
    private static boolean isBigFace(float[] quad, Vector3fc bigFacing) {
        return Math.abs(quad[12] * bigFacing.x() + quad[13] * bigFacing.y() + quad[14] * bigFacing.z()) > 0.99F;
    }

    /** The facing of the biggest of the quads. */
    private static Vector3f bigFacing(List<float[]> quads) {
        Vector3f best = new Vector3f(0.0F, 0.0F, 1.0F);
        float bestArea = -1.0F;
        for (float[] quad : quads) {
            Vector3f a = new Vector3f(quad[3] - quad[0], quad[4] - quad[1], quad[5] - quad[2]);
            Vector3f b = new Vector3f(quad[9] - quad[0], quad[10] - quad[1], quad[11] - quad[2]);
            float area = a.cross(b).length();
            if (area > bestArea) {
                bestArea = area;
                best.set(quad[12], quad[13], quad[14]);
            }
        }
        return best.lengthSquared() > 0.0F ? best.normalize() : best;
    }

    /** Collects the corners and facing of every quad written into it. */
    private static final class QuadCollector implements VertexConsumer {
        private final List<float[]> quads;
        private float[] quad = new float[15];
        private int corner;

        private QuadCollector(List<float[]> quads) {
            this.quads = quads;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (corner == 4) {
                finish();
            }
            quad[corner * 3] = x;
            quad[corner * 3 + 1] = y;
            quad[corner * 3 + 2] = z;
            corner++;
            return this;
        }

        /** Keeps the quad being written, if it is whole. */
        void finish() {
            if (corner == 4) {
                quads.add(quad);
            }
            quad = new float[15];
            corner = 0;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            quad[12] = x;
            quad[13] = y;
            quad[14] = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    /**
     * The narrowest box round a flat icon's pixels, round the middle of its {@code box} and turned about its thin side, when that
     * is clearly smaller than the box: most food is drawn on the slant, and its square box would keep things a pixel or two
     * apart where they do not touch. Null for an icon drawn square.
     */
    private static Fit flatFit(List<float[]> quads, Box3 box) {
        List<Vector3f> points = new ArrayList<>();
        Vector3f big = bigFacing(quads);
        for (float[] quad : quads) {
            if (isBigFace(quad, big)) {
                // The two big faces span the whole icon; the strips along the pixels' edges trace what is there.
                continue;
            }
            for (int corner = 0; corner < 4; corner++) {
                points.add(new Vector3f(quad[corner * 3], quad[corner * 3 + 1], quad[corner * 3 + 2]));
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

    /** The box round the measured quads; with {@code edgesOnly}, round all but a flat icon's two big faces where there are others. */
    private static Box3 itemBox(List<float[]> quads, boolean edgesOnly) {
        Vector3f big = bigFacing(quads);
        Box3 edges = new Box3();
        Box3 all = new Box3();
        for (float[] quad : quads) {
            boolean bigFace = isBigFace(quad, big);
            for (int corner = 0; corner < 4; corner++) {
                Vector3f point = new Vector3f(quad[corner * 3], quad[corner * 3 + 1], quad[corner * 3 + 2]);
                all.add(point);
                if (!bigFace) {
                    edges.add(point);
                }
            }
        }
        return (edgesOnly && !edges.isEmpty() ? edges : all).orElse(-0.5F, 0.5F);
    }

    /** Light the item gives, like a block's. */
    public int light() {
        return light;
    }

    /** Whether the item burns at its top: a torch, a candle. */
    public boolean hasFlame() {
        return flame;
    }

    /**
     * Whether the item can be flattened into a shadow. Every item can since Minecraft 26.x: what a special model draws (a chest, a
     * shield) comes through the scene's buffers like the rest.
     */
    public boolean castsShadow() {
        return true;
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
    public void draw(ItemStack stack, PoseStack poseStack, SceneBuffers buffers, int packedLight, ClientLevel level, int seed) {
        Minecraft minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(-center.x, -center.y, -center.z);
        if (block != null) {
            VertexConsumer consumer = buffers.getBuffer(translucent ? com.pockyl.diegesis.impl.client.render.DiegesisRenderTypes.translucentCullBlockSheet() : com.pockyl.diegesis.impl.client.render.DiegesisRenderTypes.cutoutBlockSheet());
            // The block's tints as an item of it shows them (grass green, not the grass of a biome).
            int[] tints = minecraft.getBlockColors().getTintSources(block).stream()
                    .mapToInt(source -> source == null ? -1 : source.color(block)).toArray();
            com.pockyl.diegesis.impl.client.render.BufferSubmits.putBlockModel(poseStack.last(), consumer,
                    com.pockyl.diegesis.impl.client.render.SceneDraw.parts(model), tints, -1, packedLight, OverlayTexture.NO_OVERLAY);
        } else {
            com.pockyl.diegesis.impl.client.render.SceneDraw.item(stack, context, packedLight, OverlayTexture.NO_OVERLAY, poseStack,
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
