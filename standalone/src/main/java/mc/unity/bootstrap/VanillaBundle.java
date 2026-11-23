package mc.unity.bootstrap;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.jar.JarFile;

/** Materializes only the files listed by the pinned Mojang Java server bundle. */
public final class VanillaBundle {
    public static final String MC = "26.2";
    public static final String SHA256 = "cdacdfb25898de5e4b4b0e5ddcc2722f77067e46605709c2d886c000ebb63ec5";
    private static final URI DOWNLOAD = URI.create(
        "https://piston-data.mojang.com/v1/objects/823e2250d24b3ddac457a60c92a6a941943fcd6a/server.jar");
    public record Runtime(Path game, List<Path> libraries) {}

    public static Runtime prepare(Path cacheDir, Path supplied) throws Exception {
        Path cache = Files.createDirectories(cacheDir.toAbsolutePath().normalize());
        try (var lockFile = FileChannel.open(
                 cache.resolve("install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            var ignored = lockFile.lock()) {
            Path bundle =
                supplied == null ? cache.resolve("mojang-server-26.2.jar") : supplied.toAbsolutePath();
            if (supplied == null && !Files.isRegularFile(bundle)) {
                System.out.println("[Unity] Downloading Minecraft Java 26.2 from Mojang.");
                var http = HttpClient.newBuilder()
                               .connectTimeout(Duration.ofSeconds(30))
                               .followRedirects(HttpClient.Redirect.NORMAL)
                               .build();
                var request = HttpRequest.newBuilder(DOWNLOAD).timeout(Duration.ofMinutes(5)).GET().build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var in = response.body()) {
                    if (response.statusCode() != 200)
                        throw new IOException("Mojang HTTP " + response.statusCode());
                    writeVerified(bundle, in, SHA256);
                }
            }
            if (!hash(bundle).equals(SHA256))
                throw new IOException(
                    "Expected original Minecraft 26.2 server SHA-256 " + SHA256 + ": " + bundle);
            try (var archive = new JarFile(bundle.toFile(), true)) {
                var versions = extract(archive, cache, "versions");
                if (versions.size() != 1
                    || !versions.getFirst().getFileName().toString().equals("server-26.2.jar"))
                    throw new IOException("Unexpected Mojang versions.list");
                return new Runtime(versions.getFirst(), List.copyOf(extract(archive, cache, "libraries")));
            }
        }
    }

    private static List<Path> extract(JarFile jar, Path cache, String section) throws Exception {
        String prefix = "META-INF/" + section;
        Path root = cache.resolve(section);
        var files = new ArrayList<Path>();
        try (var reader = new BufferedReader(new InputStreamReader(
                 jar.getInputStream(jar.getJarEntry(prefix + ".list")), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank())
                    continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 3)
                    throw new IOException("Invalid " + section + " entry");
                Path target = root.resolve(fields[2]).normalize();
                if (!target.startsWith(root))
                    throw new IOException("Invalid bundle path: " + fields[2]);
                if (!Files.isRegularFile(target) || !hash(target).equals(fields[0])) {
                    var member = jar.getJarEntry(prefix + "/" + fields[2]);
                    if (member == null)
                        throw new IOException("Missing bundle member: " + fields[2]);
                    try (var input = jar.getInputStream(member)) {
                        writeVerified(target, input, fields[0]);
                    }
                }
                files.add(target);
            }
        }
        return files;
    }

    private static void writeVerified(Path target, InputStream input, String expected) throws Exception {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".unity-", ".part");
        try {
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            if (!expected.equals(hash(temp)))
                throw new IOException("Checksum mismatch: " + target.getFileName());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static String hash(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
