package com.pockyl.diegesis.impl.client.fx;

/**
 * The relief of a thick mass in one tank: heights over the liquid's level, node pixels, on a sheet that turns with the mass, so that a
 * swirl carries the relief round without blurring it. Whatever moves through pushes the mass out of its way: what lay where it goes is
 * piled into a ridge round it, mostly to its sides, so that a stroke leaves a furrow between two ridges and a heap where it stopped. The
 * mass then flows back where it stands steeper than it can hold, as a soft solid does: a batter holds no slope and levels, a porridge
 * holds a spoon's furrows and only their edges soften, ice cream holds its peaks. A still mass sleeps and costs nothing.
 */
public final class MassSim {
    /** Cells along a side of the sheet and how many tanks wide it is: the tank's corners must stay on it as it turns. */
    static final int CELLS = 48;
    static final float SPAN = 1.5F;
    private static final float STEP = 1.0F / 60.0F;
    private static final int MAX_STEPS = 4;
    /**
     * Flow back, as the share of what stands too steep between two cells that moves in a step: a batter (it levels in about a second and
     * a half) and the firmest mass; it stays under an eighth, where the step stays stable.
     */
    private static final float FLOW_RUNNY = 0.12F;
    private static final float FLOW_FIRM = 0.025F;
    /** The steepest slope the firmest mass holds; one half as firm holds about a third of it. */
    private static final float HOLDS_STEEPEST = 2.2F;
    /**
     * A furrow: how deep for its width at full strength; how far out its ridge reaches, in the furrow's radius; the highest a ridge piles,
     * pixels. A pass that goes on in one direction pushes most to its sides, a little ahead and nothing back where it came from.
     */
    private static final float DEPTH = 0.3F;
    private static final float RIDGE_REACH = 0.8F;
    private static final float HIGHEST = 1.3F;
    private static final float TO_SIDES = 1.0F;
    private static final float AHEAD = 0.45F;
    private static final float ALL_ROUND = 0.2F;
    /** Pushes this close in time (seconds) are one stroke, and one this far (in its radius) from the last is a new one. */
    private static final float STROKE_GAP = 0.2F;
    private static final float STROKE_JUMP = 4.0F;
    /** The most any cell moves in a step while the mass still flows, pixels. */
    private static final float ASLEEP = 1.0e-4F;
    /**
     * While something still works the mass (a push this recently, seconds), what it shaped before settles, so that the newest furrow
     * reads as the trail behind it and the strokes do not pile into one churned surface: how long that takes, a batter and the firmest
     * mass, seconds. Left alone, the mass keeps what it has.
     */
    private static final float WORKED_FOR = 0.3F;
    private static final float SETTLE_RUNNY = 0.8F;
    private static final float SETTLE_FIRM = 2.5F;
    /** How far the mass turns (radians) before what lies inside the walls is worked out again. */
    private static final float MASK_TURN = 0.02F;

    private final float[] heights = new float[CELLS * CELLS];
    private final float[] moves = new float[CELLS * CELLS];
    private final boolean[] inside = new boolean[CELLS * CELLS];
    private float maskTurn = Float.NaN;
    private boolean maskRound;
    private boolean round;
    /** How wide the tank is, pixels; one cell of the sheet in pixels. */
    private float width = 10.0F;
    private float cellPixels = width * SPAN / CELLS;
    private float firmness = 0.5F;
    private boolean flowing;
    private boolean shaped;
    private float stepDebt;
    private float clock;
    private float lastX = Float.NaN;
    private float lastZ;
    private float lastClock;

    /**
     * Advances the mass by {@code dt} seconds.
     *
     * @param turned   how far the mass has turned, radians
     * @param width    the tank's width, node pixels
     * @param round    the tank is round
     * @param firmness how firm it is now, 0 to 1: a mass still half a liquid holds less
     */
    public void update(float dt, float turned, float width, boolean round, float firmness) {
        clock += dt;
        this.width = Math.max(0.5F, width);
        cellPixels = this.width * SPAN / CELLS;
        this.round = round;
        if (firmness < this.firmness - 0.01F) {
            // Thinner than it was, it may flow again where it held.
            flowing = shaped;
        }
        this.firmness = firmness;
        boolean worked = shaped && !Float.isNaN(lastX) && clock - lastClock < WORKED_FOR;
        if (!flowing && !worked) {
            return;
        }
        mask(turned);
        if (worked) {
            float firm = Math.max(0.0F, Math.min(1.0F, firmness));
            float keep = (float) Math.exp(-dt / (SETTLE_RUNNY + (SETTLE_FIRM - SETTLE_RUNNY) * firm));
            for (int i = 0; i < heights.length; i++) {
                heights[i] *= keep;
            }
            flowing = true;
        }
        stepDebt = Math.min(stepDebt + dt, STEP * MAX_STEPS);
        while (stepDebt >= STEP && flowing) {
            stepDebt -= STEP;
            flow();
        }
    }

    /** How much stands over the level in all, the cells' heights summed: pushing the mass about and its flowing back keep it. */
    public float volume() {
        float sum = 0.0F;
        for (float height : heights) {
            sum += height;
        }
        return sum;
    }

    /** Whether anything was ever pushed or heaped: a flat mass needs no reading. */
    public boolean isShaped() {
        return shaped;
    }

    /** Levels the mass again: a new bowlful. */
    public void clear() {
        java.util.Arrays.fill(heights, 0.0F);
        shaped = false;
        flowing = false;
        lastX = Float.NaN;
    }

    /**
     * Something moves through the mass at {@code u, v} (0 to 1 across the tank), {@code width} a part of the tank's width, {@code strength}
     * 0 to 1: it digs its furrow there and piles what it took round it.
     */
    public void push(float u, float v, float width, float strength, float turned) {
        float cos = (float) Math.cos(turned);
        float sin = (float) Math.sin(turned);
        mask(turned);
        float x = u - 0.5F;
        float z = v - 0.5F;
        float cx = cell(x * cos + z * sin);
        float cz = cell(z * cos - x * sin);
        float radius = Math.max(1.0F, width * 0.5F / SPAN * CELLS);
        float depth = strength * DEPTH * width * this.width;
        float dirX = 0.0F;
        float dirZ = 0.0F;
        boolean moving = false;
        if (!Float.isNaN(lastX) && clock - lastClock < STROKE_GAP) {
            float dx = cx - lastX;
            float dz = cz - lastZ;
            float went = (float) Math.sqrt(dx * dx + dz * dz);
            if (went > 0.05F && went < STROKE_JUMP * radius) {
                dirX = dx / went;
                dirZ = dz / went;
                moving = true;
            }
        }
        lastX = cx;
        lastZ = cz;
        lastClock = clock;

        float reach = radius * (1.0F + RIDGE_REACH);
        int row0 = Math.max(0, (int) Math.floor(cz - reach));
        int row1 = Math.min(CELLS - 1, (int) Math.ceil(cz + reach));
        int column0 = Math.max(0, (int) Math.floor(cx - reach));
        int column1 = Math.min(CELLS - 1, (int) Math.ceil(cx + reach));
        float taken = 0.0F;
        for (int row = row0; row <= row1; row++) {
            for (int column = column0; column <= column1; column++) {
                int at = row * CELLS + column;
                if (!inside[at]) {
                    continue;
                }
                float dx = column - cx;
                float dz = row - cz;
                float near = (dx * dx + dz * dz) / (radius * radius);
                if (near < 1.0F) {
                    float floor = -depth * (float) Math.sqrt(1.0F - near);
                    if (heights[at] > floor) {
                        taken += heights[at] - floor;
                        heights[at] = floor;
                    }
                }
            }
        }
        if (taken <= 0.0F) {
            // Already as deep as this pass digs.
            return;
        }
        // The ridge round it: a tent from the furrow's edge out, heaviest a little way out, by where the stroke goes.
        float peak = radius * (1.0F + RIDGE_REACH * 0.35F);
        float spread = radius * RIDGE_REACH * 0.65F;
        float total = 0.0F;
        for (int pass = 0; pass < 2; pass++) {
            for (int row = row0; row <= row1; row++) {
                for (int column = column0; column <= column1; column++) {
                    int at = row * CELLS + column;
                    if (!inside[at]) {
                        continue;
                    }
                    float dx = column - cx;
                    float dz = row - cz;
                    float apart = (float) Math.sqrt(dx * dx + dz * dz);
                    if (apart < radius || apart > reach) {
                        continue;
                    }
                    float weight = Math.max(0.0F, 1.0F - Math.abs(apart - peak) / spread);
                    if (moving) {
                        float along = (dx * dirX + dz * dirZ) / apart;
                        weight *= ALL_ROUND + TO_SIDES * (1.0F - along * along) + AHEAD * Math.max(0.0F, along)
                                - ALL_ROUND * Math.max(0.0F, -along);
                    }
                    if (pass == 0) {
                        total += weight;
                    } else if (weight > 0.0F) {
                        heights[at] = Math.min(HIGHEST, heights[at] + taken * weight / total);
                    }
                }
            }
            if (total <= 0.0F) {
                // Nowhere inside the walls to put it: what was taken is lost, the furrow stays.
                break;
            }
        }
        shaped = true;
        flowing = true;
    }

    /**
     * A mound at {@code u, v} (0 to 1 across the tank), {@code radius} a part of the tank's width, {@code height} pixels; below 0 a
     * hollow.
     */
    public void mound(float u, float v, float radius, float height, float turned) {
        float cos = (float) Math.cos(turned);
        float sin = (float) Math.sin(turned);
        mask(turned);
        float x = u - 0.5F;
        float z = v - 0.5F;
        float cx = cell(x * cos + z * sin);
        float cz = cell(z * cos - x * sin);
        float cells = Math.max(0.8F, radius / SPAN * CELLS);
        int row0 = Math.max(0, (int) Math.floor(cz - cells));
        int row1 = Math.min(CELLS - 1, (int) Math.ceil(cz + cells));
        int column0 = Math.max(0, (int) Math.floor(cx - cells));
        int column1 = Math.min(CELLS - 1, (int) Math.ceil(cx + cells));
        for (int row = row0; row <= row1; row++) {
            for (int column = column0; column <= column1; column++) {
                int at = row * CELLS + column;
                float dx = column - cx;
                float dz = row - cz;
                float near = 1.0F - (dx * dx + dz * dz) / (cells * cells);
                if (inside[at] && near > 0.0F) {
                    heights[at] = Math.min(HIGHEST * 2.0F, heights[at] + height * near * near);
                }
            }
        }
        shaped = true;
        flowing = true;
    }

    /**
     * How high the mass stands over its level at {@code x, z}, from -0.5 to 0.5 across the tank, node pixels; {@code cos} and {@code sin}
     * of how far it has turned.
     */
    public float height(float x, float z, float cos, float sin) {
        float fx = cell(x * cos + z * sin);
        float fz = cell(z * cos - x * sin);
        int column = (int) Math.floor(fx);
        int row = (int) Math.floor(fz);
        if (column < 0 || row < 0 || column >= CELLS - 1 || row >= CELLS - 1) {
            return 0.0F;
        }
        float tx = fx - column;
        float tz = fz - row;
        int at = row * CELLS + column;
        float near = heights[at] + (heights[at + 1] - heights[at]) * tx;
        float far = heights[at + CELLS] + (heights[at + CELLS + 1] - heights[at + CELLS]) * tx;
        return near + (far - near) * tz;
    }

    /** A place on the tank (from its middle, in tanks) as a cell of the sheet, whose middles lie on whole numbers. */
    private static float cell(float fromMiddle) {
        return (fromMiddle / SPAN + 0.5F) * CELLS - 0.5F;
    }

    /** Which cells lie inside the walls with the mass turned so far. */
    private void mask(float turned) {
        if (Math.abs(turned - maskTurn) < MASK_TURN && maskRound == round) {
            return;
        }
        maskTurn = turned;
        maskRound = round;
        float cos = (float) Math.cos(turned);
        float sin = (float) Math.sin(turned);
        for (int row = 0; row < CELLS; row++) {
            float fz = ((row + 0.5F) / CELLS - 0.5F) * SPAN;
            for (int column = 0; column < CELLS; column++) {
                float fx = ((column + 0.5F) / CELLS - 0.5F) * SPAN;
                float x = fx * cos - fz * sin;
                float z = fz * cos + fx * sin;
                inside[row * CELLS + column] = round ? x * x + z * z <= 0.25F : Math.abs(x) <= 0.5F && Math.abs(z) <= 0.5F;
            }
        }
    }

    /** One step of flowing back: between neighbours, what stands steeper than the mass holds moves down, a share at a time. */
    private void flow() {
        float firm = Math.max(0.0F, Math.min(1.0F, firmness));
        float holds = HOLDS_STEEPEST * firm * (float) Math.sqrt(firm) * cellPixels;
        float share = FLOW_RUNNY + (FLOW_FIRM - FLOW_RUNNY) * firm;
        java.util.Arrays.fill(moves, 0.0F);
        float most = 0.0F;
        for (int row = 0; row < CELLS; row++) {
            for (int column = 0; column < CELLS; column++) {
                int at = row * CELLS + column;
                if (!inside[at]) {
                    continue;
                }
                if (column + 1 < CELLS && inside[at + 1]) {
                    most = Math.max(most, exchange(at, at + 1, holds, share));
                }
                if (row + 1 < CELLS && inside[at + CELLS]) {
                    most = Math.max(most, exchange(at, at + CELLS, holds, share));
                }
            }
        }
        for (int i = 0; i < heights.length; i++) {
            heights[i] += moves[i];
        }
        flowing = most > ASLEEP;
    }

    private float exchange(int one, int other, float holds, float share) {
        float difference = heights[one] - heights[other];
        float over = Math.abs(difference) - holds;
        if (over <= 0.0F) {
            return 0.0F;
        }
        float move = Math.copySign(over * share, difference);
        moves[one] -= move;
        moves[other] += move;
        return Math.abs(move);
    }
}
