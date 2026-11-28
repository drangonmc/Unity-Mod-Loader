package mc.unity.adapter.fabric;
import com.google.gson.*;
import java.io.*;
import java.lang.classfile.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import mc.unity.adapter.Entry;
import mc.unity.adapter.common.FmlDescriptor;
public final class FabricAdapter {
    public static String fixed(String key) {
        return switch (key) {
            case "minecraft" -> "26.2";
            case "java" -> "25";
            case "fabricloader" -> "0.19.3";
            case "fabric-api" -> "0.152.1+26.2";
            case "fabric-lifecycle-events-v1" -> "4.1.3+4575b05f9c";
            case "fabric-entity-events-v1" -> "5.0.5+06488ac19c";
            case "fabric-data-attachment-api-v1" -> "2.2.16+aa0fd6fe9c";
            case "fabric-resource-loader-v1" -> "2.0.13+9edec1269c";
            case "fabric-object-builder-api-v1" -> "24.0.6+46a6d00c9c";
            case "fabric-api-base" -> "2.0.4+ece063239c";
            case "fabric-creative-tab-api-v1" -> "5.0.14+d871b99e9c";
            case "fabric-registry-sync-v0" -> "7.1.0+c7bd5b8e9c";
            case "fabric-rendering-v1" -> "25.1.6+46a6d00c9c";
            case "fabric-model-loading-api-v1" -> "8.0.11+c80601bb9c";
            case "fabric-events-interaction-v0" -> "5.2.6+8f57f7ee9c";
            case "fabric-networking-api-v1" -> "6.3.3+72073ef09c";
            case "fabric-command-api-v2" -> "3.1.0+00cb03469e";
            default -> null;
        };
    }
    public static Entry read(JarFile jar, Path file, String side) throws Exception {
        return read(jar, file, side, false);
    }
    public static Entry read(JarFile jar, Path file, String side, boolean dataPack) throws Exception {
        var fab = jar.getJarEntry("fabric.mod.json");
        JsonObject json;
        try (var in = jar.getInputStream(fab)) {
            json = JsonParser.parseReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                       .getAsJsonObject();
        }
        String env = json.has("environment") ? json.get("environment").getAsString() : "*";
        if (!env.equals("*") && !env.equals(side.toLowerCase(Locale.ROOT)))
            return null;
        String id = json.get("id").getAsString();
        var names = new ArrayList<String>();
        var sided = new ArrayList<String>();
        var points = json.getAsJsonObject("entrypoints");
        if (points != null) {
            for (String key : points.keySet()) {
                boolean allowed = key.equals("main") || key.equals("client") || key.equals("server");
                if (!allowed)
                    continue; // Custom entrypoint keys are invoked only by their owning integrations.
                if (!key.equals("main") && !key.equals(side.toLowerCase(Locale.ROOT)))
                    continue;
                var entries = key.equals("main") ? names : sided;
                for (var n : points.getAsJsonArray(key)) {
                    if (n.isJsonPrimitive())
                        entries.add(n.getAsString());
                    else {
                        var obj = n.getAsJsonObject();
                        if (!obj.has("value"))
                            throw new IOException("Unsupported Fabric entrypoint adapter");
                        entries.add(obj.get("value").getAsString());
                    }
                }
            }
        }
        var deps = new ArrayList<FmlDescriptor.Dependency>();
        if (json.has("depends"))
            for (var kv : json.getAsJsonObject("depends").entrySet()) {
                String key = kv.getKey();
                var alternatives = new ArrayList<String>();
                if (kv.getValue().isJsonArray())
                    for (var predicate : kv.getValue().getAsJsonArray())
                        alternatives.add(predicate.getAsString());
                else
                    alternatives.add(kv.getValue().getAsString());
                String range = String.join(" || ", alternatives);
                if (LoaderView.upstream() && !key.equals("minecraft") && !key.equals("java") &&
                    !key.equals("fabricloader")) {
                    deps.add(new FmlDescriptor.Dependency(key, range, true, "AFTER"));
                    continue;
                }
                String fixed =
                    dataPack && key.equals("fabric-resource-loader-v0") ? "3.3.20+4fc5413f9c" : fixed(key);
                if (fixed != null)
                    FabricVersions.check(fixed, range);
                else
                    deps.add(new FmlDescriptor.Dependency(key, range, true, "AFTER"));
            }
        return new Entry(id, json.get("version").getAsString(), names, Entry.Kind.FABRIC, file, deps, sided);
    }
}
