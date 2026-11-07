package com.pockyl.diegesis.impl.client.mesh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.client.MeshShape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Items whose look is a {@link MeshShape} made from their data. The item keeps the model of its JSON file for its display
 * transforms; {@code ItemRendererMixin} swaps it where the game asks for the model of a stack, so inventories, frames, hands and
 * shelves all show the shape. Baked shapes are kept per item data, until the textures are stitched anew.
 */
public final class ItemShapes {
    private static final int KEPT_SHAPES = 256;
    private static final Map<Item, Entry> ITEMS = new ConcurrentHashMap<>();

    private ItemShapes() {
    }

    public static void register(Item item, Function<ItemStack, MeshShape> shape, ResourceLocation texture) {
        ITEMS.put(item, new Entry(shape, texture));
    }

    /** The model to draw {@code stack} with: its shape, or {@code model} for every other item. */
    public static BakedModel resolve(ItemStack stack, BakedModel model) {
        Entry entry = ITEMS.isEmpty() ? null : ITEMS.get(stack.getItem());
        return entry == null ? model : entry.model(stack, model);
    }

    /** Models are new after a resource reload, and the sprites with them. */
    public static void clearCache() {
        ITEMS.values().forEach(entry -> entry.baked.clear());
    }

    private static final class Entry {
        private final Function<ItemStack, MeshShape> shape;
        private final ResourceLocation texture;
        private final Map<CompoundTag, BakedModel> baked = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CompoundTag, BakedModel> eldest) {
                return size() > KEPT_SHAPES;
            }
        };

        Entry(Function<ItemStack, MeshShape> shape, ResourceLocation texture) {
            this.shape = shape;
            this.texture = texture;
        }

        BakedModel model(ItemStack stack, BakedModel original) {
            CompoundTag key = stack.hasTag() ? stack.getTag() : new CompoundTag();
            BakedModel found = baked.get(key);
            if (found == null) {
                TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
                found = new Shaped(original, shape.apply(stack).mesh().quads(sprite));
                baked.put(key.copy(), found);
            }
            return found;
        }
    }

    /** Fixed quads with the display transforms of the item's JSON model. */
    private static final class Shaped implements BakedModel {
        private final BakedModel original;
        private final List<BakedQuad> quads;

        Shaped(BakedModel original, List<BakedQuad> quads) {
            this.original = original;
            this.quads = quads;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return side == null ? quads : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return original.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return original.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
