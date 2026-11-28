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
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            boolean material=name.equals("org.bukkit.Material");
            if (!material && !name.equals("org.bukkit.entity.EntityType") && !name.equals("com.destroystokyo.paper.MaterialSetTag"))return super.findClass(name);
            String path=name.replace('.','/')+".class";
            String pkg=name.substring(0,name.lastIndexOf('.'));
            var url = super.findResource(path);
            if (url == null) throw new ClassNotFoundException(name);
            try {
                var connection = (JarURLConnection)url.openConnection();
                connection.setUseCaches(false);
                try (var jar = connection.getJarFile()) {
                    var entry = jar.getJarEntry(path);
                    byte[] data;
                    try (var in = jar.getInputStream(entry)) { data = material?BukkitMaterialPatch.apply(in.readAllBytes(),this):name.equals("org.bukkit.entity.EntityType")?BukkitEntityTypePatch.apply(in.readAllBytes(),this):BukkitMaterialPatch.tagUniverse(in.readAllBytes(),this); }
                    var location = connection.getJarFileURL();
                    synchronized (this) {
                        if (getDefinedPackage(pkg) == null) definePackage(pkg,jar.getManifest(),location);
                    }
                    return defineClass(name,data,0,data.length,new java.security.CodeSource(location,entry.getCodeSigners()));
                }
            } catch (IOException e) { throw new ClassNotFoundException(name,e); }
        }
        private static boolean owned(String n) {
            return n.startsWith("org.bukkit.") || n.startsWith("io.papermc.paper.") ||
                n.startsWith("com.destroystokyo.paper.") || n.startsWith("net.kyori.") ||
                n.startsWith("org.eclipse.sisu.") || n.startsWith("org.json.simple.") || n.startsWith("org.apache.commons.lang.") || n.startsWith("org.apache.commons.codec.") ||
                n.startsWith("javax.inject.") || n.startsWith("org.apache.commons.logging.") || n.startsWith("org.eclipse.aether.") || n.startsWith("org.apache.maven.") || n.startsWith("org.codehaus.plexus.") ||
                n.startsWith("org.apache.http.") || n.startsWith("org.yaml.snakeyaml.") || n.startsWith("net.md_5.bungee.") || n.startsWith("mc.unity.plugins.");
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
        private static URL sessionResource(URL source) {
            if(source==null || !"jar".equals(source.getProtocol()))return source;
            try {
                return URL.of(source.toURI(),new URLStreamHandler() {
                    protected URLConnection openConnection(URL requested) throws IOException {
                        var conn=source.openConnection();conn.setUseCaches(false);return conn;
                    }
                });
            } catch(URISyntaxException|MalformedURLException e){throw new IllegalArgumentException(source.toString(),e);}
        }
        @Override public URL findResource(String name){return sessionResource(super.findResource(name));}
        @Override public Enumeration<URL> findResources(String name)throws IOException {
            var urls=Collections.list(super.findResources(name));
            urls.replaceAll(Domain::sessionResource);return Collections.enumeration(urls);
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
        Path primary=Files.createDirectories(Path.of(System.getProperty("mc.unity.pluginsDir",directory.toString()))).toRealPath();
        try (var current=Files.list(primary);var previous=Files.list(directory)) {
            var files=java.util.stream.Stream.concat(current,previous);
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
            session.host.initializePlugins(primary,directory.toRealPath());
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
        List<AutoCloseable> resources = new ArrayList<>();
        subscriptions.reversed().forEach(remove -> resources.add(remove::run));
        subscriptions.clear();
        if (host != null) resources.add(host);
        if (api != null) resources.add(api);
        resources.add(() -> {
            if (Files.exists(temporary)) try (var files = Files.list(temporary)) {
                List<AutoCloseable> deletes = new ArrayList<>();
                files.forEach(path -> deletes.add(() -> Files.deleteIfExists(path)));
                closeAll(deletes);
            }
        });
        resources.add(() -> Files.deleteIfExists(temporary));
        closeAll(resources);
    }
    private static void closeAll(List<AutoCloseable> resources) throws IOException {
        Throwable first = null;
        for (var resource : resources) try { resource.close(); }
        catch (Exception | LinkageError e) {
            if (first == null) first = e; else first.addSuppressed(e);
        }
        if (first instanceof IOException e) throw e;
        if (first instanceof RuntimeException e) throw e;
        if (first instanceof Error e) throw e;
        if (first != null) throw new IOException(first);
    }
}
