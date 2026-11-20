package com.pockyl.diegesis.impl.client.scene;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.scene.Box;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.impl.client.anim.Easing;

/**
 * What a node of a placed slot shows: the slot's item where its pose says. The node's matrix follows the pose with a short
 * ease, so that a turn glides and what loses its support comes down; an item that just arrived drops the last bit.
 */
public final class PlacedItem {
    private static final float EASE = 0.035F;
    /** How far (pixels) an item drops when it is put down, and in how many seconds. */
    private static final float DROP = 1.2F;
    private static final float DROP_TIME = 0.13F;
    /** How far (pixels) below the top of a burning item its flame starts. */
    private static final float FLAME_DEPTH = 0.5F;

    private final SlotRef slot;
    private final PlacingOptions options;
    /** The scene's moves of its surfaces, shared by all its placed items. */
    private final Matrix4f[] surfaceMoves;
    /** The scene's surfaces shut away now, shared by all its placed items. */
    private final boolean[] surfaceHidden;
    /** Where an item without a place of its own stands: chosen once, when it came, so that it stays there. */
    private ItemPose guessed;
    /** Finds that place: a free spot among the items standing there now. */
    private final Spots spots;
    private ItemStack stack = ItemStack.EMPTY;
    private PlacedShape shape;
    private ItemPose pose;
    private Box collider;
    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private float size;
    private float dropLeft;
    private float burning;
    /** Gone from sight until then (it fell out and is being taken); shown again where it stood if it is still there. */
    private long hiddenUntil;
    private boolean hidden;

    /** Where an item that came without a place stands. */
    interface Spots {
        ItemPose find(PlacedItem item, PlacedShape shape);

        /** The surface of a pose, also of one standing on another item. */
        PlaceSurface surfaceOf(PlacingOptions options, ItemPose pose);
    }

    PlacedItem(SlotRef slot, PlacingOptions options, Matrix4f[] surfaceMoves, boolean[] surfaceHidden, Spots spots) {
        this.slot = slot;
        this.options = options;
        this.surfaceMoves = surfaceMoves;
        this.surfaceHidden = surfaceHidden;
        this.spots = spots;
    }

    public SlotRef slot() {
        return slot;
    }

    public ItemStack stack() {
        return stack;
    }

    public PlacedShape shape() {
        return shape;
    }

    /** Where the item stands; null while the slot is empty. */
    public ItemPose pose() {
        return stack.isEmpty() ? null : pose;
    }

    public PlacingOptions options() {
        return options;
    }

    /** Seconds a refusing nod takes, and the degrees it tips. */
    private static final float NOD_TIME = 0.15F;
    private static final float NOD_ANGLE = 2.0F;
    private float nodLeft;

    /** A short nod: what the hand would put on it does not fit there. Seen only here; nothing moves. */
    public void nod() {
        nodLeft = NOD_TIME;
    }

    /** Out of sight at once: it left the station (fell out) and the server is taking it into the player's inventory. */
    public void hideWhileTaken(long millis) {
        hiddenUntil = Util.getMillis() + millis;
    }

    Box collider() {
        return collider;
    }

    /**
     * The item as the cursor finds it: its hit box where its pose is (never where hovering or easing draws it), with each side
     * at least {@code least} pixels; null while it is empty or out of sight.
     */
    Placing.Pickable pickable(float least) {
        ItemPose at = pose();
        if (at == null || shape == null || hidden || Util.getMillis() < hiddenUntil) {
            return null;
        }
        Vector3f half = new Vector3f(shape.hitHalf()).mul(shape.modelScale(at.scale() * options.itemSize()));
        Vector3f padded = new Vector3f(half).max(new Vector3f(least * 0.5F));
        Matrix4f frame = new Matrix4f().translation(at.x(), at.y() + at.lift(), at.z()).rotate(at.rotation()).rotate(shape.hitTurn());
        Matrix4f move = moveOf(at);
        Matrix4f toSurface = null;
        if (move != null) {
            new Matrix4f(move).mul(frame, frame);
            toSurface = new Matrix4f(move).invert();
        }
        return new Placing.Pickable(frame.invert(), half, padded, toSurface, at.y());
    }

    /** Takes the slot's item and pose over; returns whether an item stands there. */
    boolean sync(StationState state) {
        ItemStack now = state.getItem(slot);
        ItemPose wanted = state.getPose(slot);
        boolean arrived = stack.isEmpty() && !now.isEmpty();
        if (now.isEmpty()) {
            stack = ItemStack.EMPTY;
            guessed = null;
            hiddenUntil = 0L;
            hidden = false;
            return false;
        }
        if (!ItemStack.matches(stack, now) || shape == null) {
            if (!ItemStack.isSameItemSameComponents(stack, now)) {
                guessed = null;
            }
            stack = now;
            shape = PlacedShape.of(now, Minecraft.getInstance().level, options.blockModels(), options.framePose());
            Vector3fc half = shape.half();
            collider = new Box(new Vector3f(half).negate(), new Vector3f(half));
        }
        if (wanted != null) {
            // Kept: should the server say nothing of this place later (an item set out, never put by hand), it stays here
            // rather than being set out again elsewhere.
            guessed = wanted;
            pose = wanted;
        } else {
            if (guessed == null) {
                guessed = spots.find(this, shape);
            }
            pose = guessed;
        }
        if (arrived) {
            position.set(pose.x(), pose.y() + pose.lift(), pose.z());
            rotation.set(pose.rotation());
            size = pose.scale();
            dropLeft = DROP_TIME;
        }
        return true;
    }

    /** Writes the node's matrices for this frame. */
    void update(float dt, float hover, Matrix4f matrix, Matrix4f pickMatrix) {
        if (stack.isEmpty()) {
            return;
        }
        if (Util.getMillis() < hiddenUntil) {
            hidden = true;
            matrix.translation(position).scale(0.0F);
            pickMatrix.set(matrix);
            return;
        }
        if (hidden) {
            // Not taken after all (no room in the inventory): back where it stood, without gliding there.
            hidden = false;
            position.set(pose.x(), pose.y() + pose.lift(), pose.z());
            rotation.set(pose.rotation());
            size = pose.scale();
        }
        position.x = Easing.approach(position.x, pose.x(), EASE, dt);
        position.y = Easing.approach(position.y, pose.y() + pose.lift(), EASE * 2.0F, dt);
        position.z = Easing.approach(position.z, pose.z(), EASE, dt);
        rotation.slerp(pose.rotation(), 1.0F - (float) Math.pow(0.5, dt / EASE)).normalize();
        size = Easing.approach(size, pose.scale(), EASE, dt);
        dropLeft = Math.max(0.0F, dropLeft - dt);
        float fall = dropLeft / DROP_TIME;
        float scale = shape.modelScale(size * options.itemSize());
        nodLeft = Math.max(0.0F, nodLeft - dt);
        float nod = (float) Math.toRadians(NOD_ANGLE) * Mth.sin(Mth.PI * (1.0F - nodLeft / NOD_TIME)) * (nodLeft > 0.0F ? 1.0F : 0.0F);
        matrix.translation(position.x, position.y + DROP * fall * fall + options.hoverLift() * hover, position.z).rotateX(nod).rotate(rotation)
                .scale(scale);
        // The hit box stays where the pose is: it never slips from under the cursor by what hovering does.
        pickMatrix.translation(pose.x(), pose.y() + pose.lift(), pose.z()).rotate(pose.rotation()).scale(shape.modelScale(pose.scale() * options.itemSize()));
        Matrix4f move = moveOf(pose);
        if (move != null) {
            // It stands on a part that moves (a drawer, a door): it goes along, and so does where the cursor finds it.
            move.mul(matrix, matrix);
            move.mul(pickMatrix, pickMatrix);
        }
    }

    /** The move of the surface the item stands on, or null when it stays where the scene has it. */
    private Matrix4f moveOf(ItemPose at) {
        if (surfaceMoves.length == 0) {
            return null;
        }
        PlaceSurface surface = spots.surfaceOf(options, at);
        int index = surface == null ? -1 : options.surfaces().indexOf(surface);
        if (index < 0 || index >= surfaceMoves.length) {
            return null;
        }
        Matrix4f move = surfaceMoves[index];
        return (move.properties() & org.joml.Matrix4fc.PROPERTY_IDENTITY) != 0 ? null : move;
    }

    /** Counts the time for a burning item's next flame; true when one is due. */
    public boolean burns(float dt) {
        if (stack.isEmpty() || !shape.hasFlame() || !options.itemLight()) {
            return false;
        }
        burning += dt;
        if (burning < 0.12F) {
            return false;
        }
        burning = 0.0F;
        return true;
    }

    /** Where the flame sits: the top of the item, in scene pixels. */
    public Vector3f flamePosition() {
        Vector3f top = new Vector3f(0.0F, shape.half().y() * shape.modelScale(size * options.itemSize()) - FLAME_DEPTH, 0.0F);
        return rotation.transform(top).add(position);
    }
}
