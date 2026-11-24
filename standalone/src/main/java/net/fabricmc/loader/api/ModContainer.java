package net.fabricmc.loader.api;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.metadata.ModMetadata;
public interface ModContainer {
    ModMetadata getMetadata();
    List<Path> getRootPaths();
    default Optional<Path> findPath(String name) {
        for (var root : getRootPaths()) {
            var p = root.resolve(name).normalize();
            if (p.startsWith(root) && Files.exists(p))
                return Optional.of(p);
        }
        return Optional.empty();
    }
    Optional<ModContainer> getContainingMod();
    Collection<ModContainer> getContainedMods();
}
