package mc.unity.bootstrap;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** The pinned Mojang descriptor supplies the platform rules and hashes. Gson is Mojang's own library. */
public final class ClientFiles {
    static Path checked(Path file, String sha1) throws Exception {
        var md = MessageDigest.getInstance("SHA-1");
        try (var in = new DigestInputStream(Files.newInputStream(file), md)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        if (!HexFormat.of().formatHex(md.digest()).equals(sha1))
            throw new IOException("Original client hash mismatch: " + file);
        return file;
    }
    @SuppressWarnings("unchecked")
    public static List<Path> paths(Path home) throws Exception {
        Path root = home.toAbsolutePath().normalize();
        var json = checked(root.resolve("versions/26.2/26.2.json"), "cf103b8fab4900562221bf7f23b72725dd9a914a");
        var gson = checked(root.resolve("libraries/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar"),
            "efc0e34ede4e3204eaefb84a00e55e8c86634382");
        Map<String, Object> data;
        try (var reader = new URLClassLoader(
                 new java.net.URL[] {gson.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var cls = reader.loadClass("com.google.gson.Gson");
            data = (Map<String, Object>) cls.getMethod("fromJson", String.class, Class.class)
                       .invoke(cls.getConstructor().newInstance(), Files.readString(json), Map.class);
        }
        var files = new ArrayList<Path>();
        var downloads = (Map<String, Map<String, Object>>) data.get("downloads");
        files.add(
            checked(root.resolve("versions/26.2/26.2.jar"), (String) downloads.get("client").get("sha1")));
        var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String name = os.contains("windows") ? "windows"
            : os.contains("mac")             ? "osx"
            : os.contains("linux")           ? "linux"
                                             : os;
        for (var lib : (List<Map<String, Object>>) data.get("libraries")) {
            var rules = (List<Map<String, Object>>) lib.get("rules");
            boolean allow = rules == null;
            if (rules != null)
                for (var rule : rules) {
                    var cond = (Map<String, String>) rule.get("os");
                    if (cond != null
                        && (!name.equals(cond.getOrDefault("name", name))
                            || !System.getProperty("os.arch").matches(cond.getOrDefault("arch", ".*"))
                            || !System.getProperty("os.version").matches(cond.getOrDefault("version", ".*"))))
                        continue;
                    allow = "allow".equals(rule.get("action"));
                }
            if (!allow)
                continue;
            var artifact = ((Map<String, Map<String, Object>>) lib.get("downloads")).get("artifact");
            files.add(checked(root.resolve("libraries").resolve((String) artifact.get("path")),
                (String) artifact.get("sha1")));
        }
        return List.copyOf(files);
    }
}
