package net.neoforged.neoforge.registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
public class DeferredItem<T extends Item>
    extends DeferredHolder<Item, T> implements net.minecraft.world.level.ItemLike {
    DeferredItem(ResourceKey<Item> key, Supplier<? extends T> factory) {
        super(key, factory);
    }
    public Item asItem() {
        return get().asItem();
    }
    public ItemStack toStack() {
        return toStack(1);
    }
    public ItemStack toStack(int count) {
        return new ItemStack(asItem(), count);
    }
}
