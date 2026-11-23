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
}
