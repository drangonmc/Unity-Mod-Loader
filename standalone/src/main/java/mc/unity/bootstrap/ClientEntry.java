package mc.unity.bootstrap;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Entry used by authenticated launcher profiles and by the isolated loopback QA window. */
public final class ClientEntry {
    private static final String PROFILE = "unity-standalone-" + Main.VERSION + "-26.2";
    public static void main(String[] args) throws Throwable {
        launch(Path.of(Objects.requireNonNull(
                   System.getProperty("mc.unity.clientHome"), "Missing vanilla launcher root")),
            args);
    }
    static void launch(Path root, String[] args) throws Throwable {
        root = root.toAbsolutePath().normalize();
        var urls = new ArrayList<URL>();
        urls.add(ClientEntry.class.getProtectionDomain().getCodeSource().getLocation());
        var files = ClientFiles.paths(root);
        for (var file : files) urls.add(file.toUri().toURL());
        Path client = files.getFirst();
        var loader = new GameLoader(urls.toArray(URL[] ::new), client);
        var previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try (loader) {
            GamePatches.installClient(loader, client);
            Path game = Path.of(".");
            for (int i = 0; i < args.length - 1; i++)
                if (args[i].equals("--gameDir"))
                    game = Path.of(args[++i]);
            loader.mixins(game.toAbsolutePath().normalize(), true);
            System.out.println("[Unity] " + Main.VERSION + " standalone client | Minecraft 26.2");
            try {
                Class.forName("mc.unity.runtime.ClientHooks", true, loader)
                    .getMethod("initialize", Path.class)
                    .invoke(null, game.toAbsolutePath().normalize());
                Class.forName("net.minecraft.client.main.Main", true, loader)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } finally {
                Class.forName("mc.unity.runtime.ClientHooks", true, loader).getMethod("finish").invoke(null);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
    static void installProfile(Path root) throws Exception {
        root = root.toAbsolutePath().normalize();
        Path source = Path.of(ClientEntry.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String file = "unity-loader-" + Main.VERSION + ".jar";
        String rel = "mc/unity/unity-loader/" + Main.VERSION + "/" + file;
        Path lib = root.resolve("libraries").resolve(rel);
        Files.createDirectories(lib.getParent());
        if (!Files.exists(lib) || !VanillaBundle.hash(source).equals(VanillaBundle.hash(lib))) {
            Path temp = Files.createTempFile(lib.getParent(), ".unity-", ".part");
            try {
                Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, lib, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        var sha = MessageDigest.getInstance("SHA-1");
        try (var in = new DigestInputStream(Files.newInputStream(lib), sha)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        String home = root.toString().replace("\\", "/").replace("\"", "\\\"");
        String json = """
                {"id":"%s","inheritsFrom":"26.2","type":"release","mainClass":"mc.unity.bootstrap.ClientEntry",
                 "time":"2026-11-22T00:00:00Z","releaseTime":"2026-11-22T00:00:00Z",
                 "arguments":{"jvm":["-Dmc.unity.clientHome=%s"]},
                 "libraries":[{"name":"mc.unity:unity-loader:%s","downloads":{"artifact":{"path":"%s","size":%d,"sha1":"%s","url":""}}}]}
                """.formatted(
            PROFILE, home, Main.VERSION, rel, Files.size(lib), HexFormat.of().formatHex(sha.digest()));
        Path dir = Files.createDirectories(root.resolve("versions").resolve(PROFILE));
        Path temp = Files.createTempFile(dir, ".unity-", ".part");
        try {
            Files.writeString(temp, json);
            Files.move(temp, dir.resolve(PROFILE + ".json"), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        System.out.println(
            "Installed launcher version " + PROFILE + ". Select it in a separate game directory.");
    }
}
