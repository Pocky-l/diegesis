package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.pockyl.diegesis.api.client.SceneBuffers;

import java.util.List;

/**
 * A submit collector that draws at once into a scene's buffers: what the game's feature renderers would do with an item, a model,
 * a block model, text or custom geometry, the same quads into {@link SceneBuffers#getBuffer}. Outlines are the library's own
 * (see {@code SceneRenderer}); shadows, name tags, flames, leashes and particles have no place in a scene and are left out.
 */
public final class BufferSubmits implements SubmitNodeCollector {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final SceneBuffers buffers;
    private final MultiBufferSource source;

    public BufferSubmits(SceneBuffers buffers) {
        this.buffers = buffers;
        this.source = buffers::getBuffer;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 pos, int yOffset, Component text, boolean seethrough, int packedLight,
            double distanceToCameraSq, CameraRenderState cameraRenderState) {
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow,
            Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
        Font font = Minecraft.getInstance().font;
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        if (outlineColor == 0) {
            font.drawInBatch(string, x, y, color, dropShadow, pose, source, displayMode, backgroundColor, lightCoords);
        } else {
            font.drawInBatch8xOutline(string, x, y, color, outlineColor, pose, source, lightCoords);
        }
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords,
            int overlayCoords, int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        VertexConsumer buffer = buffers.getBuffer(renderType);
        if (sprite != null) {
            buffer = sprite.wrap(buffer);
        }
        model.setupAnim(state);
        model.renderToBuffer(poseStack, buffer, lightCoords, overlayCoords, tintedColor);
    }

    @Override
    public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
            @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil, int tintColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {
        VertexConsumer buffer = hasFoil ? ItemRenderer.getFoilBuffer(source, renderType, sheeted, true) : buffers.getBuffer(renderType);
        if (sprite != null) {
            buffer = sprite.wrap(buffer);
        }
        modelPart.render(poseStack, buffer, lightCoords, overlayCoords, tintColor);
    }

    @Override
    public void submitBlock(PoseStack poseStack, BlockState blockState, int lightCoords, int overlayCoords, int outlineColor) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(blockState, poseStack, source, lightCoords, overlayCoords);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, BlockStateModel model, float r, float g, float b,
            int lightCoords, int overlayCoords, int outlineColor) {
        ModelBlockRenderer.renderModel(poseStack.last(), buffers.getBuffer(renderType), model, r, g, b, lightCoords, overlayCoords);
    }

    /** A block model's quads, every side and the rest, as the game's block model feature draws them, tinted per tint layer. */
    public static void putBlockModel(PoseStack.Pose pose, VertexConsumer buffer, List<BlockModelPart> parts, int[] tintLayers,
            int tint, int lightCoords, int overlayCoords) {
        for (BlockModelPart part : parts) {
            for (Direction direction : DIRECTIONS) {
                for (BakedQuad quad : part.getQuads(direction)) {
                    putBlockQuad(pose, quad, tint, tintLayers, lightCoords, overlayCoords, buffer);
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                putBlockQuad(pose, quad, tint, tintLayers, lightCoords, overlayCoords, buffer);
            }
        }
    }

    private static void putBlockQuad(PoseStack.Pose pose, BakedQuad quad, int tint, int[] tintLayers, int lightCoords, int overlayCoords,
            VertexConsumer buffer) {
        int tintIndex = quad.tintIndex();
        int color = tintIndex != -1 && tintIndex < tintLayers.length ? ARGB.multiply(tint, tintLayers[tintIndex]) : tint;
        buffer.putBulkData(pose, quad, ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color), ARGB.alphaFloat(color),
                lightCoords, overlayCoords);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
            int[] tintLayers, List<BakedQuad> quads, RenderType renderType, ItemStackRenderState.FoilType foilType) {
        // As the game's item feature draws one, glint and all.
        ItemRenderer.renderItem(displayContext, poseStack, source, lightCoords, overlayCoords, tintLayers, quads, renderType, foilType);
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
        customGeometryRenderer.render(poseStack.last(), buffers.getBuffer(renderType));
    }

    @Override
    public void submitParticleGroup(ParticleGroupRenderer renderer) {
    }
}
