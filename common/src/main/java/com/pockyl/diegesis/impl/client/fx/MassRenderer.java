package com.pockyl.diegesis.impl.client.fx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.DiegesisMod;
import com.pockyl.diegesis.api.client.LiquidLook;
import com.pockyl.diegesis.api.scene.MassLook;
import com.pockyl.diegesis.api.scene.TankBinding;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The surface of a thick mass: one lit mesh over the tank whose heights are the mass's relief, grain, lumps and the rise where it meets
 * the walls, so that the light shows them and nothing is painted on. The mass's texture lies on it at the scene's texel density and goes
 * round with it; its sheen is a highlight laid over the slopes turned between the light and the eye, so a glossy mass shines on its ridges
 * and a matte one does not. While a liquid turns into it, its lumps form in the liquid and grow together: each cell of the mesh is the
 * mass or still the liquid, drawn as the liquid's surface is, on the same heights, so the two never lie one over the other.
 *
 * <p>The mass is opaque and drawn with the scene's solid parts, so what is held in it sinks into it; the liquid's cells and the sheen go
 * with the see-through sheet. Only vanilla render types and the scene's buffers, as the liquid.
 */
final class MassRenderer {
    /** Thinner than this a mass is not drawn: only its liquid. */
    static final float THINNEST = 0.002F;
    /**
     * Cells along a side of the mesh: at the station worked at as many as the mass's cells across the tank ({@link MassLook#cellPixels},
     * one texel by default, so that it bends no finer than the pixels drawn on it), at most {@code MOST_CELLS}; for one seen from closer
     * than each of {@code DETAIL_FROM} blocks no more than those; farther it is one flat cell. Every cell is four vertices a frame: a
     * street of finished bowls drew a third of its frame rate with full detail everywhere. Grain, the rise at the wall and lumps need
     * some cells to show, the sheen more.
     */
    private static final int MOST_CELLS = 28;
    private static final int FEWEST_CELLS = 2;
    private static final float[] DETAIL_FROM = {4.0F, 10.0F, 20.0F};
    private static final int[] DETAIL_CELLS = {16, 8, 4};
    private static final int RELIEF_CELLS = 8;
    private static final int SHEEN_CELLS = 8;
    private static final int MOST_POINTS = (MOST_CELLS + 1) * (MOST_CELLS + 1);
    /**
     * The relief is lit from the side the scene's light comes from, but lower (its sideways part this many times stronger), so that a
     * furrow reads under a light from high above; a flat mass keeps the texture's own colours, at {@code BASE} of them, so that slopes
     * turned to the light come out lighter.
     */
    private static final float RELIEF_SIDE = 2.5F;
    private static final float AMBIENT = 0.4F;
    private static final float DIFFUSE = 0.6F;
    private static final float DARKEST = 0.65F;
    private static final float BRIGHTEST = 1.2F;
    private static final float BASE = 0.94F;
    /** Below its level the mass darkens (a furrow's floor), by this much a pixel, at most; a ridge lightens a little less. */
    private static final float CAVITY = 0.32F;
    private static final float CAVITY_MOST = 0.22F;
    private static final float RIDGE_MOST = 0.06F;
    /** Where it meets the wall: darker over this far (pixels), and risen by this much over that far, climbing on over a gap to the wall. */
    private static final float CONTACT = 0.24F;
    private static final float CONTACT_WIDTH = 0.6F;
    private static final float MENISCUS = 0.28F;
    private static final float MENISCUS_WIDTH = 1.0F;
    private static final float MENISCUS_CLIMB = 0.6F;
    /** Grain: how high, pixels, and how fine (a cell's share), in two sizes. */
    private static final float GRAIN = 0.2F;
    private static final float GRAIN_SCALE = 1.3F;
    private static final float GRAIN_DETAIL = 3.1F;
    /** Lumps forming in the liquid as it thickens: how big (a pixel's share) and how far they stand up while the liquid is round them. */
    private static final float LUMPS = 0.55F;
    private static final float LUMP_RISE = 0.14F;
    private static final float LUMP_EDGE = 5.0F;
    /** The sheen: how strong at most, how sharp from a little gloss to much, and the warm white of a highlight. */
    private static final float SHEEN = 0.9F;
    private static final float SHARP_LEAST = 0.8F;
    private static final float SHARP_MOST = 2.2F;
    private static final float SHINE_FROM = 0.004F;
    private static final float GLOSS_LIFT = 0.004F;
    private static final float HIGHLIGHT_R = 1.0F;
    private static final float HIGHLIGHT_G = 0.98F;
    private static final float HIGHLIGHT_B = 0.93F;
    /** How much darker the deepest trail is, at a trail contrast of 1 ({@code setWakeLook}). */
    private static final float TRAIL = 0.35F;

    // Scratch space: rendering happens on one thread.
    private static final float[] PX = new float[MOST_POINTS];
    private static final float[] PY = new float[MOST_POINTS];
    private static final float[] PZ = new float[MOST_POINTS];
    private static final float[] NX = new float[MOST_POINTS];
    private static final float[] NY = new float[MOST_POINTS];
    private static final float[] NZ = new float[MOST_POINTS];
    private static final float[] SHADE = new float[MOST_POINTS];
    private static final float[] SHINE = new float[MOST_POINTS];
    private static final float[] TU = new float[MOST_POINTS];
    private static final float[] TV = new float[MOST_POINTS];
    private static final float[] TURNED_X = new float[MOST_POINTS];
    private static final float[] TURNED_Z = new float[MOST_POINTS];
    private static final float[] LUMP = new float[MOST_POINTS];
    private static final Vector3f POINT = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();
    /** The average colour of each texture's opaque pixels, RGB, for the liquid that turns into it. */
    private static final Map<TextureAtlasSprite, Integer> AVERAGES = new WeakHashMap<>();

    private MassRenderer() {
    }

    /** The liquid's colour {@code argb} gone {@code thick} of the way to the mass's own, and as opaque. */
    static int toward(int argb, MassLook look, float thick) {
        int mass = average(look);
        float t = Mth.clamp(thick, 0.0F, 1.0F);
        int a = Math.round(Mth.lerp(t, argb >>> 24, 255));
        int r = Math.round(Mth.lerp(t, argb >> 16 & 0xFF, mass >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, argb >> 8 & 0xFF, mass >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, argb & 0xFF, mass & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** What the mass looks like from afar: its texture's opaque pixels averaged, tinted, RGB. */
    static int average(MassLook look) {
        TextureAtlasSprite sprite = com.pockyl.diegesis.impl.client.render.SceneDraw.blockSprite(look.texture());
        int own = AVERAGES.computeIfAbsent(sprite, MassRenderer::measure);
        int tint = look.tint();
        return Math.round((own >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255.0F) << 16
                | Math.round((own >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255.0F) << 8
                | Math.round((own & 0xFF) * (tint & 0xFF) / 255.0F);
    }

    private static int measure(TextureAtlasSprite sprite) {
        // The atlas keeps no pixels to read on every loader: the texture's file is read once instead.
        Identifier name = sprite.contents().name();
        Identifier file = name.withPath("textures/" + name.getPath() + ".png");
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(file); NativeImage image = NativeImage.read(in)) {
            long red = 0;
            long green = 0;
            long blue = 0;
            int count = 0;
            // The first frame of an animated sprite is enough.
            for (int y = 0; y < Math.min(sprite.contents().height(), image.getHeight()); y++) {
                for (int x = 0; x < Math.min(sprite.contents().width(), image.getWidth()); x++) {
                    int argb = image.getPixel(x, y);
                    if ((argb >>> 24) == 0) {
                        continue;
                    }
                    red += argb >> 16 & 0xFF;
                    green += argb >> 8 & 0xFF;
                    blue += argb & 0xFF;
                    count++;
                }
            }
            if (count > 0) {
                return (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
            }
        } catch (IOException | RuntimeException e) {
            DiegesisMod.LOGGER.debug("No colour read from {} for a mass: {}", file, e.toString());
        }
        return 0xFFFFFF;
    }

    /**
     * Draws the surface of the mass of {@code pass}'s tank, {@code thick} of the way from its liquid.
     *
     * @param lightDir the direction to the scene's light in the node's space (unit)
     * @param distance how far the camera is, blocks: a far mass has fewer cells
     */
    static void draw(LiquidRenderer.Pass pass, TankBinding binding, LiquidSim sim, MassLook look, float thick, Vector3fc lightDir,
            com.pockyl.diegesis.api.client.SceneBuffers buffers, int light, float distance) {
        float hx = pass.hx;
        float hz = pass.hz;
        float cellPixels = look.cellPixels();
        int atWork = Mth.clamp(Mth.ceil(2.0F * Math.max(hx, hz) / cellPixels - 0.01F), FEWEST_CELLS, MOST_CELLS);
        int cells = distance <= 0.0F ? atWork : 1;
        for (int i = 0; i < DETAIL_FROM.length && distance > 0.0F; i++) {
            if (distance < DETAIL_FROM[i]) {
                cells = Math.min(atWork, DETAIL_CELLS[i]);
                break;
            }
        }
        boolean detailed = cells >= Math.min(RELIEF_CELLS, atWork);
        int side = cells + 1;
        float half = Math.min(hx, hz);
        boolean round = binding.round();
        float gap = binding.wallGap();
        Vector3fc up = pass.up;
        float level = pass.level;
        float cos = Mth.cos(sim.turned());
        float sin = Mth.sin(sim.turned());
        MassSim relief = sim.relief();
        float thin = 1.0F - thick;
        float waves = sim.isRippling() ? thin * thin : 0.0F;
        // Flat unless it is shaped: grain and lumps are relief too.
        float grain = detailed ? look.grain() * GRAIN * thick * look.relief() : 0.0F;
        float tile = look.tile();
        // Too few cells for lumps: the mass once it is mostly made, the liquid before.
        boolean whole = thick >= 0.999F || !detailed && thick >= 0.5F;
        // A cell is the mass where its lumps' noise is under this: none at the start, all of it at the end.
        float threshold = -0.12F + 1.24F * thick;
        float lumpRise = LUMP_RISE * thin * look.relief();

        for (int j = 0; j < side; j++) {
            float b = j * 2.0F / cells - 1.0F;
            for (int i = 0; i < side; i++) {
                float a = i * 2.0F / cells - 1.0F;
                boolean edge = i == 0 || j == 0 || i == cells || j == cells;
                float x;
                float z;
                float wall;
                if (round) {
                    // The square of cells laid onto the circle, its edge on the circle's edge.
                    float sa = a * (float) Math.sqrt(Math.max(0.0F, 1.0F - b * b / 2.0F));
                    float sb = b * (float) Math.sqrt(Math.max(0.0F, 1.0F - a * a / 2.0F));
                    float out = edge && gap > 0.0F ? gap : 0.0F;
                    x = sa * (hx + out);
                    z = sb * (hz + out);
                    wall = edge && gap > 0.0F ? -gap : (1.0F - (float) Math.sqrt(sa * sa + sb * sb)) * half;
                } else {
                    x = a * hx;
                    z = b * hz;
                    wall = Math.min(hx - Math.abs(x), hz - Math.abs(z));
                    if (edge && gap > 0.0F) {
                        x = i == 0 ? -hx - gap : i == cells ? hx + gap : x;
                        z = j == 0 ? -hz - gap : j == cells ? hz + gap : z;
                        wall = -gap;
                    }
                }
                int k = j * side + i;
                float ux = x / (2.0F * hx);
                float uz = z / (2.0F * hz);
                float tx = x * cos + z * sin;
                float tz = z * cos - x * sin;
                float y = (level - up.x() * x - up.z() * z) / up.y();
                if (waves > 0.0F) {
                    y -= sim.height(0.5F + ux, 0.5F - uz) * waves;
                }
                float lifted = 0.0F;
                if (relief != null) {
                    lifted += relief.height(Mth.clamp(ux, -0.5F, 0.5F), Mth.clamp(uz, -0.5F, 0.5F), cos, sin) * thick;
                }
                if (grain > 0.0F) {
                    float gx = tx / cellPixels;
                    float gz = tz / cellPixels;
                    float bumps = 0.65F * LiquidRenderer.Pass.noise(gx * GRAIN_SCALE + 5.0F, gz * GRAIN_SCALE + 11.0F)
                            + 0.35F * LiquidRenderer.Pass.noise(gx * GRAIN_DETAIL + 17.0F, gz * GRAIN_DETAIL + 2.0F);
                    lifted += grain * (bumps - 0.5F) * 2.0F;
                }
                float lump = whole ? 0.0F : LiquidRenderer.Pass.noise(tx * LUMPS + 31.0F, tz * LUMPS + 17.0F);
                if (!whole) {
                    lifted += lumpRise * Mth.clamp((threshold - lump) * LUMP_EDGE, 0.0F, 1.0F);
                }
                float rise = !detailed ? 0.0F : wall < 0.0F ? MENISCUS + -wall * MENISCUS_CLIMB
                        : MENISCUS * Mth.square(Math.max(0.0F, 1.0F - wall / MENISCUS_WIDTH));
                PX[k] = x;
                PY[k] = y + lifted + rise * thick;
                PZ[k] = z;
                // Lit by its own relief alone: the darker floor of a furrow, the lighter crest of a ridge, the darker edge at the wall.
                float cavity = 1.0F + Mth.clamp(lifted * CAVITY, -CAVITY_MOST, RIDGE_MOST);
                float contact = detailed ? 1.0F - CONTACT * thick * Mth.clamp(1.0F - wall / CONTACT_WIDTH, 0.0F, 1.0F) : 1.0F;
                SHADE[k] = cavity * contact;
                TU[k] = 0.5F + tx / tile;
                TV[k] = 0.5F + tz / tile;
                TURNED_X[k] = tx;
                TURNED_Z[k] = tz;
                LUMP[k] = lump;
            }
        }

        // The relief light: the scene's, lower; and the eye's half-way to it, for the sheen.
        Vector3f relit = new Vector3f(lightDir.x() * RELIEF_SIDE, lightDir.y(), lightDir.z() * RELIEF_SIDE).normalize();
        float flatLit = AMBIENT + DIFFUSE * Math.max(0.0F, up.x() * relit.x + up.y() * relit.y + up.z() * relit.z);
        float gloss = cells >= Math.min(SHEEN_CELLS, atWork) ? look.gloss() * thick : 0.0F;
        float sharp = Mth.lerp(look.gloss(), SHARP_LEAST, SHARP_MOST);
        Vector3f eye = pass.eye;
        Vector3f halfway = new Vector3f();
        for (int j = 0; j < side; j++) {
            int down = Math.max(0, j - 1);
            int upRow = Math.min(cells, j + 1);
            for (int i = 0; i < side; i++) {
                int left = Math.max(0, i - 1);
                int right = Math.min(cells, i + 1);
                int k = j * side + i;
                int l = j * side + left;
                int r = j * side + right;
                int d = down * side + i;
                int u = upRow * side + i;
                float ax = PX[r] - PX[l];
                float ay = PY[r] - PY[l];
                float az = PZ[r] - PZ[l];
                float bx = PX[u] - PX[d];
                float by = PY[u] - PY[d];
                float bz = PZ[u] - PZ[d];
                // Across the rows times along them: up out of the surface.
                float nx = by * az - bz * ay;
                float ny = bz * ax - bx * az;
                float nz = bx * ay - by * ax;
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1.0e-6F || ny <= 0.0F) {
                    nx = up.x();
                    ny = up.y();
                    nz = up.z();
                } else {
                    nx /= length;
                    ny /= length;
                    nz /= length;
                }
                NX[k] = nx;
                NY[k] = ny;
                NZ[k] = nz;
                float lit = (AMBIENT + DIFFUSE * Math.max(0.0F, nx * relit.x + ny * relit.y + nz * relit.z)) / flatLit;
                SHADE[k] *= Mth.clamp(Mth.lerp(thick, 1.0F, lit), DARKEST, BRIGHTEST);
                if (gloss > 0.001F) {
                    halfway.set(eye.x - PX[k], eye.y - PY[k], eye.z - PZ[k]).normalize().add(lightDir).normalize();
                    float flat = up.x() * halfway.x + up.y() * halfway.y + up.z() * halfway.z;
                    float facing = nx * halfway.x + ny * halfway.y + nz * halfway.z;
                    // Only what is turned to the light and the eye more than the level surface shines: no film of sheen over all of it.
                    float over = Math.max(0.0F, (facing - flat) / Math.max(1.0e-3F, 1.0F - flat));
                    SHINE[k] = Math.min(1.0F, gloss * SHEEN * (float) Math.pow(over, sharp));
                } else {
                    SHINE[k] = 0.0F;
                }
            }
        }

        TextureAtlasSprite sprite = com.pockyl.diegesis.impl.client.render.SceneDraw.blockSprite(look.texture());
        TextureAtlasSprite white = com.pockyl.diegesis.impl.client.render.SceneDraw.blockSprite(LiquidLook.LIQUID_SPRITE);
        float whiteU = (white.getU0() + white.getU1()) / 2.0F;
        float whiteV = (white.getV0() + white.getV1()) / 2.0F;
        VertexConsumer solid = buffers.getBuffer(com.pockyl.diegesis.impl.client.render.DiegesisRenderTypes.cutoutBlockSheet());
        float red = (look.tint() >> 16 & 0xFF) / 255.0F * BASE;
        float green = (look.tint() >> 8 & 0xFF) / 255.0F * BASE;
        float blue = (look.tint() & 0xFF) / 255.0F * BASE;
        float liquidBase = 1.0F - (1.0F - BASE) * thick;
        float strength = sim.mottle() * LiquidRenderer.MOTTLE_STRENGTH;
        float surfaceAlpha = pass.thickness(LiquidRenderer.SURFACE_PATH);
        PoseStack.Pose pose = pass.pose;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int k00 = j * side + i;
                int k01 = k00 + side;
                int k11 = k01 + 1;
                int k10 = k00 + 1;
                boolean mass = whole || (LUMP[k00] + LUMP[k01] + LUMP[k11] + LUMP[k10]) / 4.0F < threshold;
                if (mass) {
                    float baseU = Mth.floor((TU[k00] + TU[k11]) / 2.0F);
                    float baseV = Mth.floor((TV[k00] + TV[k11]) / 2.0F);
                    // The trail behind what moved through it: a darker path, a cell at a time, in steps.
                    float trail = 1.0F - Math.round(Math.min(1.0F, trailDepth(sim, k00, k11, hx, hz)) * LiquidRenderer.WAKE_STEPS)
                            / LiquidRenderer.WAKE_STEPS * TRAIL * sim.wakeContrast();
                    float cr = red * trail;
                    float cg = green * trail;
                    float cb = blue * trail;
                    solidVertex(solid, pose, pass, sprite, k00, baseU, baseV, cr, cg, cb, light);
                    solidVertex(solid, pose, pass, sprite, k01, baseU, baseV, cr, cg, cb, light);
                    solidVertex(solid, pose, pass, sprite, k11, baseU, baseV, cr, cg, cb, light);
                    solidVertex(solid, pose, pass, sprite, k10, baseU, baseV, cr, cg, cb, light);
                    if (Math.max(Math.max(SHINE[k00], SHINE[k01]), Math.max(SHINE[k11], SHINE[k10])) > SHINE_FROM) {
                        shine(pass, k00, whiteU, whiteV, light);
                        shine(pass, k01, whiteU, whiteV, light);
                        shine(pass, k11, whiteU, whiteV, light);
                        shine(pass, k10, whiteU, whiteV, light);
                    }
                    continue;
                }
                // Still the liquid here: its own colour, patches and trails, over what is seen through it, on the mass's heights.
                float mx = (PX[k00] + PX[k01] + PX[k11] + PX[k10]) / 4.0F;
                float my = (PY[k00] + PY[k01] + PY[k11] + PY[k10]) / 4.0F;
                float mz = (PZ[k00] + PZ[k01] + PZ[k11] + PZ[k10]) / 4.0F;
                float sx = (TURNED_X[k00] + TURNED_X[k11]) / 2.0F;
                float sz = (TURNED_Z[k00] + TURNED_Z[k11]) / 2.0F;
                float value = 0.65F * LiquidRenderer.Pass.noise(sx * LiquidRenderer.MOTTLE_SCALE, sz * LiquidRenderer.MOTTLE_SCALE)
                        + 0.35F * LiquidRenderer.Pass.noise(sx * LiquidRenderer.MOTTLE_DETAIL + 7.0F,
                                sz * LiquidRenderer.MOTTLE_DETAIL + 3.0F);
                float stepped = Math.round(value * LiquidRenderer.MOTTLE_STEPS) / LiquidRenderer.MOTTLE_STEPS - 0.5F;
                float middle = 1.0F - Math.max(Math.abs(mx) / hx, Math.abs(mz) / hz);
                float body = (1.0F + stepped * strength * 2.0F + Math.max(0.0F, middle) * sim.mottle() * 0.12F) * liquidBase;
                float r = pass.red * body;
                float g = pass.green * body;
                float bl = pass.blue * body;
                // The same darker path as on the mass, so that the trail does not change as the liquid thickens.
                float wake = 1.0F - Math.round(Math.min(1.0F, trailDepth(sim, k00, k11, hx, hz)) * LiquidRenderer.WAKE_STEPS)
                        / LiquidRenderer.WAKE_STEPS * TRAIL * sim.wakeContrast();
                r *= wake;
                g *= wake;
                bl *= wake;
                pass.behind(mx, my, mz, r, g, bl, surfaceAlpha);
                float mr = pass.mixed[0];
                float mg = pass.mixed[1];
                float mb = pass.mixed[2];
                float ma = pass.mixed[3];
                liquidVertex(pass, k00, mr, mg, mb, ma);
                liquidVertex(pass, k01, mr, mg, mb, ma);
                liquidVertex(pass, k11, mr, mg, mb, ma);
                liquidVertex(pass, k10, mr, mg, mb, ma);
            }
        }
    }

    /** How deep the trails are at the middle of the cell from corner {@code k00} to {@code k11}, 0 to about 1. */
    private static float trailDepth(LiquidSim sim, int k00, int k11, float hx, float hz) {
        float mx = (PX[k00] + PX[k11]) / 2.0F;
        float mz = (PZ[k00] + PZ[k11]) / 2.0F;
        return sim.trail(mx / (2.0F * hx) + 0.5F, mz / (2.0F * hz) + 0.5F);
    }

    /** A corner of a cell of the mass: its texture where the mass has turned to, wrapped into the sprite round the cell's middle. */
    private static void solidVertex(VertexConsumer consumer, PoseStack.Pose pose, LiquidRenderer.Pass pass, TextureAtlasSprite sprite,
            int k, float baseU, float baseV, float red, float green, float blue, int light) {
        float inset = LiquidRenderer.SPRITE_INSET;
        float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * Mth.clamp(TU[k] - baseU, inset, 1.0F - inset);
        float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * Mth.clamp(TV[k] - baseV, inset, 1.0F - inset);
        float shade = SHADE[k];
        pose.pose().transformPosition(PX[k] + pass.cx, PY[k] + pass.cy, PZ[k] + pass.cz, POINT);
        pose.normal().transform(NX[k], NY[k], NZ[k], NORMAL);
        consumer.addVertex(POINT.x, POINT.y, POINT.z, ARGB.colorFromFloat(1.0F, Math.min(1.0F, red * shade),
                Math.min(1.0F, green * shade), Math.min(1.0F, blue * shade)), u, v, OverlayTexture.NO_OVERLAY, light, NORMAL.x, NORMAL.y,
                NORMAL.z);
    }

    /** A corner of the sheen over a cell of the mass: white, as strong as the corner shines, a hair over it. */
    private static void shine(LiquidRenderer.Pass pass, int k, float u, float v, int light) {
        pass.pose.pose().transformPosition(PX[k] + NX[k] * GLOSS_LIFT + pass.cx, PY[k] + NY[k] * GLOSS_LIFT + pass.cy,
                PZ[k] + NZ[k] * GLOSS_LIFT + pass.cz, POINT);
        pass.pose.normal().transform(NX[k], NY[k], NZ[k], NORMAL);
        pass.consumer.addVertex(POINT.x, POINT.y, POINT.z, ARGB.colorFromFloat(SHINE[k], HIGHLIGHT_R, HIGHLIGHT_G, HIGHLIGHT_B), u,
                v, OverlayTexture.NO_OVERLAY, light, NORMAL.x, NORMAL.y, NORMAL.z);
    }

    /** A corner of a cell that is still the liquid: its colour, shaded by the relief grown in so far. */
    private static void liquidVertex(LiquidRenderer.Pass pass, int k, float r, float g, float b, float a) {
        float shade = SHADE[k];
        pass.emit(PX[k], PY[k], PZ[k], NX[k], NY[k], NZ[k], r * shade, g * shade, b * shade, a);
    }
}
