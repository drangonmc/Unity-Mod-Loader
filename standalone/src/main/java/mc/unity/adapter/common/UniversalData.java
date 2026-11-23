package mc.unity.adapter.common;
import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import com.google.gson.JsonObject;

/** Recognize the same declarative data pack offered through multiple platform descriptors. */
public final class UniversalData {
    public static boolean matches(JarFile jar) throws IOException {
        boolean forge = jar.getJarEntry("META-INF/mods.toml") != null,
                neo = jar.getJarEntry("META-INF/neoforge.mods.toml") != null;
        if (!forge && !neo || jar.getJarEntry("fabric.mod.json") == null ||
            jar.getJarEntry("pack.mcmeta") == null)
            return false;
        JsonObject fabric = NestedArchives.json(jar, "fabric.mod.json");
        if (fabric.has("accessWidener") || fabric.has("jars") || fabric.has("mixins") ||
            fabric.has("languageAdapters") ||
            fabric.has("entrypoints") && !fabric.getAsJsonObject("entrypoints").isEmpty())
            return false;
        if (jar.stream()
                .filter(e -> !e.isDirectory())
                .map(e -> e.getName())
                .anyMatch(n
                          -> n.endsWith(".class") || n.endsWith(".jar") || n.startsWith("assets/") ||
                                 n.contains("/assets/") || n.startsWith("META-INF/services/") ||
                                 n.equals("META-INF/coremods.json") ||
                                 n.equals("META-INF/accesstransformer.cfg") ||
                                 n.equals("META-INF/jarjar/metadata.json")))
            return false;
        var manifest = jar.getManifest();
        if (manifest != null && (manifest.getMainAttributes().getValue("MixinConfigs") != null ||
                                 manifest.getMainAttributes().getValue("FMLAT") != null))
            return false;
        for (boolean isNeo : new boolean[] {false, true}) {
            if (isNeo ? !neo : !forge)
                continue;
            var doc = FmlDescriptor.document(jar, isNeo);
            if (!Set.of("lowcodefml", "javafml").contains(FmlDescriptor.string(doc, "modLoader", "")) ||
                doc.contains("mixins") || doc.contains("accessTransformers"))
                return false;
            var mods = FmlDescriptor.tables(doc, "mods");
            if (mods.size() != 1 ||
                !fabric.get("id").getAsString().equals(FmlDescriptor.string(mods.getFirst(), "modId", "")) ||
                !fabric.get("version").getAsString().equals(
                    FmlDescriptor.string(mods.getFirst(), "version", "")))
                return false;
        }
        return jar.stream().anyMatch(e -> !e.isDirectory() && e.getName().startsWith("data/"));
    }
}
