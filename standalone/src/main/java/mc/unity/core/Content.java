package mc.unity.core;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

public final class Content {
    public static final PackSource MOD_SOURCE = PackSource.create(PackSource.NO_DECORATION, true);
    private record Group(String owner, ResourceKey<?> type, Consumer<Registry<?>> register) {}
    private static final List<Group> groups = new ArrayList<>();
    private static final Map<String, Path> sources = new TreeMap<>();
    private static final Map<String, Path> packs = new TreeMap<>();
    private static final Set<String> localResourcePacks = new HashSet<>();
    private static Map<Path, String> dependencyStamps = Map.of();
    public static void dependencies(Map<Path, String> values) {
        dependencyStamps = Map.copyOf(values);
    }
    private static boolean frozen;
    private static volatile String digest = "";
    private static int nextColumn = 7;
    private static final Map<CreativeModeTab.Builder, List<ResourceKey<CreativeModeTab>>> requestedOrder =
        new IdentityHashMap<>();
    private static final Map<CreativeModeTab, List<ResourceKey<CreativeModeTab>>> tabOrder =
        new IdentityHashMap<>();
    public static CreativeModeTab.Builder tabBuilder() {
        return CreativeModeTab.builder(CreativeModeTab.Row.TOP, nextColumn++);
    }
    public static CreativeModeTab.Builder tabsBefore(CreativeModeTab.Builder b,
                                                     ResourceKey<CreativeModeTab>[] refs) {
        requestedOrder.put(b, List.copyOf(Arrays.asList(refs)));
        return b;
    }
    public static void built(CreativeModeTab tab, CreativeModeTab.Builder b) {
        var order = requestedOrder.remove(b);
        if (order != null)
            tabOrder.put(tab, order);
    }
    public static java.util.stream.Stream<CreativeModeTab>
    order(java.util.stream.Stream<CreativeModeTab> vanilla) {
        if (tabOrder.isEmpty())
            return vanilla;
        var todo = new ArrayList<>(vanilla.toList());
        var done = new ArrayList<CreativeModeTab>();
        while (!todo.isEmpty()) {
            var next =
                todo.stream()
                    .filter(
                        tab
                        -> tabOrder.getOrDefault(tab, List.of())
                               .stream()
                               .noneMatch(
                                   key -> todo.contains(BuiltInRegistries.CREATIVE_MODE_TAB.getValue(key))))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cyclic creative tab order"));
            todo.remove(next);
            done.add(next);
        }
        return done.stream();
    }
    private static AutoCloseable owner;
    public static void own(AutoCloseable session) {
        if (owner != null)
            throw new IllegalStateException("A mod session is already open");
        owner = session;
    }
    private static void abort(Throwable failure) {
        var session = owner;
        try {
            if (session != null)
                session.close();
        } catch (Exception e) {
            failure.addSuppressed(e);
        } finally {
            reset();
        }
    }
    private static List<Registry<?>> registries() {
        var base = List.<Registry<?>>of(
            BuiltInRegistries.SOUND_EVENT, BuiltInRegistries.DATA_COMPONENT_TYPE, BuiltInRegistries.BLOCK,
            BuiltInRegistries.ENTITY_TYPE, BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK_ENTITY_TYPE,
            BuiltInRegistries.MENU, BuiltInRegistries.RECIPE_SERIALIZER, BuiltInRegistries.CREATIVE_MODE_TAB);
        if (ExtraRegistries.all().isEmpty())
            return base;
        var all = new ArrayList<>(base);
        all.addAll(ExtraRegistries.all());
        return all;
    }
    public static int registrySize() {
        return registries().stream().mapToInt(Registry::size).sum();
    }
    public static void localResources(String id) {
        localResourcePacks.add(id);
    }
    public static void include(String id) {
        localResourcePacks.remove(id);
        packs.put(id, Objects.requireNonNull(sources.get(id), id));
    }
    public static void checkOpen() {
        if (frozen)
            throw new IllegalStateException("Registries are frozen");
    }
    public static void attach(String owner, ResourceKey<?> type, Consumer<Registry<?>> reg) {
        checkOpen();
        if (registries().stream().noneMatch(registry -> registry.key().equals(type)))
            throw new IllegalArgumentException("Unsupported registry " + type);
        if (groups.stream().anyMatch(g -> g.register == reg))
            throw new IllegalStateException("Duplicate register " + owner);
        localResourcePacks.remove(owner);
        packs.put(owner, Objects.requireNonNull(sources.get(owner), "Undiscovered owner " + owner));
        groups.add(new Group(owner, type, reg));
    }
    public static void source(String id, Path path, boolean hasResources) {
        sources.put(id, path);
        if (hasResources)
            packs.put(id, path);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        try {
            net.minecraft.world.level.block.UnityBlockExperience.verifyRuntime();
            net.minecraft.world.item.UnityTabContents.verifyRuntime();
            Events.INIT.post(null);
            for (Registry registry : registries())
                for (var group : groups)
                    if (registry.key().equals(group.type))
                        group.register.accept(registry);
            ExtraRegistries.freeze();
            frozen = true;
            Events.PLAY_REGISTRATION.post(null);
        } catch (Exception | LinkageError e) {
            abort(e);
            throw new IllegalStateException("Content registration failed", e);
        }
    }
    public static void finish() {
        try {
            EntityAttributes.finish();
        } catch (RuntimeException | LinkageError failure) {
            abort(failure);
            throw failure;
        }
        // Vanilla Items.registerItem also binds each BlockItem to Block.asItem(). Deferred registers bypass
        // it.
        for (Item item : BuiltInRegistries.ITEM)
            if (item instanceof BlockItem block && !Item.BY_BLOCK.containsKey(block.getBlock()))
                block.registerBlocks(Item.BY_BLOCK, item);

        if (localResourcePacks.containsAll(packs.keySet()) && WorldRegistries.networkKeys().isEmpty() &&
            ConfigurationNetwork.manifest().isEmpty() && PlayNetwork.manifest().isEmpty())
            return;
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            line(hash, "unity:content/8 " + mc.unity.bootstrap.VanillaBundle.hash(coreArchive()));
            for (var entry : packs.entrySet()) {
                if (localResourcePacks.contains(entry.getKey()))
                    continue;
                line(hash, entry.getKey() + " " + mc.unity.bootstrap.VanillaBundle.hash(entry.getValue()) +
                               " " + dependencyStamps.getOrDefault(entry.getValue(), ""));
            }
            for (Block block : BuiltInRegistries.BLOCK) {
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    if (Block.BLOCK_STATE_REGISTRY.getId(state) < 0) {
                        Block.BLOCK_STATE_REGISTRY.add(state);
                        state.initCache();
                    }
                    line(hash, "S " + Block.getId(state) + " " + state);
                }
            }
            for (Registry registry : registries())
                for (Object value : registry)
                    line(hash, registry.key().identifier() + " " + registry.getId(value) + " " +
                                   registry.getKey(value));
            for (var key : WorldRegistries.networkKeys())
                line(hash, "world registry " + key);
            for (String channel : ConfigurationNetwork.manifest())
                line(hash, "configuration " + channel);
            for (String channel : PlayNetwork.manifest())
                line(hash, "play " + channel);
            digest = HexFormat.of().formatHex(hash.digest());
            System.out.println("[Unity] Registered content " + packs.keySet() + " registry " + digest);
        } catch (Exception | LinkageError e) {
            abort(e);
            throw new IllegalStateException("Registry digest failed", e);
        }
    }
    private static void line(MessageDigest h, String s) {
        h.update((s + "\n").getBytes(StandardCharsets.UTF_8));
    }
    public static String digest() {
        return digest;
    }
    static String declaringArchiveHash() {
        var owners = Set.copyOf(sources.values());
        var origin = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                         .walk(frames
                               -> frames.map(f -> f.getDeclaringClass().getProtectionDomain().getCodeSource())
                                      .filter(Objects::nonNull)
                                      .map(cs -> cs.getLocation())
                                      .filter(url -> url.getProtocol().equals("file"))
                                      .map(url -> {
                                          try {
                                              return Path.of(url.toURI());
                                          } catch (java.net.URISyntaxException e) {
                                              throw new IllegalStateException(e);
                                          }
                                      })
                                      .filter(owners::contains)
                                      .findFirst())
                         .orElse(coreArchive());
        try {
            return mc.unity.bootstrap.VanillaBundle.hash(origin);
        } catch (Exception e) {
            throw new IllegalStateException("Registry declaration source " + origin, e);
        }
    }
    private static Path coreArchive() {
        try {
            return Path.of(Content.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
    private static final Set<Path> dataArchives = new HashSet<>();
    private static final Set<Path> assetArchives = new HashSet<>();
    public static void dataArchive(String id, Path file, boolean assets) {
        if (assets)
            assetArchives.add(file);
        source(id, file, true);
        localResources(id);
        dataArchives.add(file);
    }
    public static void packs(Map<String, Pack> target, Set<RepositorySource> sources) {
        var type = sources.stream().anyMatch(ServerPacksSource.class ::isInstance)
                       ? PackType.SERVER_DATA
                       : PackType.CLIENT_RESOURCES;
        for (var e : packs.entrySet())
            if ((type == PackType.SERVER_DATA ? dataArchives : assetArchives).contains(e.getValue())) {
                String name = "unity/" + e.getKey();
                var title = Component.literal(e.getKey());
                var pack =
                    Pack.readMetaAndCreate(new PackLocationInfo(name, title, MOD_SOURCE, Optional.empty()),
                                           new FilePackResources.FileResourcesSupplier(e.getValue()), type,
                                           new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack == null || target.putIfAbsent(name, pack) != null)
                    throw new IllegalStateException("Invalid or duplicate data pack " + name);
            }
        packs(target);
    }
    public static void packs(Map<String, Pack> target) {
        mount(target, "unity_common_tags", "Unity common item tags", coreArchive());
        var mounted = new HashSet<Path>();
        packs.forEach((id, file) -> {
            if (!dataArchives.contains(file) && mounted.add(file))
                mount(target, "unity/" + id, id, file);
        });
    }
    private static void mount(Map<String, Pack> target, String name, String label, Path file) {
        var title = Component.literal(label);
        var info = new PackLocationInfo(name, title, MOD_SOURCE, Optional.empty());
        var meta = new Pack.Metadata(title, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        var pack = new Pack(info, new FilePackResources.FileResourcesSupplier(file), meta,
                            new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (target.putIfAbsent(name, pack) != null)
            throw new IllegalStateException("Duplicate resource pack " + name);
    }
    public static void creative(CreativeModeTab tab, Object out,
                                CreativeModeTab.ItemDisplayParameters context) {
        TabPages.invalidate();
        if (!Events.CREATIVE.empty())
            UnityTabContents.edit(out, context,
                                  entries -> Events.CREATIVE.post(new Events.Tab(tab, entries)));
    }
    public static void resetIfUnowned() {
        if (owner == null)
            reset();
    }
    public static void reset() {
        TabPages.reset();
        owner = null;
        groups.clear();
        sources.clear();
        WorldRegistries.reset();
        Drops.clear();
        SpawnRules.clear();
        ExtraRegistries.clear();
        EntityAttributes.clear();
        packs.clear();
        dataArchives.clear();
        assetArchives.clear();
        localResourcePacks.clear();
        dependencyStamps = Map.of();
        frozen = false;
        digest = "";
        nextColumn = 7;
        requestedOrder.clear();
        tabOrder.clear();
        Events.clear();
        ConfigurationNetwork.clear();
        PlayNetwork.clear();
        LoginSync.clear();
    }
}
