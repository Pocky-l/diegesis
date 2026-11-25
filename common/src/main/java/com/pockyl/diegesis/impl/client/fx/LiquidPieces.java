package com.pockyl.diegesis.impl.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.Floater;
import com.pockyl.diegesis.api.scene.Box;
import com.pockyl.diegesis.impl.client.anim.Easing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What floats on a tank's liquid, riding the waves and carried round by its current. Solid things are slices of voxels, one for every
 * pixel of their sprite, of even thickness as an item's model is, and a strong current turns them over now and then; a film on the
 * surface is the flat sprite. They come up from below one by one, overshoot the surface a little and ring it;
 * they leave by shrinking.
 */
public final class LiquidPieces {
    private static final int MAX = 48;
    private static final float APPEAR_HALF_LIFE = 0.16F;
    private static final float GONE = 0.02F;
    /** How far from the walls a piece stays, as a part of the way to them. */
    private static final float REACH = 0.9F;
    /** Over the surface, far enough that the liquid's layer does not cover it, in pixels. */
    private static final float LIFT = 0.16F;
    /** Two unrelated steps round and outwards: pieces added one by one spread evenly, with no clumps, holes or lines. */
    private static final float GOLDEN_ANGLE = 2.3999631F;
    private static final float OUTWARD_STEP = 0.7548777F;
    /** Coming up: from how deep (pixels), how long it takes (seconds), how strong the ring is where it breaks the surface. */
    private static final float RISE_DEPTH = 2.2F;
    private static final float RISE_TIME = 0.55F;
    private static final float RISE_RING = 0.14F;
    /** How fast a piece pushed aside drifts back to its way, afloat and held in a mass (half-lives, seconds); how much slower leaning on another. */
    private static final float PUSH_HALF_LIFE = 0.6F;
    private static final float HELD_PUSH_HALF_LIFE = 1.5F;
    private static final float LEANING_SLOWER = 2.0F;
    /** How far round a piece's middle others keep off, as a part of its size, and how many times a frame the crowd sorts itself out. */
    private static final float CLEARANCE = 0.36F;
    private static final int SEPARATE_PASSES = 3;
    /**
     * Held in a mass, a piece gives way to what stands in it over this long (seconds), not at once: the whisk wades through a salad
     * and moves its leaves aside instead of flinging them off.
     */
    private static final float HELD_YIELD = 0.15F;
    /** A moving piece's trail: how clear unless told otherwise, how far apart its points lie (pixels), from what speed it shows and how much more it takes to be full (pixels per second). */
    private static final float WAKE = 0.7F;
    private static final float WAKE_STEP = 0.4F;
    private static final float WAKE_FROM = 0.4F;
    private static final float WAKE_RANGE = 1.6F;
    /** How far past its place a rising piece shoots: the constant of an ease with overshoot. */
    private static final float OVERSHOOT = 2.6F;
    /** How thick a slice is, as a part of a voxel. */
    private static final float THICKNESS = 1.3F;
    /** From what current (radians per second) pieces turn over, how often (per second, at a current of 1), how long it takes, how high they come. */
    private static final float TUMBLE_FROM = 0.35F;
    private static final float TUMBLE_RATE = 0.3F;
    private static final float TUMBLE_TIME = 0.6F;
    private static final float TUMBLE_HOP = 0.45F;
    /**
     * In a mass: from how thick it is nothing turns over any more; how far out (in its size) a piece feels the relief under it; how much
     * of its thickness is sunk below the highest point under it.
     */
    private static final float HELD_TUMBLES = 0.3F;
    private static final float HELD_SPAN = 0.3F;
    private static final float HELD_SUNK = 0.05F;
    /** Mostly in a mass from this thickness: its pieces' sides hide in it from this far (blocks), and they are not drawn from that far. */
    private static final float HELD_HALF = 0.5F;
    private static final float HELD_FLAT_FROM = 6.0F;
    private static final float HELD_DRAWN_UNTIL = 20.0F;
    /**
     * Farther than this (blocks) a solid piece is a flat slice, its top and its underside: its voxels are smaller than a pixel
     * there, and a street of cooking soups wrote a thousand corners for every piece every frame.
     */
    private static final float FLAT_FROM = 16.0F;
    /** The side that is under, in shade. */
    private static final float UNDERSIDE = 0.8F;
    /** Sides lie in their own shade, as the faces of a block do. */
    private static final float SIDE_X = 0.62F;
    private static final float SIDE_Z = 0.78F;
    /** Which pixels every sprite has: a sprite stays the same as long as the game runs on these resources. */
    private static final Map<ResourceLocation, int[][]> SHAPES = new HashMap<>();

    private final List<Piece> pieces = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private List<Floater> sources = List.of();
    private int wanted;
    private float current;
    /** What stands in the liquid: x, z and radius of each, in the node's pixels. */
    private float[] obstacles = new float[0];
    private float wake = WAKE;
    private float time;
    private int made;
    /** How far the liquid has turned into a mass that holds what is in it, 0 (afloat) to 1 (held). */
    private float held;
    /** How far round its middle others keep off, as a part of its size; the last step's length, seconds. */
    private float clearance = CLEARANCE;
    private float lastStep = 1.0F / 60.0F;

    /** What floats from now on; pieces of other things leave, new ones come until there are {@code count}. */
    public void set(List<Floater> floaters, int count) {
        wanted = floaters.isEmpty() ? 0 : Math.min(count, MAX);
        if (!floaters.equals(sources)) {
            sources = List.copyOf(floaters);
            pieces.forEach(piece -> piece.leaving = true);
        }
    }

    /** What stands in the liquid: x, z and radius of each, in the node's pixels. */
    public void setObstacles(float... xzRadius) {
        obstacles = xzRadius;
    }

    /** How fast the liquid turns, radians per second, the sign saying which way round. */
    public void setCurrent(float radiansPerSecond) {
        current = radiansPerSecond;
    }

    /** How clear the trails of moving pieces are, 0 to 1; 0 leaves none. */
    public void setWake(float strength) {
        wake = strength;
    }

    /**
     * How far the liquid has turned into a mass, 0 to 1: a mass holds what is in it partly sunk, where it is pushed to, going round with
     * it; nothing bobs, drifts back or turns over in it.
     */
    /** How far round its middle others keep off, as a part of its size: about half keeps sprites from lying over each other. */
    public void setClearance(float share) {
        clearance = Math.max(0.1F, Math.min(1.0F, share));
    }

    public void setHeld(float amount) {
        held = Math.max(0.0F, Math.min(1.0F, amount));
    }

    public void update(float dt) {
        time += dt;
        lastStep = dt;
        int staying = 0;
        for (Piece piece : pieces) {
            if (!piece.leaving) {
                staying++;
                piece.leaving = staying > wanted;
            }
        }
        // One at a time, so that a soup fills up piece by piece.
        if (staying < wanted && !sources.isEmpty() && (pieces.isEmpty() || pieces.get(pieces.size() - 1).grown > 0.3F)) {
            pieces.add(new Piece(sources.get(made % sources.size()), made, random));
            made++;
        }
        Iterator<Piece> iterator = pieces.iterator();
        while (iterator.hasNext()) {
            Piece piece = iterator.next();
            piece.grown = Easing.approach(piece.grown, piece.leaving ? 0.0F : 1.0F, APPEAR_HALF_LIFE, dt);
            piece.rise = Math.min(1.0F, piece.rise + dt / RISE_TIME);
            piece.wakeClock += dt;
            if (piece.tumble > 0.0F) {
                piece.tumble += dt / TUMBLE_TIME;
                if (piece.tumble >= 1.0F) {
                    piece.tumble = 0.0F;
                    piece.over = !piece.over;
                }
            } else if (piece.floater.solid() && held < HELD_TUMBLES && Math.abs(current) > TUMBLE_FROM
                    && random.nextFloat() < dt * TUMBLE_RATE * Math.abs(current)) {
                piece.tumble = 0.001F;
            }
            // Pushed aside, it drifts back to its way: slower held in a mass, slower still leaning on something, so that whatever the
            // whisk moves is not left heaped against the walls.
            float halfLife = Mth.lerp(held, PUSH_HALF_LIFE, HELD_PUSH_HALF_LIFE) * (piece.touching ? LEANING_SLOWER : 1.0F);
            float settle = (float) Math.pow(0.5F, dt / halfLife);
            piece.pushX *= settle;
            piece.pushZ *= settle;
            piece.touching = false;
            if (piece.leaving && piece.grown < GONE) {
                iterator.remove();
                continue;
            }
            // The middle of a turning liquid is slower than its edge; left alone every piece keeps wandering a little. A mass turns as one.
            piece.angle += dt * (current * Mth.lerp(held, 0.45F + 0.75F * piece.reach, 1.0F) + 0.02F * piece.wander * (1.0F - held));
        }
    }

    /** Draws the pieces in full detail; the pose stack is in the node's pixels. */
    public void render(Box inner, LiquidSim sim, PoseStack poseStack, MultiBufferSource buffers, int light) {
        render(inner, sim, poseStack, buffers, light, 0.0F);
    }

    /**
     * Draws the pieces; the pose stack is in the node's pixels.
     *
     * @param distance how far the camera is from the vessel, blocks: far pieces are flat slices
     */
    public void render(Box inner, LiquidSim sim, PoseStack poseStack, MultiBufferSource buffers, int light, float distance) {
        boolean inMass = held > HELD_HALF;
        if (pieces.isEmpty() || inMass && distance >= HELD_DRAWN_UNTIL) {
            // Sunk in a mass, a piece shows as a speck from afar.
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        float halfX = (inner.to().x() - inner.from().x()) / 2.0F;
        float halfZ = (inner.to().z() - inner.from().z()) / 2.0F;
        float centerX = inner.from().x() + halfX;
        float centerZ = inner.from().z() + halfZ;
        float surface = inner.from().y() + (inner.to().y() - inner.from().y()) * sim.fraction();
        for (Piece piece : pieces) {
            // A circle pushed out into the corners: the tank is filled the way it is shaped.
            float cos = Mth.cos(piece.angle);
            float sin = Mth.sin(piece.angle);
            float stretch = piece.reach * REACH / Math.max(Math.abs(cos), Math.abs(sin));
            piece.u = cos * stretch;
            piece.v = sin * stretch;
            float size = piece.floater.size() * piece.grown;
            piece.radius = size * clearance;
            piece.wayX = centerX + piece.u * (halfX - size * 0.3F);
            piece.wayZ = centerZ + piece.v * (halfZ - size * 0.3F);
            piece.x = piece.wayX + piece.pushX;
            piece.z = piece.wayZ + piece.pushZ;
        }
        // How much of its way out of what stands in it a piece goes in one pass: all of it afloat, a share a step held in a mass.
        float frameGive = 1.0F - (float) Math.exp(-lastStep / HELD_YIELD);
        float give = Mth.lerp(held, 1.0F, 1.0F - (float) Math.pow(1.0F - frameGive, 1.0F / SEPARATE_PASSES));
        for (int pass = 0; pass < SEPARATE_PASSES; pass++) {
            separate(centerX, centerZ, halfX, halfZ, give);
        }
        Quaternionf turn = new Quaternionf();
        boolean flat = distance >= (inMass ? HELD_FLAT_FROM : FLAT_FROM);
        for (Piece piece : pieces) {
            piece.pushX = piece.x - piece.wayX;
            piece.pushZ = piece.z - piece.wayZ;
            leaveWake(piece, sim, centerX, centerZ, halfX, halfZ);
            float rock = Mth.sin(time * 0.9F + piece.wander * 3.0F) * 0.06F * (1.0F - held);
            float size = piece.floater.size() * piece.grown;
            float risen = overshot(piece.rise);
            float waveU = 0.5F + piece.u / 2.0F;
            float waveV = 0.5F - piece.v / 2.0F;
            if (!piece.rang && risen >= 0.9F) {
                piece.rang = true;
                sim.impulse(waveU, waveV, RISE_RING);
            }
            float below = RISE_DEPTH * (1.0F - risen);
            float eased = piece.tumble * piece.tumble * (3.0F - 2.0F * piece.tumble);
            float hop = Mth.sin(piece.tumble * Mth.PI) * TUMBLE_HOP;
            float y = surface + LIFT + piece.floater.size() * 0.1F + hop - below - sim.height(waveU, waveV);
            poseStack.pushPose();
            if (held > 0.0F) {
                // Held in a mass: partly sunk where it stands highest under the piece, lying along its slope.
                float x = (piece.x - centerX) / (2.0F * halfX);
                float z = (piece.z - centerZ) / (2.0F * halfZ);
                float stepX = size * HELD_SPAN / (2.0F * halfX);
                float stepZ = size * HELD_SPAN / (2.0F * halfZ);
                float east = sim.relief(x + stepX, z);
                float west = sim.relief(x - stepX, z);
                float south = sim.relief(x, z + stepZ);
                float north = sim.relief(x, z - stepZ);
                float top = Math.max(sim.relief(x, z), Math.max(Math.max(east, west), Math.max(south, north)));
                float thick = size / 16.0F * THICKNESS;
                float thin = 1.0F - held;
                float sunk = surface + top - thick * HELD_SUNK - below - sim.height(waveU, waveV) * thin * thin;
                y = Mth.lerp(held, y, sunk);
                poseStack.translate(piece.x, y, piece.z);
                float slopeX = (east - west) / (2.0F * size * HELD_SPAN);
                float slopeZ = (south - north) / (2.0F * size * HELD_SPAN);
                poseStack.mulPose(turn.rotationZ((float) Math.atan(slopeX) * held).rotateX(-(float) Math.atan(slopeZ) * held));
            } else {
                poseStack.translate(piece.x, y, piece.z);
            }
            turn.rotationY(piece.yaw).rotateX(rock + (piece.over ? Mth.PI : 0.0F) + eased * Mth.PI);
            poseStack.mulPose(turn);
            piece.draw(consumer, poseStack.last(), atlas.apply(piece.floater.sprite()), size, light, flat);
            poseStack.popPose();
        }
    }

    /** A piece going through the liquid draws a trail, the faster the clearer; one lying on calm water leaves none. */
    private void leaveWake(Piece piece, LiquidSim sim, float centerX, float centerZ, float halfX, float halfZ) {
        float wentX = piece.x - piece.wakeX;
        float wentZ = piece.z - piece.wakeZ;
        float travelled = Mth.sqrt(wentX * wentX + wentZ * wentZ);
        if (travelled < WAKE_STEP) {
            return;
        }
        float speed = travelled / Math.max(piece.wakeClock, 0.001F);
        piece.wakeX = piece.x;
        piece.wakeZ = piece.z;
        piece.wakeClock = 0.0F;
        float strength = wake * Math.min(1.0F, (speed - WAKE_FROM) / WAKE_RANGE);
        if (strength > 0.0F && piece.rise >= 1.0F && !piece.leaving) {
            sim.leaveWake(0.5F + (piece.x - centerX) / (2.0F * halfX), 0.5F + (piece.z - centerZ) / (2.0F * halfZ), piece.radius / (2.0F * halfX), strength);
        }
    }

    /** What floats pushes what floats out of its way, gives way to what stands in the liquid, and stays between the walls. */
    private void separate(float centerX, float centerZ, float halfX, float halfZ, float give) {
        for (int i = 0; i < pieces.size(); i++) {
            Piece one = pieces.get(i);
            for (int j = i + 1; j < pieces.size(); j++) {
                Piece other = pieces.get(j);
                float dx = other.x - one.x;
                float dz = other.z - one.z;
                float clear = one.radius + other.radius;
                float squared = dx * dx + dz * dz;
                // Most pairs lie apart: the root is taken only for those that touch.
                if (squared < clear * clear) {
                    float apart = Mth.sqrt(squared);
                    // Pieces exactly on each other part along a way of their own.
                    float nx = apart > 0.001F ? dx / apart : Mth.cos(i + j);
                    float nz = apart > 0.001F ? dz / apart : Mth.sin(i + j);
                    float move = (clear - apart) / 2.0F;
                    one.x -= nx * move;
                    one.z -= nz * move;
                    other.x += nx * move;
                    other.z += nz * move;
                    one.touching = true;
                    other.touching = true;
                }
            }
            for (int o = 0; o + 2 < obstacles.length; o += 3) {
                float dx = one.x - obstacles[o];
                float dz = one.z - obstacles[o + 1];
                float clear = obstacles[o + 2] + one.radius;
                float squared = dx * dx + dz * dz;
                if (squared < clear * clear) {
                    float apart = Mth.sqrt(squared);
                    float out = clear / Math.max(apart, 0.01F);
                    one.x += (obstacles[o] + dx * out - one.x) * give;
                    one.z += (obstacles[o + 1] + dz * out - one.z) * give;
                    one.touching = true;
                }
            }
        }
        for (Piece piece : pieces) {
            // Its whole sprite stays inside the walls, not under them.
            float half = piece.floater.size() * piece.grown * 0.5F;
            float roomX = Math.max(0.0F, halfX - half);
            float roomZ = Math.max(0.0F, halfZ - half);
            piece.x = Mth.clamp(piece.x, centerX - roomX, centerX + roomX);
            piece.z = Mth.clamp(piece.z, centerZ - roomZ, centerZ + roomZ);
        }
    }

    /** Where the sprite has a pixel: 1, see-through: 0. */
    private static int[][] shape(ResourceLocation id, TextureAtlasSprite sprite) {
        return SHAPES.computeIfAbsent(id, key -> {
            int[][] steps = new int[sprite.contents().width()][sprite.contents().height()];
            for (int x = 0; x < steps.length; x++) {
                for (int y = 0; y < steps[x].length; y++) {
                    steps[x][y] = sprite.contents().isTransparent(0, x, y) ? 0 : 1;
                }
            }
            return steps;
        });
    }

    private static int at(int[][] steps, int x, int y) {
        return x < 0 || y < 0 || x >= steps.length || y >= steps[x].length ? 0 : steps[x][y];
    }

    /** 0 to 1 over 0 to 1, going past 1 before it settles. */
    private static float overshot(float t) {
        float back = t - 1.0F;
        return 1.0F + (OVERSHOOT + 1.0F) * back * back * back + OVERSHOOT * back * back;
    }

    /** One thing floating. */
    private static final class Piece {
        private final Floater floater;
        private final float reach;
        private final float wander;
        private final float shade;
        private final boolean mirrored;
        private float angle;
        private float yaw;
        private float grown;
        private float rise;
        private float tumble;
        private boolean over;
        private float u;
        private float v;
        private float wayX;
        private float wayZ;
        private float x;
        private float z;
        private float radius;
        private boolean touching;
        private float pushX;
        private float pushZ;
        private float wakeX;
        private float wakeZ;
        private float wakeClock;
        private boolean rang;
        private boolean leaving;

        Piece(Floater floater, int index, RandomSource random) {
            this.floater = floater;
            reach = (float) Math.sqrt(0.06F + 0.94F * (index * OUTWARD_STEP % 1.0F));
            wander = random.nextFloat() * 2.0F - 1.0F;
            angle = index * GOLDEN_ANGLE;
            // A film keeps the light drawn on it on one side; something solid lies any way round.
            yaw = floater.solid() ? random.nextFloat() * Mth.TWO_PI : (random.nextFloat() - 0.5F) * 0.9F;
            shade = Mth.lerp(random.nextFloat(), 0.9F, 1.0F);
            mirrored = random.nextBoolean();
        }

        void draw(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, float size, int light, boolean flat) {
            float red = (floater.color() >> 16 & 0xFF) / 255.0F * shade;
            float green = (floater.color() >> 8 & 0xFF) / 255.0F * shade;
            float blue = (floater.color() & 0xFF) / 255.0F * shade;
            if (floater.solid() && flat) {
                slice(consumer, pose, sprite, size, red, green, blue, light);
            } else if (floater.solid()) {
                voxels(consumer, pose, sprite, size, red, green, blue, light);
            } else {
                quad(consumer, pose, sprite, size, 0.0F, 0.0F, 0.0F, red, green, blue, light);
            }
        }

        /**
         * A box for every pixel, in the pixel's own colour; only the faces that can be seen. The tops of all boxes lie in one plane,
         * and so do their undersides: each is one quad with the whole sprite, its see-through pixels cut out, which looks the same
         * as a face for every pixel at a part of the corners. Only the sides are drawn box by box, where the outline is.
         */
        private void voxels(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, float size, float red, float green, float blue, int light) {
            int[][] steps = shape(floater.sprite(), sprite);
            int width = steps.length;
            float cell = size / width;
            float rise = cell * THICKNESS;
            float half = rise / 2.0F;
            slice(consumer, pose, sprite, size, red, green, blue, light);
            Vector3f east = pose.normal().transform(new Vector3f(1.0F, 0.0F, 0.0F));
            Vector3f west = new Vector3f(east).negate();
            Vector3f south = pose.normal().transform(new Vector3f(0.0F, 0.0F, 1.0F));
            Vector3f north = new Vector3f(south).negate();
            float sideXr = red * SIDE_X;
            float sideXg = green * SIDE_X;
            float sideXb = blue * SIDE_X;
            float sideZr = red * SIDE_Z;
            float sideZg = green * SIDE_Z;
            float sideZb = blue * SIDE_Z;
            for (int px = 0; px < width; px++) {
                for (int py = 0; py < steps[px].length; py++) {
                    int step = steps[px][py];
                    if (step == 0) {
                        continue;
                    }
                    int texel = mirrored ? width - 1 - px : px;
                    float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (texel + 0.5F) / width;
                    float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (py + 0.5F) / steps[px].length;
                    float x0 = (px - width / 2.0F) * cell;
                    float x1 = x0 + cell;
                    float z0 = (py - steps[px].length / 2.0F) * cell;
                    float z1 = z0 + cell;
                    float top = half;
                    float low = -half;
                    if (at(steps, px + 1, py) == 0) {
                        corner(consumer, pose, x1, low, z1, u, v, sideXr, sideXg, sideXb, light, east);
                        corner(consumer, pose, x1, low, z0, u, v, sideXr, sideXg, sideXb, light, east);
                        corner(consumer, pose, x1, top, z0, u, v, sideXr, sideXg, sideXb, light, east);
                        corner(consumer, pose, x1, top, z1, u, v, sideXr, sideXg, sideXb, light, east);
                    }
                    if (at(steps, px - 1, py) == 0) {
                        corner(consumer, pose, x0, low, z0, u, v, sideXr, sideXg, sideXb, light, west);
                        corner(consumer, pose, x0, low, z1, u, v, sideXr, sideXg, sideXb, light, west);
                        corner(consumer, pose, x0, top, z1, u, v, sideXr, sideXg, sideXb, light, west);
                        corner(consumer, pose, x0, top, z0, u, v, sideXr, sideXg, sideXb, light, west);
                    }
                    if (at(steps, px, py + 1) == 0) {
                        corner(consumer, pose, x0, low, z1, u, v, sideZr, sideZg, sideZb, light, south);
                        corner(consumer, pose, x1, low, z1, u, v, sideZr, sideZg, sideZb, light, south);
                        corner(consumer, pose, x1, top, z1, u, v, sideZr, sideZg, sideZb, light, south);
                        corner(consumer, pose, x0, top, z1, u, v, sideZr, sideZg, sideZb, light, south);
                    }
                    if (at(steps, px, py - 1) == 0) {
                        corner(consumer, pose, x1, low, z0, u, v, sideZr, sideZg, sideZb, light, north);
                        corner(consumer, pose, x0, low, z0, u, v, sideZr, sideZg, sideZb, light, north);
                        corner(consumer, pose, x0, top, z0, u, v, sideZr, sideZg, sideZb, light, north);
                        corner(consumer, pose, x1, top, z0, u, v, sideZr, sideZg, sideZb, light, north);
                    }
                }
            }
        }

        /**
         * The voxels' top and, in shade, their underside, as thick apart as the voxels are: the sprite's see-through pixels cut the
         * outline. Alone it is a piece seen from afar, where the sides are thinner than a pixel.
         */
        private void slice(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, float size, float red, float green, float blue,
                int light) {
            int[][] steps = shape(floater.sprite(), sprite);
            float cell = size / steps.length;
            float half = cell * THICKNESS / 2.0F;
            float hx = size / 2.0F;
            float hz = steps[0].length * cell / 2.0F;
            float u0 = mirrored ? sprite.getU1() : sprite.getU0();
            float u1 = mirrored ? sprite.getU0() : sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();
            Vector3f up = pose.normal().transform(new Vector3f(0.0F, 1.0F, 0.0F));
            corner(consumer, pose, -hx, half, -hz, u0, v0, red, green, blue, light, up);
            corner(consumer, pose, -hx, half, hz, u0, v1, red, green, blue, light, up);
            corner(consumer, pose, hx, half, hz, u1, v1, red, green, blue, light, up);
            corner(consumer, pose, hx, half, -hz, u1, v0, red, green, blue, light, up);
            Vector3f down = pose.normal().transform(new Vector3f(0.0F, -1.0F, 0.0F));
            float r = red * UNDERSIDE;
            float g = green * UNDERSIDE;
            float b = blue * UNDERSIDE;
            corner(consumer, pose, hx, -half, -hz, u1, v0, r, g, b, light, down);
            corner(consumer, pose, hx, -half, hz, u1, v1, r, g, b, light, down);
            corner(consumer, pose, -hx, -half, hz, u0, v1, r, g, b, light, down);
            corner(consumer, pose, -hx, -half, -hz, u0, v0, r, g, b, light, down);
        }

        private void quad(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, float size, float x, float y, float z, float red,
                float green, float blue, int light) {
            float h = size / 2.0F;
            float u0 = mirrored ? sprite.getU1() : sprite.getU0();
            float u1 = mirrored ? sprite.getU0() : sprite.getU1();
            Vector3f normal = pose.normal().transform(new Vector3f(0.0F, 1.0F, 0.0F));
            corner(consumer, pose, x - h, y, z - h, u0, sprite.getV0(), red, green, blue, light, normal);
            corner(consumer, pose, x - h, y, z + h, u0, sprite.getV1(), red, green, blue, light, normal);
            corner(consumer, pose, x + h, y, z + h, u1, sprite.getV1(), red, green, blue, light, normal);
            corner(consumer, pose, x + h, y, z - h, u1, sprite.getV0(), red, green, blue, light, normal);
        }

        private static void corner(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, float red, float green,
                float blue, int light, Vector3f normal) {
            Vector3f point = pose.pose().transformPosition(new Vector3f(x, y, z));
            int color = FastColor.ARGB32.colorFromFloat(1.0F, red, green, blue);
            consumer.addVertex(point.x, point.y, point.z, color, u, v, OverlayTexture.NO_OVERLAY, light, normal.x, normal.y, normal.z);
        }
    }
}
