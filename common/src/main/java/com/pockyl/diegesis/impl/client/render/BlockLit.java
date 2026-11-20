package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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
    private static final float SQUARE = 0.9999F;
    /** How far (blocks) in front of a turned face its light is taken: clear of the face, well short of the next block. */
    private static final float IN_FRONT = 0.1F;
    /** The model turned by a quarter, a half and three quarters, made when first needed; dropped with the models. */
    private static final Map<BlockStateModel, BlockStateModel[]> TURNED = new WeakHashMap<>();
    /** The chunks' block tesselator, kept for the option it was made with (smooth lighting). */
    private static ModelBlockRenderer tesselator;
    private static boolean tesselatorSmooth;

    private BlockLit() {
    }

    /**
     * Draws {@code model} at the pose (one unit a block, the model's corner at the origin) into {@code consumer}, of a chunk render
     * type, lit as {@code state} at {@code pos} would be.
     *
     * @param toWorld the pose's turn into the world: the level's view turned back
     * @param camera  where the pose's origin is in the world (the camera), or null: a turned face is then lit from {@code pos}
     */
    static void draw(BlockStateModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, Matrix4f toWorld,
            Vec3 camera) {
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
            PoseStack.Pose pose = poseStack.last();
            tesselator(minecraft).tesselateBlock((offsetX, offsetY, offsetZ, quad, instance) -> {
                instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                PoseStack.Pose at = pose.copy();
                at.pose().translate(offsetX, offsetY, offsetZ);
                consumer.putBakedQuad(at, quad, instance);
            }, 0.0F, 0.0F, 0.0F, level, pos, state, turned(model, quarters), 42L);
            poseStack.popPose();
            return;
        }
        QuadInstance instance = new QuadInstance();
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (BlockStateModelPart part : SceneDraw.parts(model)) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    Vector3f normal = world.transformDirection(new Vector3f(quad.direction().getUnitVec3f())).normalize();
                    // A face that is not shaded by the way it faces (shade: false) is shaded as one facing up.
                    Direction shadeOverride = quad.materialInfo().shadeDirectionOverride();
                    float shade = diffuse(shadeOverride == null ? normal
                            : world.transformDirection(new Vector3f(shadeOverride.getUnitVec3f())).normalize());
                    instance.setLightCoords(faceLight(level, quad, world, camera, normal, pos));
                    int grey = Math.round(shade * 255.0F);
                    instance.setColor(0xFF000000 | grey << 16 | grey << 8 | grey);
                    consumer.putBakedQuad(poseStack.last(), quad, instance);
                }
            }
        }
    }

    /** The chunks' tesselator: smooth lighting's corner shadows as the options have them, every face drawn. */
    private static ModelBlockRenderer tesselator(Minecraft minecraft) {
        boolean smooth = minecraft.options.ambientOcclusion().get();
        if (tesselator == null || tesselatorSmooth != smooth) {
            tesselator = new ModelBlockRenderer(smooth, false, minecraft.getBlockColors());
            tesselatorSmooth = smooth;
        }
        return tesselator;
    }

    /**
     * The light of a turned face: of the block just in front of its middle, where it stands now. Where that one is solid (no
     * light inside), or the face's place is not known, the block {@code pos} looks into that way, or {@code pos} itself.
     */
    private static int faceLight(ClientLevel level, BakedQuad quad, Matrix4f world, Vec3 camera, Vector3f normal, BlockPos pos) {
        if (camera != null) {
            Vector3f middle = new Vector3f();
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                middle.add(quad.position(vertex));
            }
            world.transformPosition(middle.div(BakedQuad.VERTEX_COUNT));
            BlockPos front = BlockPos.containing(camera.x + middle.x + normal.x * IN_FRONT, camera.y + middle.y + normal.y * IN_FRONT,
                    camera.z + middle.z + normal.z * IN_FRONT);
            if (!level.getBlockState(front).isSolidRender()) {
                return LightCoordsUtil.getLightCoords(level, front);
            }
        }
        BlockPos lit = pos.relative(Direction.getApproximateNearest(normal.x, normal.y, normal.z));
        return LightCoordsUtil.getLightCoords(level, level.getBlockState(lit).isSolidRender() ? pos : lit);
    }

    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            null};

    /** The shade the world gives a face by the way it faces: 1 up, 0.5 down, 0.8 north and south, 0.6 east and west, between. */
    private static float diffuse(Vector3f normal) {
        float up = normal.y * normal.y * (3.0F + normal.y) / 4.0F;
        return Math.min(1.0F, normal.x * normal.x * 0.6F + up + normal.z * normal.z * 0.8F);
    }

    private static BlockStateModel turned(BlockStateModel model, int quarters) {
        if (quarters == 0) {
            return model;
        }
        BlockStateModel[] turns = TURNED.computeIfAbsent(model, key -> new BlockStateModel[4]);
        if (turns[quarters] == null) {
            turns[quarters] = new Turned(model, quarters);
        }
        return turns[quarters];
    }

    /** A model turned by quarters about the up through the middle of its block, its quads' sides with it. */
    private record Turned(BlockStateModel model, int quarters) implements BlockStateModel {
        @Override
        public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(random, parts);
            for (BlockStateModelPart part : parts) {
                output.add(new TurnedPart(part, quarters));
            }
        }

        @Override
        public Material.Baked particleMaterial() {
            return model.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return model.materialFlags();
        }
    }

    /** A model part turned by quarters, each side's quads turned once and kept. */
    private static final class TurnedPart implements BlockStateModelPart {
        private final BlockStateModelPart part;
        private final int quarters;
        private final Map<Direction, List<BakedQuad>> sides = new EnumMap<>(Direction.class);
        private List<BakedQuad> unsided;

        private TurnedPart(BlockStateModelPart part, int quarters) {
            this.part = part;
            this.quarters = quarters;
        }

        @Override
        public List<BakedQuad> getQuads(Direction side) {
            if (side == null) {
                if (unsided == null) {
                    unsided = turn(part.getQuads(null));
                }
                return unsided;
            }
            return sides.computeIfAbsent(side, key -> turn(part.getQuads(turnBack(key))));
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
                Vector3fc[] corners = new Vector3fc[BakedQuad.VERTEX_COUNT];
                for (int vertex = 0; vertex < corners.length; vertex++) {
                    Vector3fc position = quad.position(vertex);
                    float x = position.x() - 0.5F;
                    float z = position.z() - 0.5F;
                    corners[vertex] = new Vector3f(Math.round((cos * x + sin * z) * 1.0e5F) / 1.0e5F + 0.5F, position.y(),
                            Math.round((-sin * x + cos * z) * 1.0e5F) / 1.0e5F + 0.5F);
                }
                Vector3fc facing = quad.direction().getUnitVec3f();
                Direction side = Direction.getApproximateNearest(cos * facing.x() + sin * facing.z(), facing.y(),
                        -sin * facing.x() + cos * facing.z());
                turned.add(new BakedQuad(corners[0], corners[1], corners[2], corners[3], quad.packedUV0(), quad.packedUV1(),
                        quad.packedUV2(), quad.packedUV3(), side, quad.materialInfo()));
            }
            return turned;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return part.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return part.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return part.materialFlags();
        }
    }
}
