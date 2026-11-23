package net.neoforged.fml;
import java.util.*;
import net.neoforged.neoforgespi.language.IModInfo;
public final class ModList {
    private static final ModList INSTANCE = new ModList();
    private static volatile Map<String, ModContainer> containers = Map.of();
    public static ModList get() { return INSTANCE; }
    public static void bind(Map<String, ModContainer> mods) { containers = Map.copyOf(mods); }
    public Optional<ModContainer> getModContainerById(String id) {
        return Optional.ofNullable(containers.get(id));
    }
    public List<IModInfo> getMods() {
        return containers.values().stream().map(ModContainer::getModInfo).toList();
    }
    public int size() { return containers.size(); }
    public boolean isLoaded(String id) { return mc.unity.runtime.Platforms.present(id); }
}
