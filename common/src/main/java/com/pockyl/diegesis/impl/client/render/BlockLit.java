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
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
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
 * corner shadows: each face takes the shade of the way it faces and the light in front of it, where it stands now: a door
 * swinging out of a cupboard is lit by the room its face looks into, not by the cupboard or the wall beside it.
 */
final class BlockLit {
    private static final int STRIDE = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
    private static final int NORMAL = 7;
    private static final float SQUARE = 0.9999F;
    /** How far (blocks) in front of a turned face its light is taken: clear of the face, well short of the next block. */
    private static final float IN_FRONT = 0.1F;
    /** The model turned by a quarter, a half and three quarters, made when first needed; dropped with the models. */
    private static final Map<BakedModel, BakedModel[]> TURNED = new WeakHashMap<>();

    private BlockLit() {
    }

    /**
     * Draws {@code model} at the pose (one unit a block, the model's corner at the origin) into {@code consumer}, of a chunk render
     * type, lit as {@code state} at {@code pos} would be.
     *
     * @param toWorld the pose's turn into the world: the level's view turned back
     * @param camera  where the pose's origin is in the world (the camera), or null: a turned face is then lit from {@code pos}
     */
    static void draw(BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, Matrix4f toWorld,
            Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        BlockAndTintGetter meshed = AsMeshed.of(level);
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
            minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(meshed, turned(model, quarters), state, pos, poseStack, consumer,
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
                int light = faceLight(meshed, quad, world, camera, normal, pos);
                consumer.putBulkData(poseStack.last(), quad, shade, shade, shade, 1.0F, light, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    /**
     * The light of a turned face: of the block just in front of its middle, where it stands now. Where that one is solid (no
     * light inside), or the face's place is not known, the block {@code pos} looks into that way, or {@code pos} itself. A block
     * in the camera's way or a hidden station block is open there ({@link OpenLight}).
     */
    private static int faceLight(BlockAndTintGetter level, BakedQuad quad, Matrix4f world, Vec3 camera, Vector3f normal, BlockPos pos) {
        if (camera != null) {
            int[] data = quad.getVertices();
            int vertices = data.length / STRIDE;
            Vector3f middle = new Vector3f();
            for (int vertex = 0; vertex < vertices; vertex++) {
                int at = vertex * STRIDE;
                middle.add(Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]), Float.intBitsToFloat(data[at + 2]));
            }
            world.transformPosition(middle.div(Math.max(1, vertices)));
            BlockPos front = BlockPos.containing(camera.x + middle.x + normal.x * IN_FRONT, camera.y + middle.y + normal.y * IN_FRONT,
                    camera.z + middle.z + normal.z * IN_FRONT);
            if (open(level, front)) {
                return LevelRenderer.getLightColor(level, front);
            }
        }
        BlockPos lit = pos.relative(Direction.getNearest(normal.x, normal.y, normal.z));
        return LevelRenderer.getLightColor(level, open(level, lit) ? lit : pos);
    }

    private static boolean open(BlockAndTintGetter level, BlockPos pos) {
        return OpenLight.isOpen(pos) || !level.getBlockState(pos).isSolidRender(level, pos);
    }

    /**
     * The level as the chunk meshes see it: the blocks in the camera's way are air. The scene's copy of a block beside one
     * then gets the light and corner shade the blocks around it get in the chunk, not the shadow of a wall that is gone.
     */
    private record AsMeshed(ClientLevel level) implements BlockAndTintGetter {
        private static AsMeshed last;

        static AsMeshed of(ClientLevel level) {
            AsMeshed view = last;
            if (view == null || view.level != level) {
                view = new AsMeshed(level);
                last = view;
            }
            return view;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return ClearBlocks.hides(pos) ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return ClearBlocks.hides(pos) ? Fluids.EMPTY.defaultFluidState() : level.getFluidState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return level.getBlockEntity(pos);
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return level.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return level.getBlockTint(pos, resolver);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
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
