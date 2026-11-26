package mc.unity.adapter.common;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
public final class MixinDeclarations {
    public static List<String> read(JarFile jar, boolean client) throws IOException {
        var names = new LinkedHashSet<String>();
        var choice = PlatformChoice.read(jar);
        if (!client && choice.kind()==mc.unity.adapter.Entry.Kind.FORGE && FmlDescriptor.clientSideOnly(jar)) return null;
        var mf = jar.getManifest();
        if (mf != null && (!choice.multiple() || choice.kind() == mc.unity.adapter.Entry.Kind.FORGE)) {
            String value = mf.getMainAttributes().getValue("MixinConfigs");
            if (value != null)
                for (String n : value.split(","))
                    names.add(n.strip());
        }
        var fabric = jar.getJarEntry("fabric.mod.json");
        var neo = jar.getJarEntry("META-INF/neoforge.mods.toml");
        if (fabric != null)
            try (var reader = new InputStreamReader(jar.getInputStream(fabric), StandardCharsets.UTF_8)) {
                var obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (!matches(obj, "environment", client))
                    return null;
                if (obj.has("mixins"))
                    for (var value : obj.getAsJsonArray("mixins")) {
                        if (value.isJsonPrimitive())
                            names.add(value.getAsString());
                        else if (matches(value.getAsJsonObject(), "environment", client))
                            names.add(value.getAsJsonObject().get("config").getAsString());
                    }
            }
        if (choice.kind() == mc.unity.adapter.Entry.Kind.NEOFORGE && neo != null)
            names.addAll(FmlDescriptor.transformFiles(jar, "mixins", "config"));
        for (var name : names)
            if (name.isBlank() || name.startsWith("/") || name.contains("..") || name.contains("\\") ||
                !name.endsWith(".json"))
                throw new IOException("Invalid Mixin config " + name);
        return List.copyOf(names);
    }
    static boolean matches(JsonObject obj, String key, boolean client) throws IOException {
        String side = obj.has(key) ? obj.get(key).getAsString() : "*";
        return switch (side) {
            case "*" -> true;
            case "client" -> client;
            case "server" -> !client;
            default -> throw new IOException("Invalid environment " + side);
        };
    }
}
