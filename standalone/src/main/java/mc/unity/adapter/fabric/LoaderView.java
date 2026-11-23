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
    public static void bootstrap(Path game,boolean client,Collection<Entry> entries) throws Exception {
        INSTANCE.bind(game,client,entries);
    }
    public void bind(Path game, boolean client, Collection<Entry> entries) throws Exception {
        this.game = game;
        side = client ? EnvType.CLIENT : EnvType.SERVER;
        var map = new TreeMap<String, View>();
        for (var e : entries) {
            String name = e.id();
            if (e.kind() == Entry.Kind.FABRIC)
                try (var jar = new java.util.jar.JarFile(e.file().toFile(), true)) {
                    var meta = mc.unity.adapter.common.NestedArchives.json(jar, "fabric.mod.json");
                    if (meta != null && meta.has("name"))
                        name = meta.get("name").getAsString();
                }
            var early=mods.get(e.id());
            map.put(e.id(), early!=null && early.entry.file().equals(e.file()) ? early : new View(e,name));
        }
        Path own = Path.of(LoaderView.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (String id :
            List.of("fabricloader", "fabric-api", "fabric-lifecycle-events-v1", "fabric-entity-events-v1",
                "fabric-api-base", "fabric-resource-loader-v1","fabric-data-attachment-api-v1", "fabric-object-builder-api-v1","fabric-networking-api-v1","fabric-command-api-v2", "fabric-creative-tab-api-v1","fabric-registry-sync-v0","fabric-rendering-v1","fabric-model-loading-api-v1","fabric-events-interaction-v0")) {
            var e = new Entry(id, FabricAdapter.fixed(id), List.of(), Entry.Kind.FABRIC, own, List.of());
            map.putIfAbsent(id,mods.getOrDefault(id,new View(e,id + " (Unity compatibility)")));
        }
        mods = Map.copyOf(map);
    }
    public void close() throws IOException {
        try {
            IOException failure = null;
            for (var view : mods.values()) {
                try {
                    view.close();
                } catch (IOException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            if (failure != null) throw failure;
        } finally {
            mods = Map.of();
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
