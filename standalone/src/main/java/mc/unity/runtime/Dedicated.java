package mc.unity.runtime;

import java.nio.file.*;
import java.util.Properties;

public final class Dedicated {
    private static Thread serverThread;
    public static void startedThread(net.minecraft.server.MinecraftServer server) {
        serverThread = server.getRunningThread();
    }
    public static void launch(String[] args) throws Exception {
        Path instance = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(instance.resolve(".unity-loader.sha256")))
            throw new IllegalStateException(
                "This directory belongs to the compatibility server. Choose a separate standalone instance.");
        if (!Files.exists(instance.resolve("server.properties"))) {
            try (var input = Dedicated.class.getResourceAsStream("/META-INF/unity/default-server.properties")) {
                Files.copy(java.util.Objects.requireNonNull(input), instance.resolve("server.properties"));
            } catch (FileAlreadyExistsException ignored) { /* a concurrent starter created it */
            }
        }
        var eula = new Properties();
        if (Files.isRegularFile(instance.resolve("eula.txt")))
            try (var reader = Files.newBufferedReader(instance.resolve("eula.txt"))) {
                eula.load(reader);
            }
        if (Boolean.parseBoolean(eula.getProperty("eula"))
            && java.util.Collections.disjoint(
                java.util.List.of(args), java.util.List.of("--help", "--initSettings", "--version"))) {
            ServerHooks.discover(instance);
        }
        try {
            net.minecraft.server.Main.main(args);
        } finally {
            // Vanilla main hands the lifetime to the server thread. It owns the final save and close.
            boolean interrupted = false;
            while (serverThread != null && serverThread.isAlive()) {
                try { serverThread.join(); }
                catch (InterruptedException retry) { interrupted = true; }
            }
            serverThread = null;
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
