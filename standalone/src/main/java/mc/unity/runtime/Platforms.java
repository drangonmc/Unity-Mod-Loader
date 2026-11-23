package mc.unity.runtime;
import java.io.*;
import java.lang.classfile.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import mc.unity.adapter.Entry;
import mc.unity.adapter.common.*;
import mc.unity.adapter.fabric.*;
import mc.unity.adapter.forge.ForgeCommands;
import mc.unity.adapter.neoforge.*;
import mc.unity.core.*;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
public final class Platforms implements AutoCloseable {
    private static Platforms active;
    private boolean client;
    private final Map<String, net.neoforged.fml.ModContainer> neoContainers = new LinkedHashMap<>();
    private List<Entry> ordered = List.of();
    private Set<String> ids = Set.of();
    private boolean closed;
    public static boolean present(String id) {
        return id.equals("minecraft") || id.equals("forge") || active != null && active.ids.contains(id);
    }
    public List<String> ids() {
        return ordered.stream().map(Entry::id).toList();
    }
    private static ClassLoader domain() {
        return Platforms.class.getClassLoader();
    }
    public static Platforms open(Path game, Collection<String> reserved) throws Exception {
        return open(game, reserved, "SERVER");
    }
    public static Platforms open(Path game, Collection<String> reserved, String side) throws Exception {
        if (active != null)
            throw new IllegalStateException("Duplicate mod session");
        var session = new Platforms();
        session.client = side.equals("CLIENT");
        active = session;
        try {
            if (Files.isDirectory(game.resolve("plugins")))
                try (var files = Files.list(game.resolve("plugins"))) {
                    if (files.anyMatch(p -> p.toString().endsWith(".jar")))
                        throw new IOException("Plugins are unsupported by this runtime");
                }
            Map<Path, JarFile> jars;
            try {
                @SuppressWarnings("unchecked")
                var mounted = (Map<Path, JarFile>)domain()
                                  .getClass()
                                  .getMethod("archives", Path.class, boolean.class)
                                  .invoke(domain(), game, side.equals("CLIENT"));
                jars = mounted;
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception cause)
                    throw cause;
                throw e;
            }
            var byId = new TreeMap<String, Entry>();
            var seen = new HashSet<>(reserved);
            var metadata = new HashMap<String, FmlDescriptor.Meta>();
            var abi = new PlatformAbi();
            var unsupported = new TreeMap<String, LinkedHashMap<String, String>>();
            for (var j : jars.entrySet()) {
                Path file = j.getKey();
                JarFile jar = j.getValue();
                try {
                    boolean f = jar.getEntry("fabric.mod.json") != null,
                            n = jar.getEntry("META-INF/neoforge.mods.toml") != null,
                            g = jar.getEntry("META-INF/mods.toml") != null;
                    boolean plain = (boolean)domain()
                                        .getClass()
                                        .getMethod("plainLibrary", Path.class)
                                        .invoke(domain(), file);
                    boolean dataPack = f && (g || n) && UniversalData.matches(jar);
                    if (!plain && !dataPack && (f ? 1 : 0) + (n ? 1 : 0) + (g ? 1 : 0) != 1)
                        throw new IOException("Expected one supported platform descriptor");
                    var annotated = new ArrayList<ClassModel>();
                    var models = new ArrayList<ClassModel>();
                    for (var entry : jar.versionedStream()
                                         .filter(it -> !it.getName().startsWith("META-INF/versions/"))
                                         .toList())
                        if (entry.getName().endsWith(".class") &&
                            !entry.getName().equals("module-info.class")) {
                            try (var in = jar.getInputStream(entry)) {
                                var m = ClassFile.of().parse(in.readAllBytes());
                                if (!(m.thisClass().asInternalName() + ".class").equals(entry.getName()))
                                    throw new IOException("Class name/path mismatch " + entry.getName());
                                models.add(m);
                                if (m.findAttribute(Attributes.runtimeVisibleAnnotations()).isPresent())
                                    annotated.add(m);
                            }
                        }
                    boolean alternateProvider = n && (boolean)domain()
                                                         .getClass()
                                                         .getMethod("usesPuzzlesSpi", Path.class)
                                                         .invoke(domain(), file);
                    var inactive = FabricEntrypoints.inactive(
                        f ? NestedArchives.json(jar, "fabric.mod.json") : null, session.client, models);
                    for (var model : models)
                        if (!inactive.contains(model.thisClass().asInternalName())) {
                            try {
                                if (alternateProvider)
                                    abi.verify(model,
                                               reason
                                               -> PuzzlesModules.deferred(model.thisClass().asInternalName(),
                                                                          reason));
                                else
                                    abi.verify(model);
                            } catch (IOException missing) {
                                unsupported
                                    .computeIfAbsent(file.getFileName().toString(),
                                                     key -> new LinkedHashMap<>())
                                    .putIfAbsent(missing.getMessage(), model.thisClass().asInternalName());
                            }
                        }
                    if (plain)
                        continue;
                    var entries = new ArrayList<Entry>();
                    if (f) {
                        var e = FabricAdapter.read(jar, file, side, dataPack);
                        if (e != null)
                            entries.add(e);
                    } else {
                        if (!n && jar.getJarEntry("META-INF/coremods.json") != null)
                            throw new IOException("Unsupported Forge coremods");
                        var metas = FmlDescriptor.mods(jar, side, n);
                        var ids = metas.stream()
                                      .map(FmlDescriptor.Meta::id)
                                      .collect(java.util.stream.Collectors.toSet());
                        String annotation =
                            n ? "Lnet/neoforged/fml/common/Mod;" : "Lnet/minecraftforge/fml/common/Mod;";
                        for (var model : annotated)
                            for (var a : model.findAttribute(Attributes.runtimeVisibleAnnotations())
                                             .orElseThrow()
                                             .annotations()) {
                                if (a.className().equalsString(annotation) &&
                                    a.elements().stream().noneMatch(el
                                                                    -> el.name().equalsString("value") &&
                                                                           el.value() instanceof
                                                                               AnnotationValue.OfString v &&
                                                                           ids.contains(v.stringValue())))
                                    throw new IOException("Annotation refers to undeclared mod in " +
                                                          model.thisClass().asInternalName());
                            }
                        for (var meta : metas) {
                            metadata.put(meta.id(), meta);
                            entries.add(
                                n ? NeoAdapter.read(file, side, annotated, meta)
                                  : ForgeCommands.read(file, side, annotated, meta, metas.size() == 1));
                        }
                    }
                    for (var e : entries) {
                        if (!e.id().matches("[a-z][a-z0-9_-]{1,63}") || !seen.add(e.id()))
                            throw new IOException("Duplicate or invalid mod ID " + e.id());
                        byId.put(e.id(), e);
                    }
                } catch (IOException | RuntimeException ex) {
                    throw new IOException(file.getFileName() + ": " + ex.getMessage(), ex);
                }
            }
            if (!unsupported.isEmpty()) {
                var failures =
                    new StringBuilder("Unsupported platform types or members before initialization");
                unsupported.forEach((archive, issues) -> {
                    failures.append("\n")
                        .append(archive)
                        .append(" (")
                        .append(issues.size())
                        .append(" distinct first errors across classes)");
                    issues.forEach(
                        (reason,
                         owner) -> failures.append("\n  ").append(reason).append(" in ").append(owner));
                });
                throw new IOException(failures.toString());
            }
            session.ids = Set.copyOf(seen);
            session.ordered = order(byId);
            for (var e : session.ordered) {
                var jar = jars.get(e.file());
                if (e.kind() == Entry.Kind.NEOFORGE) {
                    var meta = metadata.get(e.id());
                    session.neoContainers.put(
                        e.id(), new net.neoforged.fml.ModContainer(e.id(), new NeoBus(e.id()), meta.title(),
                                                                   meta.description()));
                }
                var fabric = NestedArchives.json(jar, "fabric.mod.json");
                if (fabric != null && fabric.has("environment") &&
                    !fabric.get("environment").getAsString().equals("*"))
                    Content.localResources(e.id());
                if (fabric != null && UniversalData.matches(jar)) {
                    Content.dataArchive(e.id(), e.file());
                    continue;
                }
                Content.source(
                    e.id(), e.file(),
                    jar.versionedStream().anyMatch(x
                                                   -> x.getName().startsWith("assets/" + e.id() + "/") ||
                                                          x.getName().startsWith("data/" + e.id() + "/")));
            }
            net.minecraftforge.fml.loading.FMLPaths.setup(game);
            Content.dependencies(
                (Map<Path, String>)domain().getClass().getMethod("dependencyStamps").invoke(domain()));
            net.neoforged.fml.ModList.bind(session.neoContainers);
            net.neoforged.fml.loading.FMLPaths.bind(game);
            net.neoforged.fml.config.ModConfigs.bind(game, session.client);
            LoaderView.INSTANCE.bind(game, session.client, session.ordered);
            Events.PLAY_REGISTRATION.listen(v -> {
                session.dispatchNeo(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent::new);
                if (!session.client)
                    session.dispatchNeo(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent::new);
            });
            if (session.client)
                Events.CLIENT_SETUP.listen(v -> {
                    session.dispatchNeo(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent::new);
                    session.dispatchNeo(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent::new);
                });
            mc.unity.core.EntityAttributes.REGISTRATION.listen(values -> {
                for (var mod : session.neoContainers.values())
                    mod.getEventBus().post(
                        new net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent());
                var spawnBus = net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.BUS;
                if (spawnBus.hasListeners())
                    SpawnRules
                        .<net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.MergedSpawnPredicate<
                            ?>>edit(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent
                                        .MergedSpawnPredicate::new,
                                    rows
                                    -> spawnBus.post(
                                        new net.minecraftforge.event.entity.SpawnPlacementRegisterEvent(
                                            rows)));
                if (!session.neoContainers.isEmpty())
                    SpawnRules
                        .<net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent
                              .MergedSpawnPredicate<?>>edit(net.neoforged.neoforge.event.entity
                                                                .RegisterSpawnPlacementsEvent
                                                                .MergedSpawnPredicate::new,
                                                            rows -> {
                                                                var event =
                                                                    new net.neoforged.neoforge.event.entity
                                                                        .RegisterSpawnPlacementsEvent(rows);
                                                                for (var mod : session.neoContainers.values())
                                                                    mod.getEventBus().post(event);
                                                            });
                var forgeEvent = new net.minecraftforge.event.entity.EntityAttributeCreationEvent(values);
                net.minecraftforge.event.entity.EntityAttributeCreationEvent.BUS.post(forgeEvent);
                for (var mod : session.neoContainers.values())
                    mod.getEventBus().post(
                        new net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent(values));
            });
            FabricGameEvents.wire();
            ForgeCommands.wire(side);
            Events.INIT.listen(ignored -> {
                try {
                    net.neoforged.neoforge.registries.NeoForgeRegistries.initialize();
                    LootModifiers.install();
                    session.initialize();
                } catch (ReflectiveOperationException | IOException e) {
                    throw new IllegalStateException("Platform initialization failed", e);
                }
            });
            Content.own(session);
            return session;
        } catch (Exception | LinkageError e) {
            mc.unity.bootstrap.ModDiagnostics.failure(game, "检查模组与依赖", e, Map.of());
            try {
                session.close();
            } catch (Exception x) {
                e.addSuppressed(x);
            }
            throw e;
        }
    }
    static List<Entry> order(Map<String, Entry> mods) throws IOException {
        var edges = new TreeMap<String, Set<String>>();
        mods.keySet().forEach(k -> edges.put(k, new TreeSet<>()));
        for (var mod : mods.values())
            for (var d : mod.deps()) {
                var required = mods.get(d.id());
                if (required == null) {
                    if (d.required())
                        throw new IOException("Missing " + mod.kind() + " dependency " + d.id() + " for " +
                                              mod.id());
                    continue;
                }
                if (mod.kind() == Entry.Kind.FABRIC)
                    FabricVersions.check(required.version(), d.range());
                else
                    FmlDescriptor.checkRange(required.version(), d.range());
                switch (d.ordering()) {
                case "BEFORE" -> edges.get(required.id()).add(mod.id());
                case "AFTER" -> edges.get(mod.id()).add(required.id());
                case "NONE" -> {
                    if (d.required())
                        edges.get(mod.id()).add(required.id());
                }
                default -> throw new IOException("Invalid dependency ordering " + d.ordering());
                }
            }
        var order = new ArrayList<Entry>();
        while (!edges.isEmpty()) {
            String ready = edges.entrySet()
                               .stream()
                               .filter(e -> e.getValue().isEmpty())
                               .map(Map.Entry::getKey)
                               .findFirst()
                               .orElseThrow(() -> new IOException("Platform dependency cycle " + edges));
            edges.remove(ready);
            edges.values().forEach(set -> set.remove(ready));
            order.add(mods.get(ready));
        }
        return List.copyOf(order);
    }
    public void initialize() throws ReflectiveOperationException, IOException {
        for (var e : ordered)
            for (String entry : e.classes()) {
                Class<?> type = Class.forName(entry, false, domain());
                if (type.getClassLoader() != domain() ||
                    !Files.isSameFile(
                        e.file(),
                        Path.of(java.net.URI.create(
                            type.getProtectionDomain().getCodeSource().getLocation().toExternalForm()))))
                    throw new IOException("Foreign entrypoint " + entry);
                switch (e.kind()) {
                case FABRIC -> {
                    int size = Content.registrySize();
                    ((net.fabricmc.api.ModInitializer)type.getConstructor().newInstance()).onInitialize();
                    if (size != Content.registrySize())
                        Content.include(e.id());
                }
                case NEOFORGE -> {
                    var container = neoContainers.get(e.id());
                    var bus = container.getEventBus();
                    var constructors = type.getConstructors();
                    if (constructors.length != 1)
                        throw new IOException("Ambiguous NeoForge constructor " + entry);
                    var ctor = constructors[0];
                    var args = new ArrayList<Object>();
                    for (var param : ctor.getParameterTypes()) {
                        if (param == net.neoforged.bus.api.IEventBus.class)
                            args.add(bus);
                        else if (param == net.neoforged.fml.ModContainer.class)
                            args.add(container);
                        else if (param == net.neoforged.api.distmarker.Dist.class)
                            args.add(client ? net.neoforged.api.distmarker.Dist.CLIENT
                                            : net.neoforged.api.distmarker.Dist.DEDICATED_SERVER);
                        else
                            throw new IOException("Unsupported NeoForge constructor argument " +
                                                  param.getName());
                    }
                    var previous = net.neoforged.fml.ModLoadingContext.enter(container);
                    try {
                        ctor.newInstance(args.toArray());
                    } finally {
                        net.neoforged.fml.ModLoadingContext.enter(previous);
                    }
                }
                case FORGE -> {
                    var bus = BusGroup.create(e.id());
                    var context = new FMLJavaModLoadingContext(bus);
                    var previous = net.minecraftforge.fml.ModLoadingContext.enter(context);
                    try {
                        Constructor<?> constructor;
                        try {
                            constructor = type.getConstructor(FMLJavaModLoadingContext.class);
                        } catch (NoSuchMethodException absent) {
                            constructor = type.getConstructor();
                        }
                        constructor.newInstance(
                            constructor.getParameterCount() == 0 ? new Object[0] : new Object[] {context});
                    } finally {
                        net.minecraftforge.fml.ModLoadingContext.enter(previous);
                    }
                    ForgeCommands.subscribe(e.id(), domain(), bus);
                }
                }
            }
        net.minecraftforge.registries.DataPackRegistryEvent.NewRegistry.fire();
        for (var container : neoContainers.values()) {
            var previous = net.neoforged.fml.ModLoadingContext.enter(container);
            try {
                container.getEventBus().post(
                    new net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry());
                container.getEventBus().post(
                    new net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent());
            } finally {
                net.neoforged.fml.ModLoadingContext.enter(previous);
            }
        }
        if (ids.contains("fabric-biome-api-v1"))
            Class.forName("mc.unity.adapter.ecology.BiomeRules", true, domain())
                .getMethod("install")
                .invoke(null);
        if (ids.contains("fabric-transfer-api-v1")) {
            Class.forName("mc.unity.adapter.transfer.FabricInventory", true, domain())
                .getMethod("install")
                .invoke(null);
        }
        DataMaps.start();
        net.neoforged.fml.config.ModConfigs.loadCommon();
        if (client) {
            Events.CLIENT_BEFORE_OPTIONS.listen(unused -> {
                try {
                    initializeSided();
                } catch (ReflectiveOperationException | IOException e) {
                    throw new IllegalStateException("Fabric client initialization failed", e);
                }
            });
        } else
            initializeSided();
    }
    private void dispatchNeo(
        java.util.function.BiFunction<net.neoforged.fml.ModContainer, java.util.concurrent.Executor,
                                      net.neoforged.fml.event.lifecycle.ParallelDispatchEvent> phase) {
        var work = new ArrayDeque<Runnable>();
        for (var entry : ordered) {
            var owner = neoContainers.get(entry.id());
            if (owner != null)
                owner.getEventBus().post(phase.apply(owner, task -> work.add(() -> {
                    var previous = net.neoforged.fml.ModLoadingContext.enter(owner);
                    try {
                        task.run();
                    } finally {
                        net.neoforged.fml.ModLoadingContext.enter(previous);
                    }
                })));
        }
        while (!work.isEmpty())
            work.remove().run();
    }
    private void initializeSided() throws ReflectiveOperationException, IOException {
        for (var e : ordered)
            for (String entry : e.sideClasses()) {
                var type = Class.forName(entry, false, domain());
                if (!Files.isSameFile(
                        e.file(),
                        Path.of(java.net.URI.create(
                            type.getProtectionDomain().getCodeSource().getLocation().toExternalForm()))))
                    throw new IOException("Foreign sided entry point " + entry);
                int before = Content.registrySize();
                if (client)
                    type.asSubclass(net.fabricmc.api.ClientModInitializer.class)
                        .getConstructor()
                        .newInstance()
                        .onInitializeClient();
                else
                    type.asSubclass(net.fabricmc.api.DedicatedServerModInitializer.class)
                        .getConstructor()
                        .newInstance()
                        .onInitializeServer();
                if (before != Content.registrySize())
                    Content.include(e.id());
            }
    }
    public void close() throws IOException {
        if (closed)
            return;
        closed = true;
        try {
            try {
                try {
                    net.neoforged.fml.config.ModConfigs.close();
                } finally {
                    net.minecraftforge.fml.event.config.ModConfigEvent.Buses.clear();
                    net.neoforged.neoforge.capabilities.BlockCapability.resetProviders();
                    net.neoforged.neoforge.capabilities.ItemCapability.resetProviders();
                    net.neoforged.neoforge.capabilities.EntityCapability.resetProviders();
                    net.neoforged.neoforge.network.registration.PayloadRegistrar.clearClients();
                    SidedFluids.bind(null);
                    SidedItems.bind(null);
                    DataMaps.close();
                    net.neoforged.fml.ModList.bind(Map.of());
                    neoContainers.clear();
                    net.neoforged.fml.loading.FMLPaths.bind(null);
                    LoaderView.INSTANCE.close();
                }
            } finally {
                SessionEvent.clear();
                ForgeCommands.clear();
                NeoBus.clear();
            }
        } finally {
            active = null;
            Content.reset();
            try {
                domain().getClass().getMethod("releaseMods").invoke(domain());
            } catch (ReflectiveOperationException e) {
                throw new IOException(e);
            }
        }
    }
}
