import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.jar.*;
import java.util.function.*;
import com.google.gson.*;
import com.electronwill.nightconfig.core.*;
import io.netty.buffer.Unpooled;
import mc.unity.adapter.common.NestedArchives;
import mc.unity.core.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.*;
import net.minecraft.network.codec.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.*;
import net.minecraft.util.thread.BlockableEventLoop;
import net.neoforged.fml.config.*;

class ReviewChecks {
    static void check(boolean ok, String why) {
        if (!ok)
            throw new AssertionError(why);
    }
    static void await(CountDownLatch latch) throws InterruptedException {
        check(latch.await(6, TimeUnit.SECONDS), "Timed out waiting for test participant");
    }
    static void waitInside(CountDownLatch l) {
        try {
            await(l);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
    static Field field(Class<?> c, String name) throws Exception {
        var f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    static Method method(Class<?> c, String name, Class<?>... args) throws Exception {
        var m = c.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }
    static class Client implements ConfigurationNetwork.Peer {
        final ConfigurationNetwork.State state = new ConfigurationNetwork.State();
        final Connection conn = new Connection(PacketFlow.CLIENTBOUND) {
            public boolean isConnected() {
                return true;
            }
            public boolean isMemoryConnection() {
                return true;
            }
            public PacketListener getPacketListener() {
                return Client.this;
            }
            public void send(Packet<?> p) {
                check(!Thread.holdsLock(state) && !Thread.holdsLock(ConfigurationNetwork.class),
                      "Network send under lifecycle lock");
            }
        };
        public ConfigurationNetwork.State unity$state() {
            return state;
        }
        public Connection unity$connection() {
            return conn;
        }
        public BlockableEventLoop<?> unity$executor() {
            return null;
        }
        public PacketFlow flow() {
            return PacketFlow.CLIENTBOUND;
        }
        public ConnectionProtocol protocol() {
            return ConnectionProtocol.CONFIGURATION;
        }
        public boolean isAcceptingMessages() {
            return true;
        }
        public void onDisconnect(DisconnectionDetails d) {
            ConfigurationNetwork.close(this, true);
        }
    }
    record Value(CustomPacketPayload.Type<Value> type) implements CustomPacketPayload {}
    static CustomPacketPayload.Type<Value> type(String id) {
        return new CustomPacketPayload.Type<>(Identifier.parse("review:" + id));
    }
    static void terminal() throws Exception {
        ConfigurationNetwork.clear();
        var t = type("lifecycle");
        ConfigurationNetwork.type(PacketFlow.SERVERBOUND, t, StreamCodec.unit(new Value(t)), "1", false);
        var p = new Client();
        ConfigurationNetwork.init(p);
        var ends = new AtomicInteger();
        var completions = new AtomicInteger();
        ConfigurationNetwork.CLIENT_END.listen(x -> {
            check(!Thread.holdsLock(x.unity$state()) && !Thread.holdsLock(ConfigurationNetwork.class),
                  "END under lock");
            ends.incrementAndGet();
        });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closing = new CountDownLatch(1);
        var held = new HashMap<Identifier, String>() {
            public String get(Object id) {
                entered.countDown();
                waitInside(release);
                return super.get(id);
            }
        };
        held.put(t.id(), "1");
        var c = Class.forName("mc.unity.core.ConfigurationNetwork$Channels").getDeclaredConstructors()[0];
        c.setAccessible(true);
        var handshake = (CustomPacketPayload)c.newInstance(0, held, Map.of());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var io = pool.submit(() -> ConfigurationNetwork.dispatch(p, handshake));
            await(entered);
            var end = pool.submit(() -> {
                closing.countDown();
                ConfigurationNetwork.close(p, true);
            });
            await(closing);
            release.countDown();
            io.get(5, TimeUnit.SECONDS);
            end.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        check(ends.get() == 1 && !ConfigurationNetwork.canSend(p, t.id()) &&
                  ConfigurationNetwork.sendable(p).isEmpty(),
              "Connection resurrected");
        check(((Map<?,?>)field(p.state.getClass(),"remote").get(p.state)).isEmpty(),"Retained capabilities");
        var other = new Client();
        ConfigurationNetwork.init(other);
        var inside = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        ConfigurationNetwork.CLIENT_COMPLETE.listen(x -> {
            check(!Thread.holdsLock(x.unity$state()) && !Thread.holdsLock(ConfigurationNetwork.class),
                  "COMPLETE under lock");
            completions.incrementAndGet();
            inside.countDown();
            waitInside(finish);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> ConfigurationNetwork.complete(other));
            await(inside);
            pool.submit(() -> ConfigurationNetwork.close(other, true)).get(3, TimeUnit.SECONDS);
            ConfigurationNetwork.complete(p);
            finish.countDown();
            first.get(3, TimeUnit.SECONDS);
        } finally {
            finish.countDown();
        }
        ConfigurationNetwork.close(other, true);
        check(ends.get() == 1 && completions.get() == 1, "Duplicate terminal callbacks");
        ConfigurationNetwork.clear();
        System.out.println("PASS atomic configuration close versus handshake, completion and disconnect");
    }
    static void watcher(Path dir) throws Exception {
        ModConfigs.bind(dir, true);
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        IConfigSpec spec = new IConfigSpec() {
            public boolean isEmpty() {
                return false;
            }
            public void validateSpec(ModConfig c) {}
            public boolean isCorrect(UnmodifiableCommentedConfig c) {
                return c.get("v") instanceof Number;
            }
            public void correct(CommentedConfig c) {
                c.set("v", 1);
            }
            public void acceptConfig(ILoadedConfig c) {}
        };
        ModConfigs.register("watchtest", ModConfig.Type.COMMON, spec, "a.toml", e -> {
            if (e instanceof net.neoforged.fml.event.config.ModConfigEvent.Reloading) {
                calls.incrementAndGet();
                entered.countDown();
                waitInside(release);
                ModConfigs.getConfigSet(ModConfig.Type.COMMON);
            }
        });
        ModConfigs.loadCommon();
        var watcher = field(ModConfigs.class, "local").get(null);
        var cls = watcher.getClass();
        var service = field(cls, "watcher");
        var original = (WatchService)service.get(watcher);
        var watch = method(cls, "watch", Collection.class);
        var poll = method(cls, "poll");
        var conf = ModConfigs.getConfigSet(ModConfig.Type.COMMON).iterator().next();
        var alt = Files.createDirectories(dir.resolve("next/world"));
        method(ModConfig.class, "load", Path.class)
            .invoke(conf, Path.of("").toAbsolutePath().relativize(alt.toAbsolutePath()));
        watch.invoke(watcher, List.of(conf));
        try {
            original.poll();
            throw new AssertionError("Replaced watcher still open");
        } catch (ClosedWatchServiceException expected) {}
        var paths=((Map<?,?>)field(cls,"files").get(watcher)).keySet();
        check(paths.equals(Set.of(alt.toAbsolutePath().normalize().resolve("a.toml"))), "Old world retained");
        Files.writeString(conf.getFullPath(), "v=2\n");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tick = pool.submit(() -> {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline) {
                    if (Boolean.TRUE.equals(poll.invoke(watcher)))
                        return true;
                    Thread.sleep(5);
                }
                return false;
            });
            await(entered);
            var started = new CountDownLatch(1);
            var close = pool.submit(() -> {
                started.countDown();
                ModConfigs.close();
            });
            await(started);
            release.countDown();
            check(tick.get(5, TimeUnit.SECONDS), "Lost change");
            close.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            ModConfigs.close();
        }
        check(service.get(watcher) == null && !(boolean)poll.invoke(watcher) && calls.get() == 1,
              "Closed watcher tick");
        System.out.println(
            "PASS watcher replacement, normalized paths, poll during teardown and handle closure");
    }
    static void limits() {
        var id = type("bounded");
        for (boolean play : new boolean[] {false, true})
            for (var flow : PacketFlow.values()) {
                int limit = flow == PacketFlow.SERVERBOUND ? 32767 : 1048576;
                PlayNetwork.clear();
                ConfigurationNetwork.clear();
                var seen = new AtomicInteger(-1);
                StreamCodec<FriendlyByteBuf, Value> writer = new StreamCodec<>() {
                    public Value decode(FriendlyByteBuf b) {
                        return new Value(id);
                    }
                    public void encode(FriendlyByteBuf b, Value v) {
                        seen.set(b.maxCapacity());
                        check(!play || b instanceof RegistryFriendlyByteBuf, "Lost registry context");
                        b.ensureWritable(limit + 1);
                    }
                };
                if (play) {
                    PlayNetwork.register(id.id(), "1", flow, id, writer.cast(), (v, ctx) -> {}, false, true);
                    PlayNetwork.manifest();
                } else
                    ConfigurationNetwork.type(flow, id, writer, "1", true);
                StreamCodec base = flow == PacketFlow.SERVERBOUND
                                       ? ServerboundCustomPayloadPacket.STREAM_CODEC
                                   : play ? ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC
                                          : ClientboundCustomPayloadPacket.CONFIG_STREAM_CODEC;
                StreamCodec codec =
                    flow == PacketFlow.SERVERBOUND
                        ? (play ? PlayNetwork.serverbound(base) : ConfigurationNetwork.serverbound(base))
                        : (play ? PlayNetwork.clientbound(base) : ConfigurationNetwork.clientbound(base));
                FriendlyByteBuf out = play ? new RegistryFriendlyByteBuf(
                                                 Unpooled.buffer(16), net.minecraft.core.RegistryAccess.EMPTY)
                                           : new FriendlyByteBuf(Unpooled.buffer(16));
                try {
                    out.writeByte(27);
                    try {
                        codec.encode(out, ConfigurationNetwork.packet(flow, new Value(id)));
                        throw new AssertionError("Oversize accepted");
                    } catch (IllegalArgumentException expected) {}
                    check(seen.get() == limit && out.capacity() == 16 && out.writerIndex() == 1 &&
                              out.getByte(0) == 27,
                          "Unbounded allocation or partial output");
                } finally {
                    out.release();
                }
            }
        PlayNetwork.clear();
        ConfigurationNetwork.clear();
        System.out.println(
            "PASS bounded Play and configuration encoders, registry context and atomic output");
    }
    static byte[] jar(Map<String, byte[]> members) throws IOException {
        var data = new ByteArrayOutputStream();
        try (var out = new JarOutputStream(data)) {
            for (var e : members.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return data.toByteArray();
    }
    static byte[] mod(String id, String version, Map<String, String> deps, Map<String, byte[]> inners)
        throws IOException {
        var json = new JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("id", id);
        json.addProperty("version", version);
        var requires = new JsonObject();
        deps.forEach(requires::addProperty);
        json.add("depends", requires);
        var members = new LinkedHashMap<>(inners);
        var jars = new JsonArray();
        for (String n : inners.keySet()) {
            var e = new JsonObject();
            e.addProperty("file", n);
            jars.add(e);
        }
        json.add("jars", jars);
        members.put("fabric.mod.json", json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return jar(members);
    }
    static void solve(Path dir) throws Exception {
        Files.createDirectories(dir);
        var inners = new TreeMap<String, byte[]>();
        var deps = new HashMap<String, String>();
        for (int i = 0; i < 24; i++) {
            String id = "lib" + i;
            deps.put(id, "=1.0");
            for (String ver : List.of("1.0", "2.0"))
                inners.put(id + "-" + ver + ".jar", mod(id, ver, Map.of(), Map.of()));
        }
        inners.put("last.jar", mod("zz_narrow", "1.0", deps, Map.of()));
        Files.write(dir.resolve("parent.jar"), mod("root_mod", "1.0", Map.of(), inners));
        try (var found = NestedArchives.resolve(dir, false)) {
            check(found.metadata()
                          .stream()
                          .filter(e -> e.id().startsWith("lib") && e.version().equals("1.0"))
                          .count() == 24,
                  "Wrong nested result");
            check(found.searchSteps() <= 27, "Excessive backtracking: " + found.searchSteps());
            System.out.println("PASS MRV solver resolved 24 binary choices in " + found.searchSteps() +
                               " states");
        }
    }
    static void cleanup(Path dir) throws Exception {
        var file = dir.resolve("a file #%.jar");
        Files.write(file, jar(Map.of("docs/a #%.txt", new byte[] {5})));
        var cl = Class.forName("mc.unity.bootstrap.GameLoader");
        var ctor = cl.getDeclaredConstructor(URL[].class, Path.class);
        ctor.setAccessible(true);
        var loader = (java.net.URLClassLoader)ctor.newInstance(new URL[] {file.toUri().toURL()}, file);
        IOException primary = new IOException("first archive");
        try {
            var failing = new JarFile(file.toFile()) {
                public void close() throws IOException {
                    super.close();
                    throw primary;
                }
            };
            var url = method(cl, "url", JarFile.class, String.class);
            var good = (URL)url.invoke(loader, failing, "docs/a #%.txt");
            try (var in = good.openStream()) {
                check(in.read() == 5, "Encoded URL failed");
            }
            var missing = (URL)url.invoke(loader, failing, "missing");
            try (var ignored = missing.openStream()) {
                throw new AssertionError("Missing resource opened");
            } catch (FileNotFoundException expected) {}
            ((Map<Path, JarFile>)field(cl, "mods").get(loader)).put(file, failing);
            var attempts = new AtomicInteger();
            field(cl, "nested").set(loader, (AutoCloseable)() -> {
                if (attempts.getAndIncrement() == 0)
                    throw new IOException("nested archive");
            });
            try {
                cl.getMethod("releaseMods").invoke(loader);
                throw new AssertionError("Close failure lost");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                check(cause == primary && cause.getSuppressed().length == 1 &&
                          cause.getSuppressed()[0].getCause().getMessage().equals("nested archive"),
                      "Close exception ordering");
            }
            try (var ignored = good.openStream()) {
                throw new AssertionError("Closed resource opened");
            } catch (IOException expected) {
                check(expected.getCause() instanceof IllegalStateException, "Closed JAR diagnosis");
            }
            loader.close();
            check(attempts.get() == 2, "Nested close retry lost");
        } finally {
            loader.close();
        }
        System.out.println("PASS archive close exception preservation and missing/closed resource URLs");
    }
    static void placement() throws Exception {
        var a = new Placement.Change(null, null, null, null, 3, 0);
        var b = new Placement.Change(null, null, null, null, 3, 0);
        check(a.equals(b) && a != b, "Placement fixture");
        var changes = new ArrayList<>(List.of(a, b, a));
        method(Placement.class, "discard", List.class, Placement.Change.class).invoke(null, changes, b);
        check(changes.size() == 2 && changes.get(0) == a && changes.get(1) == a,
              "Wrong nested snapshot removed");
        System.out.println("PASS identity-based removal of equal recursive placement snapshots");
    }
    public static void main(String[] args) throws Exception {
        var dir = Files.createDirectories(Path.of(args[0]).resolve("review"));
        terminal();
        watcher(dir.resolve("configs"));
        limits();
        solve(dir.resolve("solver"));
        cleanup(dir);
        placement();
    }
}
