package mc.unity.runtime;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import mc.unity.api.PluginHost;
import mc.unity.core.Events;

/** The dedicated server owns a fresh plugin API domain, including Bukkit's static HandlerLists. */
final class PluginSession implements AutoCloseable {
    private static final class Domain extends URLClassLoader {
        static {
            if (!registerAsParallelCapable())
                throw new ExceptionInInitializerError("Bukkit API classloader registration");
        }
        Domain(URL[] urls, ClassLoader parent) {
            super("Unity Bukkit 26.2", urls, parent);
        }
        private static boolean owned(String n) {
            return n.startsWith("org.bukkit.") || n.startsWith("io.papermc.paper.") ||
                n.startsWith("com.destroystokyo.paper.") || n.startsWith("net.kyori.") ||
                n.startsWith("net.md_5.bungee.") || n.startsWith("mc.unity.plugins.");
        }
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!owned(name))
                return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                var found = findLoadedClass(name);
                if (found == null)
                    found = findClass(name);
                if (resolve)
                    resolveClass(found);
                return found;
            }
        }
        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (name.startsWith("META-INF/services/") && owned(name.substring("META-INF/services/".length())))
                return findResources(name);
            return super.getResources(name);
        }
    }
    private final List<Runnable> subscriptions = new ArrayList<>();
    private final Path temporary;
    private URLClassLoader api;
    private PluginHost host;

    static PluginSession open(Path game) throws Exception {
        Path directory = Files.createDirectories(game.resolve("plugins"));
        try (var files = Files.list(directory)) {
            if (files.noneMatch(f
                                -> Files.isRegularFile(f) &&
                                       f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")))
                return null;
        }
        var session = new PluginSession(Files.createTempDirectory("unity-bukkit-"));
        try {
            var urls = new ArrayList<URL>();
            var loader = PluginSession.class.getClassLoader();
            try (var index = new BufferedReader(new InputStreamReader(
                     Objects.requireNonNull(loader.getResourceAsStream("META-INF/unity/bukkit/index")),
                     java.nio.charset.StandardCharsets.UTF_8))) {
                for (String row : index.lines().toList()) {
                    var fields = row.split("  ", 2);
                    if (fields.length != 2 || !fields[1].matches("[a-zA-Z0-9._+%-]+\\.jar"))
                        throw new IOException("Invalid embedded plugin library: " + row);
                    Path file = session.temporary.resolve(fields[1]);
                    try (var input = Objects.requireNonNull(
                             loader.getResourceAsStream("META-INF/unity/bukkit/" + fields[1]))) {
                        Files.copy(input, file);
                    }
                    if (!mc.unity.bootstrap.VanillaBundle.hash(file).equals(fields[0]))
                        throw new IOException("Changed embedded plugin library " + file.getFileName());
                    urls.add(file.toUri().toURL());
                }
            }
            session.api = new Domain(urls.toArray(URL[] ::new), loader);
            session.host = Class.forName("mc.unity.plugins.Host", true, session.api)
                               .asSubclass(PluginHost.class)
                               .getConstructor()
                               .newInstance();
            session.host.initialize(directory);
            session.subscribe();
            return session;
        } catch (Exception | Error failure) {
            try {
                session.close();
            } catch (Exception e) {
                failure.addSuppressed(e);
            }
            throw failure;
        }
    }
    private PluginSession(Path temporary) {
        this.temporary = temporary;
    }
    private void subscribe() {
        subscriptions.add(Events.COMMANDS.listen(e -> host.registerCommands(e.dispatcher())));
        subscriptions.add(
            Events.SERVER_ABOUT_TO_START.listen(server -> start(() -> host.aboutToStart(server))));
        subscriptions.add(Events.SERVER_STARTED.listen(server -> start(() -> host.serverStarted(server))));
        subscriptions.add(Events.SERVER_TICK_POST.listenScoped((server, time) -> host.tick(server)));
        subscriptions.add(Events.LEVEL_LOAD.listen(level -> host.worldEvent(level, true)));
        subscriptions.add(Events.LEVEL_UNLOAD.listen(level -> host.worldEvent(level, false)));
        subscriptions.add(Events.SERVER_STOPPING.listen(host::serverStopping));
    }
    private void start(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (Exception | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }
    PluginHost host() {
        return host;
    }
    public void close() throws IOException {
        subscriptions.reversed().forEach(Runnable::run);
        subscriptions.clear();
        try {
            try {
                if (host != null)
                    host.close();
            } finally {
                if (api != null)
                    api.close();
            }
        } finally {
            if (Files.exists(temporary))
                try (var files = Files.list(temporary)) {
                    for (var file : files.toList())
                        Files.delete(file);
                }
            Files.deleteIfExists(temporary);
        }
    }
}
