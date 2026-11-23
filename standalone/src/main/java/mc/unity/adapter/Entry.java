package mc.unity.adapter;
import java.nio.file.Path;
import java.util.List;
import mc.unity.adapter.common.FmlDescriptor;
public record Entry(String id, String version, List<String> classes, Kind kind, Path file,
    List<FmlDescriptor.Dependency> deps, List<String> sideClasses) {
    public Entry(String id, String version, List<String> classes, Kind kind, Path file,
        List<FmlDescriptor.Dependency> deps) {
        this(id, version, classes, kind, file, deps, List.of());
    }
    public enum Kind { FORGE, FABRIC, NEOFORGE }
}
