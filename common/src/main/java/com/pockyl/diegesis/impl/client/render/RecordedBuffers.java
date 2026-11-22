package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.pockyl.diegesis.api.client.SceneBuffers;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A frame's scene, kept per render type until it is handed to the game's renderer. Since Minecraft 26.x the level is drawn from
 * what is submitted during the frame, after every submit is in: the scene writes its vertices here as it always did (in the level's
 * camera-relative space), and {@link #submit} gives each type's vertices to the game as one piece of custom geometry.
 */
final class RecordedBuffers implements SceneBuffers {
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return recorders.computeIfAbsent(type, key -> new Recorder());
    }

    /** Hands {@code type}'s vertices to {@code collector}, if any were written, and forgets them. */
    void submit(OrderedSubmitNodeCollector collector, RenderType type) {
        Recorder recorder = recorders.get(type);
        if (recorder != null && recorder.count > 0) {
            recorder.submit(collector, type);
        }
    }

    /** Hands every type's vertices to {@code collector}, in the order the types were first drawn, and forgets them. */
    void submitAll(OrderedSubmitNodeCollector collector) {
        recorders.forEach((type, recorder) -> {
            if (recorder.count > 0) {
                recorder.submit(collector, type);
            }
        });
    }

    /** Whether anything was drawn in {@code type} this frame. */
    boolean has(RenderType type) {
        Recorder recorder = recorders.get(type);
        return recorder != null && recorder.count > 0;
    }

    /** Drops what was not handed over (a frame that failed halfway). */
    void clear() {
        recorders.values().forEach(recorder -> recorder.count = 0);
    }

    /** The vertices of one render type: each attribute as it was set, replayed in the same order into the game's buffer. */
    private static final class Recorder implements VertexConsumer {
        private static final int COLOR = 1;
        private static final int UV = 1 << 1;
        private static final int UV1 = 1 << 2;
        private static final int UV2 = 1 << 3;
        private static final int NORMAL = 1 << 4;
        private static final int LINE_WIDTH = 1 << 5;
        /** Ints per vertex: flags, position (3), colour, uv (2), uv1, uv2, normal (3), line width. */
        private static final int STRIDE = 13;
        private int[] data = new int[STRIDE * 256];
        private int count;
        private int at = -STRIDE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if ((count + 1) * STRIDE > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            at = count++ * STRIDE;
            data[at] = 0;
            data[at + 1] = Float.floatToRawIntBits(x);
            data[at + 2] = Float.floatToRawIntBits(y);
            data[at + 3] = Float.floatToRawIntBits(z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF);
        }

        @Override
        public VertexConsumer setColor(int color) {
            data[at] |= COLOR;
            data[at + 4] = color;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            data[at] |= UV;
            data[at + 5] = Float.floatToRawIntBits(u);
            data[at + 6] = Float.floatToRawIntBits(v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            data[at] |= UV1;
            data[at + 7] = (v & 0xFFFF) << 16 | u & 0xFFFF;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            data[at] |= UV2;
            data[at + 8] = (v & 0xFFFF) << 16 | u & 0xFFFF;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            data[at] |= NORMAL;
            data[at + 9] = Float.floatToRawIntBits(x);
            data[at + 10] = Float.floatToRawIntBits(y);
            data[at + 11] = Float.floatToRawIntBits(z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            data[at] |= LINE_WIDTH;
            data[at + 12] = Float.floatToRawIntBits(width);
            return this;
        }

        void submit(OrderedSubmitNodeCollector collector, RenderType type) {
            // The vertices stand where the scene put them already: the pose the game hands back is not applied.
            int[] vertices = Arrays.copyOf(data, count * STRIDE);
            int vertexCount = count;
            count = 0;
            collector.submitCustomGeometry(new PoseStack(), type, (pose, buffer) -> replay(vertices, vertexCount, buffer));
        }

        private static void replay(int[] vertices, int vertexCount, VertexConsumer buffer) {
            for (int i = 0; i < vertexCount; i++) {
                int at = i * STRIDE;
                int flags = vertices[at];
                buffer.addVertex(Float.intBitsToFloat(vertices[at + 1]), Float.intBitsToFloat(vertices[at + 2]),
                        Float.intBitsToFloat(vertices[at + 3]));
                if ((flags & COLOR) != 0) {
                    buffer.setColor(vertices[at + 4]);
                }
                if ((flags & UV) != 0) {
                    buffer.setUv(Float.intBitsToFloat(vertices[at + 5]), Float.intBitsToFloat(vertices[at + 6]));
                }
                if ((flags & UV1) != 0) {
                    buffer.setUv1(vertices[at + 7] & 0xFFFF, vertices[at + 7] >>> 16);
                }
                if ((flags & UV2) != 0) {
                    buffer.setUv2(vertices[at + 8] & 0xFFFF, vertices[at + 8] >>> 16);
                }
                if ((flags & NORMAL) != 0) {
                    buffer.setNormal(Float.intBitsToFloat(vertices[at + 9]), Float.intBitsToFloat(vertices[at + 10]),
                            Float.intBitsToFloat(vertices[at + 11]));
                }
                if ((flags & LINE_WIDTH) != 0) {
                    buffer.setLineWidth(Float.intBitsToFloat(vertices[at + 12]));
                }
            }
        }
    }
}
