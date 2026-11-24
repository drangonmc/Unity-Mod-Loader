package mc.unity.adapter.fabric;
import java.util.*;
import net.fabricmc.loader.api.MappingResolver;
/** Mojang distributes 26.2 with official runtime names. */
enum Names implements MappingResolver {
    INSTANCE;
    public Collection<String> getNamespaces() {
        return List.of("official");
    }
    public String getCurrentRuntimeNamespace() {
        return "official";
    }
    public String mapClassName(String ns, String name) {
        return name;
    }
    public String unmapClassName(String ns, String name) {
        return name;
    }
    public String mapMethodName(String ns, String owner, String name, String desc) {
        return name;
    }
    public String mapFieldName(String ns, String owner, String name, String desc) {
        return name;
    }
}
