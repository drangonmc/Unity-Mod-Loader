package net.fabricmc.fabric.api.event.registry;
import java.util.*;
import com.mojang.serialization.Codec;
import mc.unity.core.WorldRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
public final class DynamicRegistries {
    public enum SyncOption { SKIP_WHEN_EMPTY }
    public static <T> void register(ResourceKey<? extends Registry<T>> key, Codec<T> codec) {
        WorldRegistries.add(key, codec, null, false);
    }
    public static <T> void registerSynced(ResourceKey<? extends Registry<T>> key, Codec<T> codec,
                                          SyncOption... options) {
        registerSynced(key, codec, codec, options);
    }
    public static <T> void registerSynced(ResourceKey<? extends Registry<T>> key, Codec<T> codec,
                                          Codec<T> network, SyncOption... options) {
        WorldRegistries.add(key, codec, Objects.requireNonNull(network),
                            Arrays.asList(options).contains(SyncOption.SKIP_WHEN_EMPTY));
    }
    public static List<RegistryDataLoader.RegistryData<?>> getBootstrappingRegistries() {
        return List.copyOf(RegistryDataLoader.WORLDGEN_REGISTRIES);
    }
    public static List<RegistryDataLoader.RegistryData<?>> getWorldRegistries() {
        var result = new ArrayList<>(getBootstrappingRegistries());
        result.addAll(RegistryDataLoader.DIMENSION_REGISTRIES);
        return List.copyOf(result);
    }
    public static List<RegistryDataLoader.RegistryData<?>> getDynamicRegistries() {
        return getBootstrappingRegistries();
    }
}
