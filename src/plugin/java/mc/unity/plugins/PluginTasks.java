package mc.unity.plugins;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.*;

final class PluginTasks implements AutoCloseable {
    private final Map<Integer, Task> tasks = new LinkedHashMap<>();
    private final PriorityQueue<Task> pending =
        new PriorityQueue<>(Comparator.<Task>comparingLong(t -> t.due).thenComparingInt(t -> t.id));
    private final Set<BukkitWorker> workers = new LinkedHashSet<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private long tick;
    private int sequence;
    private boolean closed;
    private final class Task implements BukkitTask {
        final int id;
        final Plugin owner;
        final Consumer<BukkitTask> action;
        final boolean async;
        final long period;
        long due;
        int running;
        boolean queued = true, cancelled;
        Task(int id, Plugin plugin, Consumer<BukkitTask> action, boolean async, long delay, long period) {
            this.id = id;
            owner = plugin;
            this.action = action;
            this.async = async;
            due = nextTick(delay);
            this.period = period;
        }
        public int getTaskId() {
            return id;
        }
        public Plugin getOwner() {
            return owner;
        }
        public boolean isSync() {
            return !async;
        }
        public boolean isCancelled() {
            synchronized (PluginTasks.this) {
                return cancelled;
            }
        }
        public void cancel() {
            synchronized (PluginTasks.this) {
                cancelled = true;
                if (queued)
                    pending.remove(this);
                queued = false;
                if (running == 0)
                    tasks.remove(id);
            }
        }
    }
    @SuppressWarnings("unchecked")
    synchronized Object call(String name, Object[] args) {
        switch (name) {
        case "cancelTasks":
            for (var t : List.copyOf(tasks.values()))
                if (t.owner == args[0])
                    t.cancel();
            return null;
        case "cancelTask":
            var target = tasks.get((int)args[0]);
            if (target != null)
                target.cancel();
            return null;
        case "isQueued":
            var queued = tasks.get((int)args[0]);
            return queued != null && queued.queued;
        case "isCurrentlyRunning":
            var running = tasks.get((int)args[0]);
            return running != null && running.running > 0;
        case "getActiveWorkers":
            return List.copyOf(workers);
        case "getPendingTasks":
            return tasks.values().stream().filter(t -> t.queued).toList();
        }
        if (!Set.of("scheduleSyncDelayedTask", "scheduleSyncRepeatingTask", "scheduleAsyncDelayedTask",
                    "scheduleAsyncRepeatingTask", "runTask", "runTaskLater", "runTaskTimer",
                    "runTaskAsynchronously", "runTaskLaterAsynchronously", "runTaskTimerAsynchronously")
                 .contains(name))
            throw Views.unsupported("Scheduler", name);
        if (closed || !(args[0] instanceof Plugin plugin) || !plugin.isEnabled())
            throw new IllegalStateException("Inactive plugin scheduler");
        Consumer<BukkitTask> work;
        if (args[1] instanceof Runnable runnable)
            work = t -> runnable.run();
        else if (args[1] instanceof Consumer<?> callback)
            work = (Consumer<BukkitTask>)callback;
        else
            throw Views.unsupported("Scheduler", "task type");
        long delay = args.length >= 3 ? ((Number)args[2]).longValue() : 0;
        long period = args.length >= 4 ? ((Number)args[3]).longValue() : -1;
        if (period == 0)
            period = 1;
        var t = new Task(++sequence, plugin, work, name.contains("Async"), delay, period);
        tasks.put(t.id, t);
        pending.add(t);
        return name.startsWith("schedule") ? t.id : t;
    }
    private long nextTick(long delay) {
        return tick + Math.min(Math.max(0, delay), Long.MAX_VALUE - tick);
    }
    void tick() {
        synchronized (this) {
            tick++;
        }
        // New zero-delay work may become ready during this heartbeat. Never run it on the submitting thread
        // inline.
        for (;;) {
            Task t;
            synchronized (this) {
                t = pending.peek();
                if (t == null || t.due > tick)
                    return;
                pending.remove();
                t.running++;
                if (t.period > 0) {
                    t.due = nextTick(t.period);
                    pending.add(t);
                } else
                    t.queued = false;
            }
            if (!t.async)
                execute(t);
            else
                try {
                    executor.execute(() -> execute(t));
                } catch (RejectedExecutionException stopped) {
                    synchronized (this) {
                        if (--t.running == 0 && !t.queued)
                            tasks.remove(t.id);
                        if (!closed)
                            throw stopped;
                    }
                }
        }
    }
    private void execute(Task t) {
        var thread = Thread.currentThread();
        BukkitWorker worker = new BukkitWorker() {
            public int getTaskId() {
                return t.id;
            }
            public Plugin getOwner() {
                return t.owner;
            }
            public Thread getThread() {
                return thread;
            }
        };
        var old = thread.getContextClassLoader();
        try {
            synchronized (this) {
                if (t.cancelled || closed || !t.owner.isEnabled())
                    return;
                if (t.async)
                    workers.add(worker);
            }
            thread.setContextClassLoader(t.owner.getClass().getClassLoader());
            t.action.accept(t);
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError fatal)
                throw fatal;
            if (failure instanceof ThreadDeath death)
                throw death;
            Host.LOG.log(java.util.logging.Level.SEVERE, "Task " + t.id + " of " + t.owner.getName(),
                         failure);
        } finally {
            thread.setContextClassLoader(old);
            synchronized (this) {
                workers.remove(worker);
                if (--t.running == 0 && !t.queued)
                    tasks.remove(t.id);
            }
        }
    }
    public void close() {
        synchronized (this) {
            closed = true;
            for (var t : List.copyOf(tasks.values()))
                t.cancel();
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS))
                Host.LOG.warning("A plugin task is still stopping");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
