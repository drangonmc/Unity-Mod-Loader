import com.google.gson.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;
import mc.unity.adapter.common.NestedArchives;

/** Inspect two published archives with the production resolver, without launching their entrypoints. */
class PublishedNested {
    record Sample(String name, String hash, String member) {}
    static final List<Sample> SAMPLES = List.of(
        new Sample("ImmersiveOres-26.2-0.3.jar", "426984339b6d2a5828a2cf86685cdd4fdea965a6afc6d988c4f52b166c6708831bfb2f8cfbee78e64091da5ffc7be8cced923da1b7920a26d33167d2de48eae7", "META-INF/jars/PlayerAbilityLib-1.16.0.jar"),
        new Sample("BlockRunner-v26.2.0-mc26.2.x-NeoForge.jar", "38b6b62c4eb6d414941469e574f6c9d6be12a79503bfa097a0f7e6663a8ad7f35b551ca551b4eb61e3927a518ed4646447d6e052e09cf0248edd3429bc15456e", "META-INF/jarjar/multiloaderdataextensions-neoforge-26.2.0.jar"));
    static String hash(byte[] bytes, String algorithm) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }
    public static void main(String[] args) throws Exception {
        Path mods = Path.of(args[0]).toRealPath();
        var parents = new TreeMap<String, String>();
        var members = new TreeMap<String, String>();
        for (var sample : SAMPLES) {
            Path file = mods.resolve(sample.name());
            var digest = hash(Files.readAllBytes(file), "SHA-512");
            if (!digest.equals(sample.hash()))
                throw new AssertionError("Original JAR differs: " + sample.name());
            parents.put(sample.name(), digest);
            try (var jar = new JarFile(file.toFile()); var input = jar.getInputStream(jar.getJarEntry(sample.member()))) {
                members.put(sample.member(), hash(input.readAllBytes(), "SHA-256"));
            }
        }
        var results = new TreeMap<String, Set<String>>();
        for (boolean client : List.of(false, true)) {
            List<Path> extracted;
            try (var graph = NestedArchives.resolve(mods, client)) {
                extracted = graph.files().stream().filter(p -> !p.getParent().equals(mods)).toList();
                var hashes = new TreeSet<String>();
                for (var path : extracted)
                    hashes.add(hash(Files.readAllBytes(path), "SHA-256"));
                if (!hashes.equals(new TreeSet<>(members.values())))
                    throw new AssertionError("Unexpected selected nested members: " + hashes);
                results.put(client ? "client" : "server", hashes);
            }
            if (extracted.stream().anyMatch(Files::exists))
                throw new AssertionError("Owned nested files survived close");
        }
        var loader = Path.of(NestedArchives.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var report = Map.of("loaderSHA256", hash(Files.readAllBytes(loader), "SHA-256"),
            "parentsSHA512", parents, "membersSHA256", members, "resolved", results);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
