package qa.entry;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReentrantFabric implements ModInitializer {
    static final AtomicInteger constructions = new AtomicInteger();
    public static final class Nested implements Runnable {
        public Nested() {
            constructions.incrementAndGet();
        }
        public void run() {}
    }
    static final AtomicInteger parallelCount = new AtomicInteger(), cycleCount = new AtomicInteger();
    public static final class Parallel implements Runnable {
        public Parallel() {
            parallelCount.incrementAndGet();
        }
        public void run() {}
    }
    static final AtomicInteger failureCalls = new AtomicInteger();
    static final CountDownLatch creating = new CountDownLatch(1), release = new CountDownLatch(1);
    public static final class Retry implements Runnable {
        public Retry() {
            if (failureCalls.incrementAndGet() == 1) {
                creating.countDown();
                try {
                    if (!release.await(8, TimeUnit.SECONDS))
                        throw new AssertionError("Cannot release constructor");
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                throw new IllegalStateException("expected first constructor failure");
            }
        }
        public void run() {}
    }
    private static void retryAtPublication(FabricLoader loader) {
        var entry = loader.getEntrypointContainers("qa_retry", Runnable.class).getFirst();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(entry::getEntrypoint);
            try {
                if (!creating.await(5, TimeUnit.SECONDS))
                    throw new AssertionError("Constructor not entered");
                var view = entry.getProvider();
                var cache = view.getClass().getDeclaredField("instances");
                cache.setAccessible(true);
                var monitor = loader.getClass().getDeclaredField("waiting");
                monitor.setAccessible(true);
                CompletableFuture<?> pending = null;
                synchronized (monitor.get(loader)) {
                    for(var slot:((Map<?,?>)cache.get(view)).values()){
                        var result = slot.getClass().getDeclaredField("result");
                        result.setAccessible(true);
                        var future = (CompletableFuture<?>)result.get(slot);
                        if (!future.isDone()) {
                            if (pending != null)
                                throw new AssertionError("Ambiguous pending entry");
                            pending = future;
                        }
                    }
                }
                if (pending == null)
                    throw new AssertionError("Missing resolution");
                var retry = pending.handle((unused, error) -> entry.getEntrypoint());
                release.countDown();
                try {
                    first.get(5, TimeUnit.SECONDS);
                    throw new AssertionError("First construction succeeded");
                } catch (ExecutionException expected) {}
                if (retry.get(5, TimeUnit.SECONDS) != entry.getEntrypoint() || failureCalls.get() != 2)
                    throw new AssertionError("Failed resolution returned again");
                System.out.println("QA24 FAILURE PUBLICATION RETRY PASS");
            } finally {
                release.countDown();
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    public static final class Cycle implements Runnable {
        public Cycle() {
            if (cycleCount.incrementAndGet() == 1)
                FabricLoader.getInstance().getEntrypoints("qa_cycle", Runnable.class);
        }
        public void run() {}
    }
    public static int order(String left, String right) {
        return left.compareTo(right);
    }
    public ReentrantFabric() {
        var loader = FabricLoader.getInstance();
        var nested = loader.getEntrypoints("qa_nested", Runnable.class).getFirst();
        if (nested != loader.getEntrypoints("qa_nested", Runnable.class).getFirst() ||
            constructions.get() != 1)
            throw new AssertionError("identity");
    }
    @SuppressWarnings("unchecked")
    public void onInitialize() {
        Comparator<String> cmp =
            FabricLoader.getInstance().getEntrypoints("qa_compare", Comparator.class).getFirst();
        if (cmp.compare("ab", "cd") >= 0 || cmp.compare("a", "a") != 0)
            throw new AssertionError("Comparator");
        var loader = FabricLoader.getInstance();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<Future<Runnable>>();
            for (int i = 0; i < 12; i++)
                results.add(
                    pool.submit(() -> loader.getEntrypoints("qa_parallel", Runnable.class).getFirst()));
            var first = results.getFirst().get(5, TimeUnit.SECONDS);
            for (var f : results)
                if (f.get(5, TimeUnit.SECONDS) != first)
                    throw new AssertionError("Duplicate instance");
            if (parallelCount.get() != 1)
                throw new AssertionError("Duplicate constructor");
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
        try {
            loader.getEntrypoints("qa_cycle", Runnable.class);
            throw new AssertionError("Cycle accepted");
        } catch (IllegalStateException expected) {
            Throwable t = expected;
            while (t != null && !String.valueOf(t.getMessage()).contains("Circular Fabric entrypoint"))
                t = t.getCause();
            if (t == null)
                throw expected;
        }
        loader.getEntrypoints("qa_cycle", Runnable.class);
        if (cycleCount.get() != 2)
            throw new AssertionError("Failed entry poisoned");
        retryAtPublication(loader);
        System.out.println("QA24 CONCURRENT ENTRY IDENTITY + CYCLE RETRY PASS");
        System.out.println("QA24 REENTRANT ENTRY + COMPARATOR PASS");
    }
}
