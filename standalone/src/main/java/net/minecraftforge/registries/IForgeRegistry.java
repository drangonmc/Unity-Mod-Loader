package net.minecraftforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public interface IForgeRegistry<T> {
    ResourceKey<? extends Registry<T>> getRegistryKey();
    T getValue(Identifier id);
    Identifier getKey(T value);
}
