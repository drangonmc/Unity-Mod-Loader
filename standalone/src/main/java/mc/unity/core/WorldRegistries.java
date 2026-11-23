package mc.unity.core;

import java.util.*;
import java.util.function.BiConsumer;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import net.minecraft.resources.RegistryDataLoader.RegistryData;

/** The game owns every registry instance. Only codec declarations survive a world change. */
public final class WorldRegistries {
    private static final class View extends AbstractList<RegistryData<?>> implements RandomAccess {
        final List<RegistryData<?>> vanilla;
        volatile List<RegistryData<?>> values;
        View(List<RegistryData<?>> vanilla) {
            this.vanilla = List.copyOf(vanilla);
            values = this.vanilla;
        }
        public int size() { return values.size(); }
        public RegistryData<?> get(int i) { return values.get(i); }
        public Iterator<RegistryData<?>> iterator() { return values.iterator(); }
        void append(RegistryData<?> entry) {
            var copy = new ArrayList<>(values);
            copy.add(entry);
            values = List.copyOf(copy);
        }
    }
    private static final Map<ResourceKey<?>, String> sourceHashes = new HashMap<>();
    private static View world, network;
    private static Set<ResourceKey<?>> vanillaNetwork = Set.of();
    private static volatile Set<ResourceKey<?>> synced = Set.of(), optional = Set.of();
    public static List<RegistryData<?>> wrap(List<RegistryData<?>> vanilla, boolean forNetwork) {
        var view = new View(vanilla);
        if (forNetwork) {
            network = view;
            var keys = new HashSet<ResourceKey<?>>();
            for (var r : vanilla)
                keys.add(r.key());
            vanillaNetwork = Set.copyOf(keys);
        } else
            world = view;
        return view;
    }
    public static synchronized<T> void add(ResourceKey<? extends Registry<T>> key, Codec<T> file, Codec<T> wire,
                                           boolean skipEmpty) {
        Content.checkOpen();
        Objects.requireNonNull(key);
        Objects.requireNonNull(file);
        if (RegistryDataLoader.WORLDGEN_REGISTRIES.stream().anyMatch(r -> r.key().equals(key)) ||
            RegistryDataLoader.DIMENSION_REGISTRIES.stream().anyMatch(r -> r.key().equals(key)) ||
            net.minecraft.core.registries.BuiltInRegistries.REGISTRY.containsKey(key.identifier()))
            throw new IllegalArgumentException("Duplicate data-driven registry " + key.identifier());
        var source = wire == null ? null : Content.declaringArchiveHash();
        world.append(new RegistryData<>(key, file, RegistryValidator.none()));
        if (wire != null) {
            sourceHashes.put(key, source);
            network.append(new RegistryData<>(key, wire, RegistryValidator.none()));
            var keys = new HashSet<>(synced);
            keys.add(key);
            synced = Set.copyOf(keys);
            if (skipEmpty) {
                var opts = new HashSet<>(optional);
                opts.add(key);
                optional = Set.copyOf(opts);
            }
        }
    }
    public static String directory(Identifier id) {
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.getNamespace() + "/" + id.getPath();
    }
    public static boolean networkable(boolean vanilla, ResourceKey<?> key) {
        return network == null ? vanilla : vanillaNetwork.contains(key) || synced.contains(key);
    }
    public static List<String> networkKeys() {
        return synced.stream()
            .filter(k -> !optional.contains(k))
            .map(k -> k.identifier() + " " + sourceHashes.get(k))
            .sorted()
            .toList();
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void send(BiConsumer receiver, Object key, Object entries) {
        if (!optional.contains(key) || !((List<?>)entries).isEmpty())
            receiver.accept(key, entries);
    }
    public static synchronized void reset() {
        if (world != null)
            world.values = world.vanilla;
        if (network != null)
            network.values = network.vanilla;
        synced = Set.of();
        optional = Set.of();
        sourceHashes.clear();
    }
}
