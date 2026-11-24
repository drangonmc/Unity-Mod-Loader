package net.fabricmc.loader.api;
import net.fabricmc.api.EnvType;
import java.nio.file.*;
import java.util.*;
public interface FabricLoader {
    static FabricLoader getInstance() {
        return mc.unity.adapter.fabric.LoaderView.INSTANCE;
    }
    EnvType getEnvironmentType();
    Path getGameDir();
    Path getConfigDir();
    boolean isDevelopmentEnvironment();
    boolean isModLoaded(String id);
    Optional<ModContainer> getModContainer(String id);
    Collection<ModContainer> getAllMods();
    MappingResolver getMappingResolver();
    <T> List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>>
    getEntrypointContainers(String key, Class<T> type);
    default<T> List<T> getEntrypoints(String key, Class<T> type) {
        return getEntrypointContainers(key, type)
            .stream()
            .map(net.fabricmc.loader.api.entrypoint.EntrypointContainer::getEntrypoint)
            .toList();
    }
}
