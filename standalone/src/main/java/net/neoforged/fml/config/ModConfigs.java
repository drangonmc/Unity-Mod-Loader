package net.neoforged.fml.config;
import java.nio.file.*;
import java.util.*;
import mc.unity.core.Events;
import net.neoforged.fml.ModContainer;
public final class ModConfigs {
    private static final Map<String, ModConfig> configs = new LinkedHashMap<>();
    private static Path game;
    private static boolean client;
    private static final ConfigWatcher local = new ConfigWatcher(), world = new ConfigWatcher();
    private static int clientTicks;
    private static volatile Object session;
    private static net.minecraft.server.MinecraftServer watchedServer;
    private static List<ModConfig> localConfigs() {
        var result = new ArrayList<>(getConfigSet(ModConfig.Type.COMMON));
        if (client)
            result.addAll(getConfigSet(ModConfig.Type.CLIENT));
        return result;
    }
    public static synchronized void bind(Path root, boolean physicalClient) {
        Object epoch = new Object();
        session = epoch;
        game = root;
        client = physicalClient;
        Events.SERVER_STOPPED.listen(server -> {
            synchronized(world){
                if(session!=epoch || watchedServer!=server)return;
                watchedServer=null;world.close();ConfigSync.endWorld();
                getConfigSet(ModConfig.Type.SERVER).forEach(ModConfig::unload);
    }
});
Events.SERVER_ABOUT_TO_START.listen(server -> {
    var configs = getConfigSet(ModConfig.Type.SERVER);
    var folder =
        server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serverconfig");
    synchronized (world) {
        if (session != epoch)
            return;
        world.close();
        ConfigSync.endWorld();
        watchedServer = server;
        try {
            for (var c : configs) {
                c.unload();
                c.load(folder);
            }
            world.watch(configs);
            ConfigSync.snapshot(server);
        } catch (RuntimeException | Error failure) {
            watchedServer = null;
            try {
                world.close();
            } catch (RuntimeException e) {
                failure.addSuppressed(e);
            }
            ConfigSync.endWorld();
            for (var c : configs)
                try {
                    c.unload();
                } catch (RuntimeException e) {
                    failure.addSuppressed(e);
                }
            throw failure;
        }
    }
});
Events.SERVER_TICK_PRE.listen((server, time) -> {
    if (session != epoch || server.getTickCount() % 20 != 0)
        return;
    if (!client)
        local.poll();
    synchronized (world) {
        if (session == epoch && watchedServer == server && world.poll())
            ConfigSync.snapshot(server);
    }
});
if (client)
    Events.CLIENT_TICK_PRE.listen(t -> {
        if (session == epoch && ++clientTicks % 20 == 0)
            local.poll();
    });
}
public static synchronized void register(ModContainer owner, ModConfig.Type type, IConfigSpec spec,
                                         String file) {
    register(owner.getModId(), type, spec, file, e -> owner.getEventBus().post(e));
}
public static synchronized void register(
    String id, ModConfig.Type type, IConfigSpec spec, String file,
    java.util.function.Consumer<net.neoforged.fml.event.config.ModConfigEvent> events) {
    if (game == null)
        throw new IllegalStateException("No config session");
    var relative = Path.of(file).normalize();
    if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank())
        throw new IllegalArgumentException(file);
    var config = new ModConfig(id, type, spec, relative.toString(), events);
    if (configs.putIfAbsent(relative.toString(), config) != null)
        throw new IllegalArgumentException("Duplicate config file " + file);
    if (type == ModConfig.Type.STARTUP)
        config.load(game.resolve("config"));
}
public static void loadCommon() {
    getConfigSet(ModConfig.Type.COMMON).forEach(c -> c.load(game.resolve("config")));
    if (client)
        getConfigSet(ModConfig.Type.CLIENT).forEach(c -> c.load(game.resolve("config")));
    local.watch(localConfigs());
    ConfigSync.register(getConfigSet(ModConfig.Type.SERVER));
}
public static synchronized Set<ModConfig> getConfigSet(ModConfig.Type type) {
    var result = new LinkedHashSet<ModConfig>();
    for (var config : configs.values())
        if (config.getType() == type)
            result.add(config);
    return Collections.unmodifiableSet(result);
}
public static void close() {
    List<ModConfig> closing;
    synchronized (ModConfigs.class) {
        if (session == null)
            return;
        closing = List.copyOf(configs.values());
        game = null;
        session = null;
    }
    // Do not hold the registry monitor while waiting on an in-flight reload callback.
    var cleanup = new ArrayList<Runnable>();
    cleanup.add(local::close);cleanup.add(()->{synchronized(world){watchedServer=null;world.close();
}
});
cleanup.add(ConfigSync::clear);
closing.forEach(c -> cleanup.add(c::unload));
clientTicks = 0;
RuntimeException failure = null;
for (var step : cleanup)
    try {
        step.run();
    } catch (RuntimeException e) {
        if (failure == null)
            failure = e;
        else
            failure.addSuppressed(e);
    }
synchronized (ModConfigs.class) {
    for (var c : closing)
        configs.remove(c.getFileName(), c);
}
if (failure != null)
    throw failure;
}
}
