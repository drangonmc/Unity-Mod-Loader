package mc.unity.loader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import mc.unity.api.UnityMod;

/** A single shared native-extension class space, with deterministic dependency order. */
public final class NativeLoader implements AutoCloseable {
    private record Descriptor(String id, String entry, List<String> requires, String side, Path jar) {}
    public record Loaded(String id, UnityMod mod) {}
    private final URLClassLoader classes;
    private final List<Loaded> loaded;
    private final List<Loaded> serverCallbacks;

    private NativeLoader(URLClassLoader classes, List<Loaded> loaded, List<Loaded> serverCallbacks) {
        this.classes = classes;
        this.loaded = List.copyOf(loaded);
        this.serverCallbacks=List.copyOf(serverCallbacks);
    }

    public static NativeLoader open(Path game) throws Exception {
        return open(game,"server");
    }

    public static NativeLoader open(Path game,String side) throws Exception {
        if (!Set.of("client","server").contains(side)) throw new IllegalArgumentException("Invalid Unity side: "+side);
        var directory = Files.createDirectories(game.resolve("unity-mods"));
        var descriptors = new TreeMap<String, Descriptor>();
        try (var files = Files.list(directory)) {
            for (Path jar : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted().toList()) {
                var descriptor = read(jar);
                if (!descriptor.side.equals("both") && !descriptor.side.equals(side)) continue;
                if (descriptors.putIfAbsent(descriptor.id, descriptor) != null)
                    throw new IOException("Duplicate Unity id " + descriptor.id + " in " + jar);
            }
        }
        var order = new LinkedHashMap<String, Descriptor>();
        for (var descriptor : descriptors.values()) visit(descriptor, descriptors, new LinkedHashSet<>(), order);
        var urls = new ArrayList<URL>();
        for (var descriptor : order.values()) urls.add(descriptor.jar.toUri().toURL());
        var classes = new URLClassLoader("unity-native", urls.toArray(URL[]::new), UnityMod.class.getClassLoader());
        try {
            var loaded = new ArrayList<Loaded>();
            for (var descriptor : order.values()) {
                var type = Class.forName(descriptor.entry, false, classes).asSubclass(UnityMod.class);
                var origin = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (!Files.isSameFile(origin, descriptor.jar)) throw new IOException("Entrypoint belongs to a different jar: " + descriptor.entry);
                var mod = type.getConstructor().newInstance();
                mod.initialize(Files.createDirectories(game.resolve("unity-data").resolve(descriptor.id)));
                loaded.add(new Loaded(descriptor.id, mod));
            }
            return new NativeLoader(classes, loaded, loaded.stream().filter(mod -> !descriptors.get(mod.id()).side.equals("client")).toList());
        } catch (Exception | LinkageError failure) {
            classes.close();
            throw failure;
        }
    }

    private static Descriptor read(Path jar) throws IOException {
        var props = new Properties();
        try (var archive = new JarFile(jar.toFile())) {
            var metadata = archive.getJarEntry("META-INF/unity.properties");
            if (metadata == null) throw new IOException("Missing META-INF/unity.properties in " + jar);
            try (var input = archive.getInputStream(metadata)) { props.load(input); }
        }
        var id = props.getProperty("id", "").trim();
        var entry = props.getProperty("entrypoint", "").trim();
        if (!id.matches("[a-z][a-z0-9_-]{0,63}") || entry.isEmpty() || !"1".equals(props.getProperty("api")))
            throw new IOException("Invalid Unity id, entrypoint or api in " + jar);
        var requires = Arrays.stream(props.getProperty("requires", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        var side=props.getProperty("environment","both").trim();
        if (!Set.of("client","server","both").contains(side)) throw new IOException("Invalid Unity environment in "+jar);
        return new Descriptor(id, entry, requires, side, jar);
    }

    private static void visit(Descriptor descriptor, Map<String, Descriptor> available, Set<String> path,
                              Map<String, Descriptor> order) throws IOException {
        if (order.containsKey(descriptor.id)) return;
        if (!path.add(descriptor.id)) throw new IOException("Unity dependency cycle " + path + " -> " + descriptor.id);
        for (String id : descriptor.requires) {
            var dependency = available.get(id);
            if (dependency == null) throw new IOException(descriptor.id + " requires missing Unity mod " + id);
            visit(dependency, available, path, order);
        }
        path.remove(descriptor.id);
        order.put(descriptor.id, descriptor);
    }

    public List<Loaded> serverCallbacks() { return serverCallbacks; }
    public List<Loaded> mods() { return loaded; }
    @Override public void close() throws IOException { classes.close(); }
}
