package com.pockyl.diegesis.impl.client.mesh;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.mixin.client.ItemStackRenderStateAccessor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Items whose look is a {@link MeshShape} made from their data. The item keeps the model of its JSON file for its display
 * transforms; {@code ItemModelResolverMixin} swaps its quads wherever the game builds the look of a stack, so inventories, frames,
 * hands and shelves all show the shape. Baked shapes are kept per item data, until the textures are stitched anew.
 */
public final class ItemShapes {
    private static final int KEPT_SHAPES = 256;
    private static final Map<Item, Entry> ITEMS = new ConcurrentHashMap<>();

    private ItemShapes() {
    }

    public static void register(Item item, Function<ItemStack, MeshShape> shape, Identifier texture) {
        ITEMS.put(item, new Entry(shape, texture));
    }

    /**
     * Gives the layers of {@code look} from {@code firstLayer} on, the ones the item's model just added, the shape of {@code stack}
     * when its item has one.
     */
    public static void apply(ItemStack stack, ItemStackRenderStateAccessor look, int firstLayer) {
        Entry entry = ITEMS.isEmpty() ? null : ITEMS.get(stack.getItem());
        if (entry == null) {
            return;
        }
        Shape shape = entry.shape(stack);
        ItemStackRenderState.LayerRenderState[] layers = look.diegesis$layers();
        for (int i = firstLayer; i < look.diegesis$layerCount(); i++) {
            List<BakedQuad> quads = layers[i].prepareQuadList();
            quads.clear();
            quads.addAll(shape.quads());
            // Drawn as an item of the block atlas, cut out, as the shape's quads are.
            layers[i].setRenderType(Sheets.cutoutBlockSheet());
            layers[i].setExtents(shape::extents);
            layers[i].setUsesBlockLight(true);
        }
    }

    /** Models are new after a resource reload, and the sprites with them. */
    public static void clearCache() {
        ITEMS.values().forEach(entry -> entry.baked.clear());
    }

    private static final class Entry {
        private final Function<ItemStack, MeshShape> shape;
        private final Identifier texture;
        private final Map<DataComponentPatch, Shape> baked = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<DataComponentPatch, Shape> eldest) {
                return size() > KEPT_SHAPES;
            }
        };

        Entry(Function<ItemStack, MeshShape> shape, Identifier texture) {
            this.shape = shape;
            this.texture = texture;
        }

        Shape shape(ItemStack stack) {
            DataComponentPatch key = stack.getComponentsPatch();
            Shape found = baked.get(key);
            if (found == null) {
                TextureAtlasSprite sprite = com.pockyl.diegesis.impl.client.render.SceneDraw.blockSprite(texture);
                List<BakedQuad> quads = shape.apply(stack).mesh().quads(sprite);
                Set<Vector3fc> corners = new HashSet<>();
                for (BakedQuad quad : quads) {
                    for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                        corners.add(quad.position(vertex));
                    }
                }
                found = new Shape(quads, corners.toArray(Vector3fc[]::new));
                baked.put(key, found);
            }
            return found;
        }
    }

    /** A shape baked for one item's data: its quads, and their corners (what the game measures an item by). */
    private record Shape(List<BakedQuad> quads, Vector3fc[] extents) {
    }
}
