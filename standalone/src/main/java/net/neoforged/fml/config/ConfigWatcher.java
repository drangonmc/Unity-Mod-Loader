package net.neoforged.fml.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.StandardWatchEventKinds.*;

/** Drain filesystem events under the monitor, then invoke mod code on the owning game thread. */
final class ConfigWatcher {
    private WatchService watcher;
    private final Map<Path, ModConfig> files = new LinkedHashMap<>();
    private final Map<Thread, Integer> running = new IdentityHashMap<>();

    void watch(Collection<ModConfig> configs) {
        WatchService generation;
        List<ModConfig> snapshot = List.copyOf(configs);
        synchronized (this) {
            close();
            if (snapshot.isEmpty())
                return;
            try {
                generation = watcher = FileSystems.getDefault().newWatchService();
                var directories = new HashSet<Path>();
                for (var config : snapshot) {
                    Path file = config.getFullPath().toAbsolutePath().normalize();
                    if (directories.add(file.getParent()))
                        file.getParent().register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                    files.put(file, config);
                }
            } catch (IOException | RuntimeException | Error e) {
                closeAfterFailure(e);
                if (e instanceof IOException io)
                    throw new UncheckedIOException(io);
                if (e instanceof RuntimeException runtime)
                    throw runtime;
                throw (Error)e;
            }
        }
        try {
            reload(generation, snapshot);
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                if (watcher == generation)
                    closeAfterFailure(failure);
            }
            throw failure;
        }
    }

    private void closeAfterFailure(Throwable failure) {
        try {
            close();
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    boolean poll() {
        WatchService generation;
        Set<ModConfig> dirty = new LinkedHashSet<>();
        synchronized (this) {
            if (watcher == null)
                return false;
            generation = watcher;
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
        }
        return !dirty.isEmpty() && reload(generation, dirty);
    }

    private boolean reload(WatchService generation, Collection<ModConfig> configs) {
        Thread owner = Thread.currentThread();
        synchronized (this) {
            if (watcher != generation)
                return false;
            running.merge(owner, 1, Integer::sum);
        }
        boolean changed = false;
        try {
            for (var config : configs) {
                synchronized (this) {
                    if (watcher != generation)
                        break;
                }
                changed |= config.reload();
            }
            return changed;
        } finally {
            synchronized (this) {
                if (running.get(owner) == 1)
                    running.remove(owner);
                else
                    running.put(owner, running.get(owner) - 1);
                notifyAll();
            }
        }
    }

    synchronized void close() {
        files.clear();
        var old = watcher;
        watcher = null;
        if (old != null) {
            try {
                old.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Use before unloading the configs. wait releases the monitor so callbacks can finish. */
    synchronized void stopAndDrain() {
        close();
        boolean interrupted = false;
        try {
            while (running.size() > (running.containsKey(Thread.currentThread()) ? 1 : 0)) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
