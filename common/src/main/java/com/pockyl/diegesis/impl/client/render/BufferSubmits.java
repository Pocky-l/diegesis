package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.pockyl.diegesis.api.client.SceneBuffers;

import java.util.List;

/**
 * A submit collector that draws at once into a scene's buffers: what the game's feature renderers would do with an item, a model,
 * a block model, text or custom geometry, the same quads into {@link SceneBuffers#getBuffer}. Outlines are the library's own
 * (see {@code SceneRenderer}); shadows, name tags, flames, leashes, particles and breaking overlays have no place in a scene and
 * are left out.
 */
public final class BufferSubmits implements SubmitNodeCollector {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final SceneBuffers buffers;
    private final QuadInstance quad = new QuadInstance();

    public BufferSubmits(SceneBuffers buffers) {
        this.buffers = buffers;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough,
            int lightCoords, double distanceToCameraSq, CameraRenderState camera) {
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow,
            Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
        // As the game's text feature draws it (a glowing sign's outline too), straight into the scene's real buffers.
        Font font = Minecraft.getInstance().font;
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        if (outlineColor == 0) {
            font.drawInBatch(string, x, y, color, dropShadow, pose, source(), displayMode, backgroundColor, lightCoords);
        } else {
            font.drawInBatch8xOutline(string, x, y, color, outlineColor, pose, source(), lightCoords);
        }
    }

    /** The scene's buffers as the buffer source the game's own drawing calls take on Minecraft 26.1.2. */
    private MultiBufferSource source() {
        return buffers::getBuffer;
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
            @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil, int tintedColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {
        // As the game's model part feature draws one, with its glint when it has one.
        VertexConsumer buffer = hasFoil ? ItemFeatureRenderer.getFoilBuffer(source(), renderType, sheeted, true)
                : buffers.getBuffer(renderType);
        if (sprite != null) {
            buffer = sprite.wrap(buffer);
        }
        modelPart.render(poseStack, buffer, lightCoords, overlayCoords, tintedColor);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers,
            int lightCoords, int overlayCoords, int outlineColor) {
        putBlockModel(poseStack.last(), buffers.getBuffer(renderType), parts, tintLayers, -1, lightCoords, overlayCoords);
    }

    /** A block model's quads, every side and the rest, as the game's block model feature draws them. */
    public static void putBlockModel(PoseStack.Pose pose, VertexConsumer buffer, List<BlockStateModelPart> parts, int[] tintLayers,
            int tint, int lightCoords, int overlayCoords) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(lightCoords);
        instance.setOverlayCoords(overlayCoords);
        for (BlockStateModelPart part : parts) {
            for (Direction direction : DIRECTIONS) {
                for (BakedQuad quad : part.getQuads(direction)) {
                    putBlockQuad(pose, quad, instance, tint, tintLayers, buffer);
                }
            }
            for (BakedQuad quad : part.getQuads(null)) {
                putBlockQuad(pose, quad, instance, tint, tintLayers, buffer);
            }
        }
    }

    private static void putBlockQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance, int tint, int[] tintLayers,
            VertexConsumer buffer) {
        int tintIndex = quad.materialInfo().tintIndex();
        boolean layered = tintIndex != -1 && tintIndex < tintLayers.length;
        instance.setColor(layered ? ARGB.multiply(tint, tintLayers[tintIndex]) : tint);
        buffer.putBakedQuad(pose, quad, instance);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
            int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
        // As the game's item feature draws one, glint and all (on Minecraft 26.1.2 the glint is a layer of its own over the item's
        // quads); only the special glint's own decal pose is left out.
        PoseStack.Pose pose = poseStack.last();
        quad.setLightCoords(lightCoords);
        quad.setOverlayCoords(overlayCoords);
        for (BakedQuad baked : quads) {
            BakedQuad.MaterialInfo material = baked.materialInfo();
            RenderType renderType = material.itemRenderType();
            int tintIndex = material.tintIndex();
            quad.setColor(material.isTinted() && tintIndex < tintLayers.length ? tintLayers[tintIndex] : -1);
            if (foilType != ItemStackRenderState.FoilType.NONE) {
                buffers.getBuffer(ItemFeatureRenderer.getFoilRenderType(renderType, true)).putBakedQuad(pose, baked, quad);
            }
            buffers.getBuffer(renderType).putBakedQuad(pose, baked, quad);
        }
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
        customGeometryRenderer.render(poseStack.last(), buffers.getBuffer(renderType));
    }

    @Override
    public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
    }
}
