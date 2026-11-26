package qa.bukkit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

final class PluginScopeChecks {
    static void verify(JavaPlugin plugin) {
        var loader = plugin.getClass().getClassLoader();
        if (!loader.isRegisteredAsParallelCapable() || !loader.getParent().isRegisteredAsParallelCapable())
            throw new AssertionError("Plugin domains are not parallel-capable");
        var facade = Bukkit.getPluginManager().getPlugin("UnityFacade").getClass().getClassLoader();
        var types = Bukkit.getPluginManager().getPlugin("UnityTypes").getClass().getClassLoader();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            var jobs = new ArrayList<Future<Class<?>>>();
            for (var cl : List.of(loader, facade, types, loader))
                jobs.add(pool.submit(() -> {
                    start.await();
                    return Class.forName("qa.libc.Types$Value", false, cl);
                }));
            start.countDown();
            var expected = jobs.getFirst().get(5, TimeUnit.SECONDS);
            for (var job : jobs)
                if (job.get(5, TimeUnit.SECONDS) != expected)
                    throw new AssertionError("Type identity");
            try {
                ((io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader)loader)
                    .loadClass("qa.libc.Types$Value", false, false, true);
                throw new AssertionError("Local lookup leaked a transitive type");
            } catch (ClassNotFoundException expectedLocalMiss) {}
            if (qa.libb.Facade.value().getClass() != expected || expected.getClassLoader() != types)
                throw new AssertionError("Transitive API");
            try {
                Class.forName("qa.unrelated.Absent", false, loader);
                throw new AssertionError("Absent class");
            } catch (ClassNotFoundException expectedMiss) {}
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        plugin.getLogger().info("QA24 TRANSITIVE CLASS IDENTITY PASS");
        var scheduler = Bukkit.getScheduler();
        scheduler.runTask(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            var order = new AtomicInteger();
            scheduler.runTaskLater(plugin, () -> {
                if (Bukkit.getCurrentTick() != now || order.getAndIncrement() != 1)
                    throw new AssertionError("Zero delay");
                plugin.getLogger().info("QA24 ZERO DELAY SAME HEARTBEAT");
            }, 0);
            scheduler.runTaskLater(plugin, () -> {
                if (Bukkit.getCurrentTick() != now + 1 || order.get() != 2)
                    throw new AssertionError("Positive delay");
                plugin.getLogger().info("QA24 NEXT TICK");
            }, 1);
            order.incrementAndGet();
        });
        scheduler.runTaskAsynchronously(
            plugin, () -> { throw new AssertionError("QA24 intentional task assertion"); });
        var count = new AtomicInteger();
        var overlap = new CountDownLatch(1);
        scheduler.runTaskTimerAsynchronously(plugin, (org.bukkit.scheduler.BukkitTask task) -> {
            int call = count.incrementAndGet();
            if (call == 1) {
                try {
                    if (!overlap.await(5, TimeUnit.SECONDS))
                        throw new AssertionError("Async repeat did not overlap");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            } else if (call == 2) {
                task.cancel();
                overlap.countDown();
                plugin.getLogger().info("QA24 ASYNC REPEAT OVERLAP");
            }
        }, 0, 1);
    }
}
