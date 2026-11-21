package com.pockyl.diegesis.impl.client.scene;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Where two turned boxes touch: the way they overlap least (the faces of each, the crossings of their edges) and, face to
 * face, the patch where the faces meet (the face of one clipped to the sides of the other), up to four points; edge across
 * edge, the one point where the edges pass closest. A box lying across another touches it at the corners of the patch they
 * share, the same points step after step, so nothing tips or spins by a contact that jumps between corners.
 *
 * <p>Owned by one physics: the scratch vectors are reused, nothing is made per call.
 */
public final class PlacedContacts {
    /** Most points a touch keeps. */
    public static final int MOST = 4;
    /** Of two ways nearly as good, the one touched along before wins unless the other is better by this share, plus a hair. */
    private static final float KEEP_SHARE = 0.05F;
    private static final float KEEP_HAIR = 0.005F;
    /** An edge's way is a hair worse than a face's: a box resting on another touches it face to face. */
    private static final float EDGE_HAIR = 0.01F;
    private static final float EDGE_SHARE = 0.05F;

    /** The touch: {@link #count} points, each on {@code a} and on {@code b}, how deep; the normal from b to a (a goes out along it). */
    public static final class Manifold {
        public int count;
        /** The way they touch along, 0..14: a's faces, b's faces, then the crossings of a's and b's edges. */
        public int axis;
        public final Vector3f normal = new Vector3f();
        public final Vector3f[] onA = new Vector3f[MOST];
        public final Vector3f[] onB = new Vector3f[MOST];
        public final float[] depth = new float[MOST];

        public Manifold() {
            for (int i = 0; i < MOST; i++) {
                onA[i] = new Vector3f();
                onB[i] = new Vector3f();
            }
        }
    }

    private final Vector3f between = new Vector3f();
    private final Vector3f axis = new Vector3f();
    private final Vector3f best = new Vector3f();
    private final Vector3f kept = new Vector3f();
    private final Vector3f faceRef = new Vector3f();
    private final Vector3f normalRef = new Vector3f();
    private final Vector3f faceInc = new Vector3f();
    private final Vector3f normalInc = new Vector3f();
    private final Vector3f edgeA = new Vector3f();
    private final Vector3f edgeB = new Vector3f();
    private final Vector3f rel = new Vector3f();
    private final Vector3f[] polygon = new Vector3f[16];
    private final Vector3f[] clipped = new Vector3f[16];
    private final Vector3f[] points = new Vector3f[16];
    private final float[] seps = new float[16];

    public PlacedContacts() {
        for (int i = 0; i < 16; i++) {
            polygon[i] = new Vector3f();
            clipped[i] = new Vector3f();
            points[i] = new Vector3f();
        }
    }

    private static float get(Vector3fc v, int i) {
        return i == 0 ? v.x() : i == 1 ? v.y() : v.z();
    }

    private static float reach(Vector3fc[] axes, Vector3fc half, Vector3fc way) {
        return half.x() * Math.abs(axes[0].dot(way)) + half.y() * Math.abs(axes[1].dot(way)) + half.z() * Math.abs(axes[2].dot(way));
    }

    /**
     * The touch of box a (middle, its three sides as unit vectors in the room, half size along them) and box b.
     *
     * @param margin    pixels apart two boxes still count as touching: points that far apart come with a negative depth
     * @param kept      the way this pair touched along last time, or -1
     * @return false when the boxes are further apart than {@code margin}
     */
    public boolean boxBox(Vector3fc centerA, Vector3fc[] axesA, Vector3fc halfA, Vector3fc centerB, Vector3fc[] axesB, Vector3fc halfB,
            float margin, int kept, Manifold out) {
        between.set(centerA).sub(centerB);
        int bestAxis = -1;
        float bestScore = Float.MAX_VALUE;
        float keptScore = Float.NaN;
        float keptOverlap = 0.0F;
        for (int test = 0; test < 15; test++) {
            if (test < 3) {
                axis.set(axesA[test]);
            } else if (test < 6) {
                axis.set(axesB[test - 3]);
            } else {
                axesA[(test - 6) / 3].cross(axesB[(test - 6) % 3], axis);
                if (axis.lengthSquared() < 1.0e-6F) {
                    continue;
                }
                axis.normalize();
            }
            float overlap = reach(axesA, halfA, axis) + reach(axesB, halfB, axis) - Math.abs(between.dot(axis));
            if (overlap < -margin) {
                return false;
            }
            float score = test < 6 ? overlap : overlap + EDGE_HAIR + EDGE_SHARE * Math.abs(overlap);
            if (test == kept) {
                keptScore = score;
                keptOverlap = overlap;
                this.kept.set(axis);
            }
            if (score < bestScore) {
                bestScore = score;
                bestAxis = test;
                best.set(axis);
            }
        }
        if (bestAxis < 0) {
            return false;
        }
        if (!Float.isNaN(keptScore) && keptScore <= bestScore + KEEP_SHARE * Math.abs(keptOverlap) + KEEP_HAIR) {
            // Two ways nearly as good (a thin plate leaning on something): the one touched along before, so the push does not
            // flip between them step after step.
            bestAxis = kept;
            best.set(this.kept);
        }
        Vector3f normal = out.normal.set(best);
        if (normal.dot(between) < 0.0F) {
            normal.negate();
        }
        out.axis = bestAxis;
        out.count = 0;
        if (bestAxis < 3) {
            // a's face: b's face meets it. The reference face's normal points from a towards b.
            face(centerA, axesA, halfA, bestAxis, normalRef.set(normal).negate(), centerB, axesB, halfB, margin, true, out);
        } else if (bestAxis < 6) {
            face(centerB, axesB, halfB, bestAxis - 3, normalRef.set(normal), centerA, axesA, halfA, margin, false, out);
        } else {
            edges(centerA, axesA, halfA, (bestAxis - 6) / 3, centerB, axesB, halfB, (bestAxis - 6) % 3, margin, out);
        }
        return out.count > 0;
    }

    /**
     * Face to face: the incident box's face most against the reference face, clipped to the reference face's sides; each corner
     * of what is left no further than {@code margin} off the reference face is a point.
     */
    private void face(Vector3fc centerR, Vector3fc[] axesR, Vector3fc halfR, int side, Vector3f normal, Vector3fc centerI, Vector3fc[] axesI,
            Vector3fc halfI, float margin, boolean refIsA, Manifold out) {
        faceRef.set(normal).mul(get(halfR, side)).add(centerR);
        int t1 = (side + 1) % 3;
        int t2 = (side + 2) % 3;
        // The incident face: the side of the other box most against the reference face.
        int incident = 0;
        float most = -1.0F;
        for (int k = 0; k < 3; k++) {
            float along = Math.abs(axesI[k].dot(normal));
            if (along > most) {
                most = along;
                incident = k;
            }
        }
        float sign = axesI[incident].dot(normal) > 0.0F ? -1.0F : 1.0F;
        normalInc.set(axesI[incident]).mul(sign);
        faceInc.set(normalInc).mul(get(halfI, incident)).add(centerI);
        Vector3fc a1 = axesI[(incident + 1) % 3];
        Vector3fc a2 = axesI[(incident + 2) % 3];
        float f1 = get(halfI, (incident + 1) % 3);
        float f2 = get(halfI, (incident + 2) % 3);
        polygon[0].set(faceInc).fma(f1, a1).fma(f2, a2);
        polygon[1].set(faceInc).fma(-f1, a1).fma(f2, a2);
        polygon[2].set(faceInc).fma(-f1, a1).fma(-f2, a2);
        polygon[3].set(faceInc).fma(f1, a1).fma(-f2, a2);
        int count = 4;
        count = clip(count, axesR[t1], 1.0F, centerR, get(halfR, t1));
        count = clip(count, axesR[t1], -1.0F, centerR, get(halfR, t1));
        count = clip(count, axesR[t2], 1.0F, centerR, get(halfR, t2));
        count = clip(count, axesR[t2], -1.0F, centerR, get(halfR, t2));
        int kept = 0;
        for (int i = 0; i < count; i++) {
            float sep = rel.set(polygon[i]).sub(faceRef).dot(normal);
            if (sep <= margin) {
                points[kept].set(polygon[i]);
                seps[kept] = sep;
                kept++;
            }
        }
        reduce(kept, normal, out);
        for (int i = 0; i < out.count; i++) {
            // On the incident box the point itself; on the reference box the same point down on its face.
            Vector3f onInc = refIsA ? out.onB[i] : out.onA[i];
            Vector3f onRef = refIsA ? out.onA[i] : out.onB[i];
            onRef.set(onInc).fma(out.depth[i], normal);
        }
    }

    /** Keeps the polygon's part on the inner side of the plane {@code way . (p - center) <= reach} (way = sign * side). */
    private int clip(int count, Vector3fc side, float sign, Vector3fc center, float reach) {
        if (count == 0) {
            return 0;
        }
        int made = 0;
        for (int i = 0; i < count; i++) {
            Vector3f p = polygon[i];
            Vector3f q = polygon[(i + 1) % count];
            float dp = sign * (side.x() * (p.x - center.x()) + side.y() * (p.y - center.y()) + side.z() * (p.z - center.z())) - reach;
            float dq = sign * (side.x() * (q.x - center.x()) + side.y() * (q.y - center.y()) + side.z() * (q.z - center.z())) - reach;
            if (dp <= 0.0F) {
                clipped[made++].set(p);
            }
            if (dp <= 0.0F != dq <= 0.0F && made < clipped.length) {
                float k = dp / (dp - dq);
                clipped[made++].set(q).sub(p).mul(k).add(p);
            }
        }
        for (int i = 0; i < made; i++) {
            polygon[i].set(clipped[i]);
        }
        return made;
    }

    /** Keeps at most four of the {@code count} points: the deepest, the furthest from it, and the widest on each side of those two. */
    private void reduce(int count, Vector3f normal, Manifold out) {
        out.count = 0;
        if (count <= MOST) {
            for (int i = 0; i < count; i++) {
                add(out, i);
            }
            return;
        }
        int first = 0;
        for (int i = 1; i < count; i++) {
            if (seps[i] < seps[first]) {
                first = i;
            }
        }
        int second = -1;
        float far = -1.0F;
        for (int i = 0; i < count; i++) {
            float d = points[i].distanceSquared(points[first]);
            if (i != first && d > far) {
                far = d;
                second = i;
            }
        }
        int third = -1;
        int fourth = -1;
        float high = 1.0e-6F;
        float low = -1.0e-6F;
        edgeA.set(points[second]).sub(points[first]);
        for (int i = 0; i < count; i++) {
            if (i == first || i == second) {
                continue;
            }
            float area = edgeA.cross(rel.set(points[i]).sub(points[first]), edgeB).dot(normal);
            if (area > high) {
                high = area;
                third = i;
            }
            if (area < low) {
                low = area;
                fourth = i;
            }
        }
        add(out, first);
        add(out, second);
        if (third >= 0) {
            add(out, third);
        }
        if (fourth >= 0) {
            add(out, fourth);
        }
    }

    /** Takes point {@code index} as the next of the touch, on the incident box (face() moves the reference one onto its face). */
    private void add(Manifold out, int index) {
        out.onA[out.count].set(points[index]);
        out.onB[out.count].set(points[index]);
        out.depth[out.count] = -seps[index];
        out.count++;
    }

    /** Edge across edge: the closest points of the two edges that reach furthest into each other. */
    private void edges(Vector3fc centerA, Vector3fc[] axesA, Vector3fc halfA, int i, Vector3fc centerB, Vector3fc[] axesB, Vector3fc halfB,
            int j, float margin, Manifold out) {
        Vector3f normal = out.normal;
        // a's edge along its side i nearest b (against the normal), b's edge along its side j nearest a.
        edgeA.set(centerA);
        for (int k = 0; k < 3; k++) {
            if (k != i) {
                edgeA.fma(axesA[k].dot(normal) > 0.0F ? -get(halfA, k) : get(halfA, k), axesA[k]);
            }
        }
        edgeB.set(centerB);
        for (int k = 0; k < 3; k++) {
            if (k != j) {
                edgeB.fma(axesB[k].dot(normal) > 0.0F ? get(halfB, k) : -get(halfB, k), axesB[k]);
            }
        }
        Vector3fc dirA = axesA[i];
        Vector3fc dirB = axesB[j];
        rel.set(edgeA).sub(edgeB);
        float b = dirA.dot(dirB);
        float c = dirA.dot(rel);
        float f = dirB.dot(rel);
        float denominator = 1.0F - b * b;
        float s = denominator > 1.0e-6F ? clamp((b * f - c) / denominator, get(halfA, i)) : 0.0F;
        float t = clamp(b * s + f, get(halfB, j));
        s = clamp(b * t - c, get(halfA, i));
        out.onA[0].set(edgeA).fma(s, dirA);
        out.onB[0].set(edgeB).fma(t, dirB);
        float depth = rel.set(out.onB[0]).sub(out.onA[0]).dot(normal);
        if (depth < -margin) {
            out.count = 0;
            return;
        }
        out.depth[0] = depth;
        out.count = 1;
    }

    private static float clamp(float value, float half) {
        return Math.max(-half, Math.min(half, value));
    }
}
