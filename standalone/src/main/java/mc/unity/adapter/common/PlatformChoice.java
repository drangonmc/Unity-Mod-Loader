package mc.unity.adapter.common;

import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import mc.unity.adapter.Entry;

/** One descriptor family owns discovery, dependencies, access changes, initialization and Mixin. */
public final class PlatformChoice {
    public record Choice(Entry.Kind kind, boolean multiple) {}
    private static final Map<JarFile, Choice> cache = new WeakHashMap<>();
    public static Choice read(JarFile jar) throws IOException {
        synchronized (cache) {
            var choice = cache.get(jar);
            if (choice != null)
                return choice;
        }
        var result = inspect(jar);
        synchronized (cache) {
            cache.put(jar, result);
        }
        return result;
    }
    private static Choice inspect(JarFile jar) throws IOException {
        boolean fabric = jar.getJarEntry("fabric.mod.json") != null;
        boolean forge = jar.getJarEntry("META-INF/mods.toml") != null;
        boolean neo = jar.getJarEntry("META-INF/neoforge.mods.toml") != null;
        int families = (fabric ? 1 : 0) + (forge ? 1 : 0) + (neo ? 1 : 0);
        var kind = fabric ? Entry.Kind.FABRIC : neo ? Entry.Kind.NEOFORGE : forge ? Entry.Kind.FORGE : null;
        if (families < 2)
            return new Choice(kind, false);
        var identity = new TreeMap<String, String>();
        if (fabric) {
            var metadata = NestedArchives.json(jar, "fabric.mod.json");
            identity.put(metadata.get("id").getAsString(), metadata.get("version").getAsString());
        }
        for (boolean n : List.of(false, true)) {
            if (n ? !neo : !forge)
                continue;
            var declared = new TreeMap<String, String>();
            for (var row : FmlDescriptor.tables(FmlDescriptor.document(jar, n), "mods")) {
                String id = FmlDescriptor.string(row, "modId", ""),
                       ver = FmlDescriptor.version(row, FmlDescriptor.implementationVersion(jar));
                if (id.isBlank() || ver.isBlank() || declared.putIfAbsent(id, ver) != null)
                    throw new IOException("Ambiguous platform descriptors: invalid mod identity in " +
                                          jar.getName());
            }
            if (declared.isEmpty() || !identity.isEmpty() && !identity.equals(declared))
                throw new IOException("Ambiguous platform descriptors: conflicting mod IDs or versions " +
                                      identity + " / " + declared + " in " + jar.getName());
            identity.putAll(declared);
        }
        if (!UniversalData.matches(jar) &&
            jar.versionedStream().noneMatch(
                e -> e.getName().endsWith(".class") && !e.getName().endsWith("module-info.class")))
            throw new IOException("Ambiguous platform descriptors: no executable or supported data pack in " +
                                  jar.getName());
        return new Choice(kind, true);
    }
}
