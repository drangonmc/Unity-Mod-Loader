package net.neoforged.fml.config;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import mc.unity.core.Events;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;

public final class ModConfigs {
    private static final Map<String, ModConfig> configs = new LinkedHashMap<>();
    private static final ConfigWatcher local = new ConfigWatcher(), world = new ConfigWatcher();
    private static final Object worldLifecycle = new Object();
    private static volatile Object session;
    private static volatile MinecraftServer watchedServer;
    private static Path game;
    private static boolean client, closing;
    private static int clientTicks;

    public static synchronized void bind(Path root, boolean physicalClient) {
        if (session != null || closing)
            throw new IllegalStateException("Config session is already active or closing");
        Object epoch = new Object();
        session = epoch;
        game = root;
        client = physicalClient;
        Events.SERVER_ABOUT_TO_START.listen(server -> startWorld(epoch, server));
        Events.SERVER_STOPPED.listen(server -> stopWorld(epoch, server));
        Events.SERVER_TICK_PRE.listen((server, time) -> {
            if (session != epoch || server.getTickCount() % 20 != 0)
                return;
            if (!physicalClient)
                local.poll();
            if (session == epoch && watchedServer == server && world.poll()) {
                synchronized (worldLifecycle) {
                    if (session == epoch && watchedServer == server)
                        ConfigSync.snapshot(server);
                }
            }
        });
        if (physicalClient)
            Events.CLIENT_TICK_PRE.listen(t -> {
                if (session == epoch && ++clientTicks % 20 == 0)
                    local.poll();
            });
    }

    private static void startWorld(Object epoch, MinecraftServer server) {
        var registered = getConfigSet(ModConfig.Type.SERVER);
        var directory = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        synchronized (worldLifecycle) {
            if (session != epoch)
                return;
            world.stopAndDrain();
            ConfigSync.endWorld();
            watchedServer = server;
            try {
                for (var config : registered) {
                    if (session != epoch)
                        return;
                    config.unload();
                    config.load(directory);
                }
                if (session != epoch)
                    return;
                world.watch(registered);
                if (session == epoch && watchedServer == server)
                    ConfigSync.snapshot(server);
            } catch (RuntimeException | Error failure) {
                watchedServer = null;
                try {
                    world.stopAndDrain();
                } catch (RuntimeException e) {
                    failure.addSuppressed(e);
                }
                ConfigSync.endWorld();
                for (var config : registered) {
                    try {
                        config.unload();
                    } catch (RuntimeException e) {
                        failure.addSuppressed(e);
                    }
                }
                throw failure;
            }
        }
    }

    private static void stopWorld(Object epoch, MinecraftServer server) {
        synchronized (worldLifecycle) {
            if (session != epoch || watchedServer != server)
                return;
            watchedServer = null;
            world.stopAndDrain();
            ConfigSync.endWorld();
            getConfigSet(ModConfig.Type.SERVER).forEach(ModConfig::unload);
        }
    }

    public static void register(ModContainer owner, ModConfig.Type type, IConfigSpec spec, String file) {
        register(owner.getModId(), type, spec, file, e -> owner.getEventBus().post(e));
    }

    public static void register(String id, ModConfig.Type type, IConfigSpec spec, String file,
                                Consumer<ModConfigEvent> events) {
        var epoch = session;
        var relative = Path.of(file).normalize();
        if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank())
            throw new IllegalArgumentException(file);
        var config = new ModConfig(id, type, spec, relative.toString(), events);
        Path root;
        synchronized (ModConfigs.class) {
            if (epoch == null || session != epoch)
                throw new IllegalStateException("No config session");
            if (configs.putIfAbsent(relative.toString(), config) != null)
                throw new IllegalArgumentException("Duplicate config file " + file);
            root = game;
        }
        if (type == ModConfig.Type.STARTUP) {
            synchronized (config) {
                if (session == epoch)
                    config.load(root.resolve("config"));
            }
        }
    }

    public static void loadCommon() {
        var epoch = session;
        synchronized (worldLifecycle) {
            if (epoch == null || session != epoch)
                return;
            var registered = new ArrayList<>(getConfigSet(ModConfig.Type.COMMON));
            if (client)
                registered.addAll(getConfigSet(ModConfig.Type.CLIENT));
            for (var c : registered) {
                if (session != epoch)
                    return;
                c.load(game.resolve("config"));
            }
            if (session != epoch)
                return;
            local.watch(registered);
            if (session == epoch)
                ConfigSync.register(getConfigSet(ModConfig.Type.SERVER));
        }
    }

    public static synchronized Set<ModConfig> getConfigSet(ModConfig.Type type) {
        var result = new LinkedHashSet<ModConfig>();
        for (var c : configs.values())
            if (c.getType() == type)
                result.add(c);
        return Collections.unmodifiableSet(result);
    }

    public static void close() {
        List<ModConfig> closingConfigs;
        synchronized (ModConfigs.class) {
            if (session == null)
                return;
            closingConfigs = List.copyOf(configs.values());
            closing = true;
            session = null;
        }
        try {
            // Wait for admission to finish before draining watchers. Never wait under the registry monitor.
            synchronized (worldLifecycle) {
                watchedServer = null;
            }
            var cleanup = new ArrayList<Runnable>();
            cleanup.add(local::stopAndDrain);
            cleanup.add(world::stopAndDrain);
            cleanup.add(ConfigSync::clear);
            for (var c : closingConfigs)
                cleanup.add(() -> {
                    if (c.getType() == ModConfig.Type.STARTUP) {
                        synchronized (c) {
                            c.unload();
                        }
                    } else
                        c.unload();
                });
            RuntimeException failure = null;
            for (var task : cleanup) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    if (failure == null)
                        failure = e;
                    else
                        failure.addSuppressed(e);
                }
            }
            if (failure != null)
                throw failure;
        } finally {
            synchronized (ModConfigs.class) {
                configs.clear();
                game = null;
                clientTicks = 0;
                closing = false;
            }
        }
    }
}
