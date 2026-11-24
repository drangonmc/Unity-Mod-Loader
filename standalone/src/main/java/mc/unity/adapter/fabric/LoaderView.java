package mc.unity.adapter.fabric;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.api.EnvType;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import mc.unity.adapter.Entry;
import mc.unity.adapter.common.FmlDescriptor;
public final class LoaderView implements FabricLoader, AutoCloseable {
    public static final LoaderView INSTANCE = new LoaderView();
    private Map<String, View> mods = Map.of();
    private Path game;
    private boolean upstream;
    public static boolean upstream() {
        return INSTANCE.upstream;
    }
    private EnvType side;
    private record Number(String getFriendlyString) implements Version {
        public int compareTo(Version o) {
            try {
                return FmlDescriptor.compare(getFriendlyString, o.getFriendlyString());
            } catch (IOException e) {
                return getFriendlyString.compareTo(o.getFriendlyString());
            }
        }
    }
    private static final class View implements ModContainer, ModMetadata {
        final Entry entry;
        final String name;
        FileSystem fs;
        com.google.gson.JsonObject metadata = new com.google.gson.JsonObject();
        View parent;
        final List<ModContainer> children = new ArrayList<>();
        final Map<String, Object> instances = new HashMap<>();
        public Optional<ModContainer> getContainingMod() {
            return Optional.ofNullable(parent);
        }
        public Collection<ModContainer> getContainedMods() {
            return List.copyOf(children);
        }
        public String getType() {
            return entry.id().equals("fabricloader") || entry.id().equals("minecraft") ||
                    entry.id().equals("java")
                ? "builtin"
                : "fabric";
        }
        public boolean containsCustomValue(String key) {
            return metadata.has("custom") && metadata.getAsJsonObject("custom").has(key);
        }
        public net.fabricmc.loader.api.metadata.CustomValue getCustomValue(String key) {
            return containsCustomValue(key) ? MetadataValue.of(metadata.getAsJsonObject("custom").get(key))
                                            : null;
        }
        public Optional<String> getIconPath(int size) {
            var icon = metadata.get("icon");
            if (icon == null)
                return Optional.empty();
            if (icon.isJsonPrimitive())
                return Optional.of(icon.getAsString());
            var sizes = new TreeMap<Integer, String>();
            icon.getAsJsonObject().entrySet().forEach(
                e -> sizes.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()));
            if (sizes.isEmpty())
                return Optional.empty();
            var result = sizes.ceilingEntry(size);
            return Optional.of((result == null ? sizes.lastEntry() : result).getValue());
        }
        View(Entry entry, String title) {
            this.entry = entry;
            this.name = title;
        }
        public ModMetadata getMetadata() {
            return this;
        }
        public String getId() {
            return entry.id();
        }
        public String getName() {
            return name;
        }
        public Version getVersion() {
            return new Number(entry.version());
        }
        public synchronized List<Path> getRootPaths() {
            try {
                if (Files.isDirectory(entry.file()))
                    return List.of(entry.file());
                if (fs == null)
                    fs = FileSystems.newFileSystem(entry.file());
                return List.of(fs.getPath("/"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        synchronized void close() throws IOException {
            if (fs != null)
                fs.close();
        }
    }
    public static void bootstrap(Path game, boolean client, Collection<Entry> entries) throws Exception {
        INSTANCE.bind(game, client, entries);
    }
    public void bind(Path game, boolean client, Collection<Entry> entries) throws Exception {
        this.game = game;
        try {
            upstream = (boolean)getClass()
                           .getClassLoader()
                           .getClass()
                           .getMethod("fullFabricApi")
                           .invoke(getClass().getClassLoader());
        } catch (NoSuchMethodException ordinaryClasspath) {
            upstream = false;
        }
        side = client ? EnvType.CLIENT : EnvType.SERVER;
        var map = new LinkedHashMap<String, View>();
        for (var e : entries) {
            String name = e.id();
            com.google.gson.JsonObject metadata = null;
            if (e.kind() == Entry.Kind.FABRIC)
                try (var jar = new java.util.jar.JarFile(e.file().toFile(), true)) {
                    var meta = mc.unity.adapter.common.NestedArchives.json(jar, "fabric.mod.json");
                    metadata = meta;
                    if (meta != null && meta.has("name"))
                        name = meta.get("name").getAsString();
                }
            var early = mods.get(e.id());
            var v = early != null && early.entry.file().equals(e.file()) ? early : new View(e, name);
            if (metadata != null)
                v.metadata = metadata;
            v.children.clear();
            v.parent = null;
            map.put(e.id(), v);
        }
        if (upstream) {
            var javaEntry = new Entry("java", System.getProperty("java.version"), List.of(),
                                      Entry.Kind.FABRIC, Path.of(System.getProperty("java.home")), List.of());
            map.put("java", mods.getOrDefault("java", new View(javaEntry, "Java")));
        }
        Path own = Path.of(LoaderView.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (String id :
             List.of("fabricloader", "fabric-api", "fabric-lifecycle-events-v1", "fabric-entity-events-v1",
                     "fabric-api-base", "fabric-resource-loader-v1", "fabric-data-attachment-api-v1",
                     "fabric-object-builder-api-v1", "fabric-networking-api-v1", "fabric-command-api-v2",
                     "fabric-creative-tab-api-v1", "fabric-registry-sync-v0", "fabric-rendering-v1",
                     "fabric-model-loading-api-v1", "fabric-events-interaction-v0")) {
            if (upstream && !id.equals("fabricloader"))
                continue;
            var e = new Entry(id, FabricAdapter.fixed(id), List.of(), Entry.Kind.FABRIC, own, List.of());
            map.putIfAbsent(id, mods.getOrDefault(id, new View(e, id + " (Unity compatibility)")));
        }
        try {
            var loader = getClass().getClassLoader();
            var vanilla = (Path)loader.getClass().getMethod("vanillaPath").invoke(loader);
            var mc = new Entry("minecraft", "26.2", List.of(), Entry.Kind.FABRIC, vanilla, List.of());
            map.put("minecraft", mods.getOrDefault("minecraft", new View(mc, "Minecraft")));
            @SuppressWarnings("unchecked")
            var parents = (Map<Path, Path>)loader.getClass().getMethod("containers").invoke(loader);
            var owners = new HashMap<Path, View>();
            map.values().forEach(v -> owners.putIfAbsent(v.entry.file(), v));
            for (var v : map.values()) {
                var file = parents.get(v.entry.file());
                var parent = file == null ? null : owners.get(file);
                if (parent != null) {
                    v.parent = parent;
                    parent.children.add(v);
                }
            }
        } catch (NoSuchMethodException ordinaryClasspath) {}
        mods = Collections.unmodifiableMap(map);
    }
    public MappingResolver getMappingResolver() {
        return Names.INSTANCE;
    }
    public <T> List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>>
    getEntrypointContainers(String key, Class<T> type) {
        var result = new ArrayList<net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>>();
        for (var v : mods.values()) {
            var points = v.metadata.getAsJsonObject("entrypoints");
            if (points == null || !points.has(key))
                continue;
            for (var row : points.getAsJsonArray(key)) {
                var definition = row.isJsonPrimitive() ? row.getAsString()
                                                       : row.getAsJsonObject().get("value").getAsString();
                if (!row.isJsonPrimitive() && row.getAsJsonObject().has("adapter") &&
                    !row.getAsJsonObject().get("adapter").getAsString().equals("default"))
                    throw new IllegalArgumentException("Unsupported entrypoint adapter for " + definition);
                result.add(new net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>() {
                    public ModContainer getProvider() {
                        return v;
                    }
                    public String getDefinition() {
                        return definition;
                    }
                    public T getEntrypoint() {
                        synchronized (v.instances) {
                            return type.cast(v.instances.computeIfAbsent(
                                key + " " + definition + " " + type.getName(), unused -> {
                                    try {
                                        return resolve(definition, type);
                                    } catch (ReflectiveOperationException ex) {
                                        throw new IllegalStateException(
                                            "Entrypoint " + v.getId() + " " + definition, ex);
                                    }
                                }));
                        }
                    }
                });
            }
        }
        return List.copyOf(result);
    }
    public <T> T entrypoint(String id, String key, String definition, Class<T> type) {
        for (var container : getEntrypointContainers(key, type))
            if (container.getProvider().getMetadata().getId().equals(id) &&
                container.getDefinition().equals(definition))
                return container.getEntrypoint();
        throw new IllegalArgumentException("Unknown " + key + " entrypoint " + id + " " + definition);
    }
    private static <T> Object resolve(String definition, Class<T> type) throws ReflectiveOperationException {
        var parts = definition.split("::", 2);
        var cls = Class.forName(parts[0], true, LoaderView.class.getClassLoader());
        if (parts.length == 1)
            return type.cast(cls.getConstructor().newInstance());
        try {
            var field = cls.getField(parts[1]);
            return type.cast(field.get(null));
        } catch (NoSuchFieldException absent) {
            var sam = Arrays.stream(type.getMethods())
                          .filter(m -> java.lang.reflect.Modifier.isAbstract(m.getModifiers()))
                          .toList();
            if (sam.size() != 1)
                throw new IllegalArgumentException("Expected a functional entrypoint " + definition);
            var method = cls.getMethod(parts[1], sam.getFirst().getParameterTypes());
            var handle = java.lang.invoke.MethodHandles.publicLookup().unreflect(method);
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                handle = handle.bindTo(cls.getConstructor().newInstance());
            return java.lang.invoke.MethodHandleProxies.asInterfaceInstance(type, handle);
        }
    }
    public void close() throws IOException {
        try {
            IOException failure = null;
            for (var view : mods.values()) {
                try {
                    view.close();
                } catch (IOException e) {
                    if (failure == null)
                        failure = e;
                    else
                        failure.addSuppressed(e);
                }
            }
            if (failure != null)
                throw failure;
        } finally {
            mods = Map.of();
            upstream = false;
            game = null;
        }
    }
    public EnvType getEnvironmentType() {
        return side;
    }
    public boolean isDevelopmentEnvironment() {
        return false;
    }
    public Path getGameDir() {
        return game;
    }
    public Path getConfigDir() {
        try {
            return Files.createDirectories(game.resolve("config"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public boolean isModLoaded(String id) {
        return mods.containsKey(id) || mc.unity.runtime.Platforms.present(id);
    }
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(mods.get(id));
    }
    public Collection<ModContainer> getAllMods() {
        return List.copyOf(mods.values());
    }
}
