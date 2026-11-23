package net.neoforged.fml.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.StandardWatchEventKinds.*;

/** Notifications are consumed on the owning game thread, including atomic file replacements. */
final class ConfigWatcher {
    private WatchService watcher;
    private final Map<Path, ModConfig> files = new LinkedHashMap<>();
    synchronized void watch(Collection<ModConfig> configs) {
        close();
        if (configs.isEmpty())
            return;
        try {
            watcher = FileSystems.getDefault().newWatchService();
            Set<Path> directories = new HashSet<>();
            for (var config : configs) {
                var file = config.getFullPath().toAbsolutePath().normalize();
                if (directories.add(file.getParent()))
                    file.getParent().register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                files.put(file, config);
            }
            // Catch an edit between the initial parse and registering the directory.
            var registered = watcher;
            for (var c : configs) {
                if (watcher != registered)
                    break;
                c.reload();
            }
        } catch (IOException | RuntimeException | Error e) {
            try {
                close();
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
            if (e instanceof IOException io)
                throw new UncheckedIOException(io);
            if (e instanceof RuntimeException runtime)
                throw runtime;
            throw (Error)e;
        }
    }
    synchronized boolean poll() {
        if (watcher == null)
            return false;
        Set<ModConfig> dirty = new LinkedHashSet<>();
        WatchKey key;
        while ((key = watcher.poll()) != null) {
            Path directory = (Path)key.watchable();
            for (var event : key.pollEvents()) {
                if (event.kind() == OVERFLOW)
                    dirty.addAll(files.values());
                else {
                    var config =
                        files.get(directory.resolve((Path)event.context()).toAbsolutePath().normalize());
                    if (config != null)
                        dirty.add(config);
                }
            }
            if (!key.reset())
                System.err.println("[Unity] 配置目录已移除 " + directory);
        }
        boolean changed = false;
        var registered = watcher;
        for (var config : dirty) {
            if (watcher != registered)
                break;
            changed |= config.reload();
        }
        return changed;
    }
    synchronized void close() {
        files.clear();
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                watcher = null;
            }
        }
    }
}
