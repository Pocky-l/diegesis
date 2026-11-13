package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A block model drawn lit as the world lights the station's block ({@code Visual.litAsBlock}): the faces shaded as a block's
 * are, the light of the blocks around, and smooth lighting's shadows in the corners. A scene's copy of a block it hides then
 * looks like the block itself, and nothing changes as the two swap.
 *
 * <p>A model standing square to the world, as such a copy does, is drawn the way the chunks draw blocks, its faces turned to
 * the world's sides first so that each is lit from its own side. One turned some other way (a lid set aside, leaning) has no
 * corner shadows: each face takes the shade of the way it faces and the light in front of it.
 */
final class BlockLit {
    private static final int STRIDE = DefaultVertexFormat.BLOCK.getIntegerSize();
    private static final int NORMAL = 7;
    private static final float SQUARE = 0.9999F;
    /** The model turned by a quarter, a half and three quarters, made when first needed; dropped with the models. */
    private static final Map<BakedModel, BakedModel[]> TURNED = new WeakHashMap<>();

    private BlockLit() {
    }

    /**
     * Draws {@code model} at the pose (one unit a block, the model's corner at the origin) into {@code consumer}, of a chunk render
     * type, lit as {@code state} at {@code pos} would be.
     *
     * @param toWorld the pose's turn into the world: the level's view turned back
     */
    static void draw(BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, Matrix4f toWorld) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Matrix4f world = new Matrix4f(toWorld).mul(poseStack.last().pose());
        Vector3f x = world.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
        Vector3f y = world.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        boolean upright = y.y > SQUARE;
        boolean square = upright && (Math.abs(x.x) > SQUARE || Math.abs(x.z) > SQUARE);
        if (square) {
            // The turn the pose gives the model, by quarters about the world's up; the model is turned so instead and the pose
            // turned back, so that the faces lie towards the world's sides they face.
            int quarters = Math.floorMod(Math.round((float) Math.atan2(-x.z, x.x) / Mth.HALF_PI), 4);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.last().pose().rotateY(-quarters * Mth.HALF_PI);
            poseStack.last().normal().rotateY(-quarters * Mth.HALF_PI);
            poseStack.translate(-0.5F, 0.0F, -0.5F);
            minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(level, turned(model, quarters), state, pos, poseStack, consumer,
                    false, RandomSource.create(), 42L, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            return;
        }
        RandomSource random = RandomSource.create();
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(state, side, random)) {
                Vector3f normal = world.transformDirection(new Vector3f(quad.getDirection().step())).normalize();
                float shade = quad.isShade() ? diffuse(normal) : 1.0F;
                // The light of the block the face looks into, or of the station's own where that one is solid.
                BlockPos lit = pos.relative(Direction.getNearest(normal.x, normal.y, normal.z));
                int light = LevelRenderer.getLightColor(level, level.getBlockState(lit).isSolidRender(level, lit) ? pos : lit);
                consumer.putBulkData(poseStack.last(), quad, shade, shade, shade, light, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            null};

    /** The shade the world gives a face by the way it faces: 1 up, 0.5 down, 0.8 north and south, 0.6 east and west, between. */
    private static float diffuse(Vector3f normal) {
        float up = normal.y * normal.y * (3.0F + normal.y) / 4.0F;
        return Math.min(1.0F, normal.x * normal.x * 0.6F + up + normal.z * normal.z * 0.8F);
    }

    private static BakedModel turned(BakedModel model, int quarters) {
        if (quarters == 0) {
            return model;
        }
        BakedModel[] turns = TURNED.computeIfAbsent(model, key -> new BakedModel[4]);
        if (turns[quarters] == null) {
            turns[quarters] = new Turned(model, quarters);
        }
        return turns[quarters];
    }

    /** A model turned by quarters about the up through the middle of its block, its quads' sides with it. */
    private static final class Turned implements BakedModel {
        private final BakedModel model;
        private final int quarters;
        private final Map<Direction, List<BakedQuad>> sides = new EnumMap<>(Direction.class);
        private List<BakedQuad> unsided;

        private Turned(BakedModel model, int quarters) {
            this.model = model;
            this.quarters = quarters;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            if (side == null) {
                if (unsided == null) {
                    unsided = turn(model.getQuads(state, null, random));
                }
                return unsided;
            }
            return sides.computeIfAbsent(side, key -> turn(model.getQuads(state, turnBack(key), random)));
        }

        private Direction turnBack(Direction side) {
            Direction back = side;
            for (int i = 0; i < quarters; i++) {
                back = back.getAxis().isHorizontal() ? back.getClockWise() : back;
            }
            return back;
        }

        private List<BakedQuad> turn(List<BakedQuad> quads) {
            List<BakedQuad> turned = new ArrayList<>(quads.size());
            float cos = Mth.cos(quarters * Mth.HALF_PI);
            float sin = Mth.sin(quarters * Mth.HALF_PI);
            for (BakedQuad quad : quads) {
                int[] data = quad.getVertices().clone();
                for (int vertex = 0; vertex < data.length / STRIDE; vertex++) {
                    int at = vertex * STRIDE;
                    float x = Float.intBitsToFloat(data[at]) - 0.5F;
                    float z = Float.intBitsToFloat(data[at + 2]) - 0.5F;
                    data[at] = Float.floatToRawIntBits(Math.round((cos * x + sin * z) * 1.0e5F) / 1.0e5F + 0.5F);
                    data[at + 2] = Float.floatToRawIntBits(Math.round((-sin * x + cos * z) * 1.0e5F) / 1.0e5F + 0.5F);
                    int normal = data[at + NORMAL];
                    float nx = (byte) normal;
                    float nz = (byte) (normal >> 16);
                    int tx = Math.round(cos * nx + sin * nz);
                    int tz = Math.round(-sin * nx + cos * nz);
                    data[at + NORMAL] = normal & 0xFF00FF00 | tx & 0xFF | (tz & 0xFF) << 16;
                }
                Vector3f facing = new Vector3f(quad.getDirection().step());
                Direction side = Direction.getNearest(cos * facing.x + sin * facing.z, facing.y, -sin * facing.x + cos * facing.z);
                turned.add(new BakedQuad(data, quad.getTintIndex(), side, quad.getSprite(), quad.isShade()));
            }
            return turned;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return model.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return model.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return model.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return model.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return model.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return model.getOverrides();
        }
    }
}
