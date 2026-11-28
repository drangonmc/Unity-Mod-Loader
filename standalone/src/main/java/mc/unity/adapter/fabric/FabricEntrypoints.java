package mc.unity.adapter.fabric;
import com.google.gson.*;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.util.*;
/** Only suppress gameplay preflight for the exclusive dependency tree of an inactive entrypoint. */
public final class FabricEntrypoints {
    public static Set<String> inactive(JsonObject metadata, boolean client, List<ClassModel> classes) {
        if (metadata == null || !metadata.has("entrypoints"))
            return Set.of();
        var active = new HashSet<String>();
        var inactive = new HashSet<String>();
        for (var row : metadata.getAsJsonObject("entrypoints").entrySet()) {
            var key = row.getKey();
            var roots = key.equals("preLaunch") || key.equals("main") || key.equals(client ? "client" : "server") ? active : inactive;
            for (var entry : row.getValue().getAsJsonArray()) {
                String value = entry.isJsonPrimitive() ? entry.getAsString()
                                                       : entry.getAsJsonObject().get("value").getAsString();
                roots.add(value.split("::", 2)[0].replace('.', '/'));
            }
        }
        if (inactive.isEmpty())
            return Set.of();
        var local = new HashMap<String, ClassModel>();
        for (var cls : classes)
            local.put(cls.thisClass().asInternalName(), cls);
        var sideOnly = new HashSet<>(inactive);
        sideOnly.removeAll(active);
        expand(active, local, sideOnly);
        expand(inactive, local, Set.of());
        inactive.removeAll(active);
        return inactive;
    }
    private static void expand(Set<String> found, Map<String, ClassModel> local, Set<String> excluded) {
        var queue = new ArrayDeque<>(found);
        while (!queue.isEmpty()) {
            var type = local.get(queue.remove());
            if (type == null)
                continue;
            for (var constant : type.constantPool())
                if (constant instanceof ClassEntry c && local.containsKey(c.asInternalName()) &&
                    !excluded.contains(c.asInternalName()) && found.add(c.asInternalName()))
                    queue.add(c.asInternalName());
        }
    }
}
