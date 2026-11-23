package mc.unity.core;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.*;
import net.minecraft.util.thread.BlockableEventLoop;

/** Configuration-only payload codecs and receiver negotiation. Each listener owns exactly one session. */
public final class ConfigurationNetwork {
    public interface Link {
        Connection unity$connection();
    }
    public interface Peer extends Link, PacketListener {
        State unity$state();
        BlockableEventLoop<?> unity$executor();
    }
    public interface ServerPeer extends Peer {
        Queue<ConfigurationTask> unity$tasks();
        void unity$finish(ConfigurationTask.Type type);
        ConfigurationTask unity$current();
    }
    public interface Receiver<T extends CustomPacketPayload> {
        void receive(T value, Peer peer);
    }
    private record Key(PacketFlow direction, Identifier id) {}
    private record Binding<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                          StreamCodec<? super FriendlyByteBuf, T> codec,
                                                          String version, boolean optional) {}
    public record Handler(Object original, Receiver<?> receiver, boolean main) {}
    private static boolean requiredFrozen, opened;
    private static volatile boolean required;
    static boolean required() {
        return required;
    }
    private static volatile Map<Key, Binding<?>> types = Map.of();
    private static volatile Map<Key, Handler> globals = Map.of();
    private static final Set<Connection> negotiated = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Peer> active = Collections.newSetFromMap(new WeakHashMap<>());
    public static final Events.Signal<ServerPeer> BEFORE = new Events.Signal<>(),
                                                  CONFIGURE = new Events.Signal<>(),
                                                  SERVER_END = new Events.Signal<>();
    public static final Events.Signal<Peer> CLIENT_INIT = new Events.Signal<>(),
                                            CLIENT_READY = new Events.Signal<>(),
                                            CLIENT_COMPLETE = new Events.Signal<>(),
                                            CLIENT_END = new Events.Signal<>();
    private static volatile Peer client;
    private static final int MAX_CHANNELS = 256;
    private static final Identifier CONTROL = Identifier.parse("unity:configuration_channels");
    private static final ConfigurationTask.Type HELLO =
        new ConfigurationTask.Type("unity:configuration_handshake");
    private static final ConfigurationTask.Type SETUP =
        new ConfigurationTask.Type("unity:configuration_callbacks");
    private enum Stage { NEW, WAITING, READY, COMPLETING, CLOSED }
    public static final class State {
        private volatile Stage stage = Stage.NEW;
        private Thread completingThread;
        boolean canUse() {
            return stage == Stage.READY ||
                stage == Stage.COMPLETING && completingThread == Thread.currentThread();
        }
        private volatile Map<Identifier, String> remote = Map.of(), remoteCodecs = Map.of();
        private volatile Map<Key, Handler> receivers = Map.of();
        // Terminal ownership and all protocol transitions are guarded by this State.
        // Registry operations acquire ConfigurationNetwork.class before State, never in reverse.
        private boolean terminal, confirmed, begun, collected, draining;
        private final ArrayDeque<Channels> notices = new ArrayDeque<>();
        private volatile long handshakeStart;
        private List<ConfigurationTask> staging;
        private final Set<ConfigurationTask> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<ConfigurationTask.Type> tasks = new HashSet<>();
    }
    public static synchronized<T extends CustomPacketPayload> void
    type(PacketFlow direction, CustomPacketPayload.Type<T> type,
         StreamCodec<? super FriendlyByteBuf, T> codec, String version, boolean optional) {
        if (opened || !optional && requiredFrozen)
            throw new IllegalStateException("Configuration codec registration is closed");
        var id = type.id();
        Objects.requireNonNull(codec);
        if (id.getNamespace().equals("minecraft") || id.getNamespace().equals("unity") || version.isEmpty() ||
            version.length() > 128)
            throw new IllegalArgumentException("Reserved or invalid configuration channel " + id);
        var key = new Key(direction, id);
        var next = new HashMap<>(types);
        if (next.putIfAbsent(key, new Binding<>(type, codec, version, optional)) != null)
            throw new IllegalStateException("Duplicate configuration codec " + key);
        if (next.keySet().stream().filter(k -> k.direction == direction).count() > MAX_CHANNELS)
            throw new IllegalStateException("Too many configuration channels");
        types = Map.copyOf(next);
        if (!optional)
            required = true;
    }
    public static <T extends CustomPacketPayload> boolean receiver(Peer listener, PacketFlow direction,
                                                                   CustomPacketPayload.Type<T> type,
                                                                   Object original, Receiver<T> handler,
                                                                   boolean main) {
        var key = new Key(direction, type.id());
        var binding = new Handler(Objects.requireNonNull(original), Objects.requireNonNull(handler), main);
        var notify = new ArrayList<Peer>();
        synchronized (ConfigurationNetwork.class) {
            if (!types.containsKey(key))
                throw new IllegalStateException("Missing configuration codec " + key);
            if (listener == null) {
                var copy = new HashMap<>(globals);
                if (copy.putIfAbsent(key, binding) != null)
                    return false;
                globals = Map.copyOf(copy);
                for (var peer : active)
                    if (peer.flow() == direction && changeReceiver(peer, key, null, binding))
                        notify.add(peer);
            } else {
                if (listener.flow() != direction)
                    throw new IllegalStateException("Wrong-sided configuration receiver");
                if (!changeReceiver(listener, key, null, binding))
                    return false;
                notify.add(listener);
            }
        }
        notify.forEach(p -> advertise(p, 2));
        return true;
    }
    public static Object unregister(Peer listener, PacketFlow direction, Identifier id) {
        var key = new Key(direction, id);
        var notify = new ArrayList<Peer>();
        Handler removed;
        synchronized (ConfigurationNetwork.class) {
            if (listener == null) {
                var copy = new HashMap<>(globals);
                removed = copy.remove(key);
                if (removed == null)
                    return null;
                globals = Map.copyOf(copy);
                for (var peer : active)
                    if (peer.flow() == direction && changeReceiver(peer, key, removed, null))
                        notify.add(peer);
            } else {
                var state = listener.unity$state();
                synchronized (state) {
                    removed = state.receivers.get(key);
                    if (removed == null || !changeReceiver(listener, key, removed, null))
                        return null;
                }
                notify.add(listener);
            }
        }
        notify.forEach(p -> advertise(p, 2));
        return removed.original;
    }
    private static boolean changeReceiver(Peer peer, Key key, Handler expected, Handler replacement) {
        var s = peer.unity$state();
        synchronized (s) {
            if (s.terminal || s.receivers.get(key) != expected)
                return false;
            var copy = new HashMap<>(s.receivers);
            if (replacement == null)
                copy.remove(key);
            else
                copy.put(key, replacement);
            s.receivers = Map.copyOf(copy);
            return true;
        }
    }
    public static synchronized List<String> manifest() {
        requiredFrozen = true;
        return types.entrySet()
            .stream()
            .filter(e -> !e.getValue().optional)
            .map(e -> e.getKey().direction + " " + e.getKey().id + " " + e.getValue().version)
            .sorted()
            .toList();
    }
    static boolean enabled() {
        return !types.isEmpty();
    }
    static synchronized void agree(Connection c, boolean configuration) {
        if (configuration)
            negotiated.add(c);
        else
            negotiated.remove(c);
    }
    private static synchronized boolean supported(Connection c) {
        return c.isMemoryConnection() ? !types.isEmpty() : negotiated.contains(c);
    }
    private static boolean live(Peer p) {
        var c = p.unity$connection();
        if (!c.isConnected() || c.getPacketListener() != p)
            return false;
        var s = p.unity$state();
        synchronized (s) {
            return !s.terminal || s.stage == Stage.COMPLETING && s.completingThread == Thread.currentThread();
        }
    }
    public static Peer client() {
        return Objects.requireNonNull(client, "No configuration client");
    }
    private static Handler handler(Peer p, Key key) {
        return p.unity$state().receivers.get(key);
    }
    public static boolean declared(PacketFlow flow, Identifier id) {
        return types.containsKey(new Key(flow, id));
    }
    public static Set<Identifier> received(Peer peer, PacketFlow flow) {
        var ids = new HashSet<Identifier>();
        for (var key : types.keySet())
            if (key.direction == flow && (peer == null ? globals.get(key) : handler(peer, key)) != null)
                ids.add(key.id);
        return Set.copyOf(ids);
    }
    public static Set<Identifier> sendable(Peer p) {
        var result = new HashSet<Identifier>();
        var state = p.unity$state();
        synchronized (state) {
            if (!state.canUse())
                return Set.of();
            var direction = p.flow().getOpposite();
            state.remote.forEach((id, version) -> {
                var binding = types.get(new Key(direction, id));
                if (binding != null && binding.version.equals(version))
                    result.add(id);
            });
        }
        return Set.copyOf(result);
    }
    public static boolean hasChannel(Link listener, CustomPacketPayload.Type<?> type) {
        return listener instanceof Peer p ? canSend(p, type.id())
                                          : PlayNetwork.hasChannel(listener.unity$connection(), type);
    }
    public static boolean canSend(Peer p, Identifier id) {
        var b = types.get(new Key(p.flow().getOpposite(), id));
        var state = p.unity$state();
        synchronized (state) {
            return b != null && state.canUse() && b.version.equals(state.remote.get(id));
        }
    }
    public static Packet<?> packet(PacketFlow direction, CustomPacketPayload payload) {
        return direction == PacketFlow.CLIENTBOUND ? new ClientboundCustomPayloadPacket(payload)
                                                   : new ServerboundCustomPayloadPacket(payload);
    }
    public static void send(Peer p, CustomPacketPayload value, ChannelFutureListener callback) {
        if (!live(p) || !canSend(p, value.type().id()))
            throw new IllegalStateException("Unavailable configuration receiver " + value.type().id());
        p.unity$connection().send(packet(p.flow().getOpposite(), value), callback);
    }
    /** Vanilla task senders also take this path on the configuration listener. */
    public static void checkTaskPacket(Peer peer, Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom &&
            declared(PacketFlow.CLIENTBOUND, custom.payload().type().id()))
            checkPacket(peer, packet);
    }
    public static void checkPacket(Peer peer, Packet<?> packet) {
        if (!live(peer))
            throw new IllegalStateException("Configuration listener already closed");
        CustomPacketPayload value = packet instanceof ClientboundCustomPayloadPacket cp   ? cp.payload()
                                    : packet instanceof ServerboundCustomPayloadPacket sp ? sp.payload()
                                                                                          : null;
        if (value != null && types.containsKey(new Key(peer.flow().getOpposite(), value.type().id())) &&
            !canSend(peer, value.type().id()))
            throw new IllegalArgumentException("Peer has no configuration receiver " + value.type().id());
    }
    private record Channels(int action, Map<Identifier, String> entries, Map<Identifier, String> sends)
        implements CustomPacketPayload {
        static final Type<Channels> TYPE = new Type<>(CONTROL);
        public Type<Channels> type() {
            return TYPE;
        }
    }
    private static void advertise(Peer p, int action) {
        var s = p.unity$state();
        var flow = p.flow();
        if (!supported(p.unity$connection()) || !live(p))
            return;
        synchronized (s) {
            if (s.terminal || action == 2 && s.stage != Stage.READY)
                return;
            var entries = new TreeMap<Identifier, String>();
            var sends = new TreeMap<Identifier, String>();
            types.forEach((k, v) -> {
                if (k.direction == flow) {
                    if (s.receivers.containsKey(k))
                        entries.put(k.id, v.version);
                } else
                    sends.put(k.id, v.version);
            });
            s.notices.addLast(new Channels(action, Map.copyOf(entries), Map.copyOf(sends)));
            if (s.draining)
                return;
            s.draining = true;
        }
        // A single drainer preserves the order of snapshot publication across caller threads.
        try {
            while (true) {
                Channels next;
                synchronized (s) {
                    next = s.terminal ? null : s.notices.pollFirst();
                    if (next == null) {
                        s.draining = false;
                        return;
                    }
                }
                p.unity$connection().send(packet(flow.getOpposite(), next));
            }
        } catch (RuntimeException | Error failure) {
            synchronized (s) {
                s.notices.clear();
                s.draining = false;
            }
            throw failure;
        }
    }
    private static void intersect(Peer p, Channels c) {
        var direction = p.flow().getOpposite();
        for (var e : types.entrySet())
            if (e.getKey().direction == direction && !e.getValue().optional &&
                !e.getValue().version.equals(c.entries.get(e.getKey().id)))
                throw new IllegalStateException("配置阶段缺少必需接收器或版本不一致 " + e.getKey().id);
        for (var e : types.entrySet())
            if (e.getKey().direction == p.flow() && !e.getValue().optional &&
                !e.getValue().version.equals(c.sends.get(e.getKey().id)))
                throw new IllegalStateException("配置阶段缺少必需发送通道或版本不一致 " + e.getKey().id);
        p.unity$state().remote = c.entries;
        p.unity$state().remoteCodecs = c.sends;
    }
    private static void channels(Peer peer, Channels message) {
        var s = peer.unity$state();
        if (!supported(peer.unity$connection()))
            throw new IllegalStateException("Unnegotiated configuration channel advertisement");
        var flow = peer.flow();
        synchronized (s) {
            if (s.terminal)
                return;
            boolean allowed = switch (message.action) {
                case 0 -> flow == PacketFlow.CLIENTBOUND &&s.stage == Stage.NEW;
                case 1 -> peer instanceof ServerPeer &&s.stage == Stage.WAITING;
                case 2 -> s.stage == Stage.READY;
                case 3 -> flow == PacketFlow.CLIENTBOUND &&s.stage == Stage.READY && !s.confirmed;
                case 4 -> peer instanceof ServerPeer &&s.stage == Stage.READY && !s.confirmed;
                default -> false;
            };
            if (!allowed)
                throw new IllegalStateException("Unexpected configuration channel advertisement");
            intersect(peer, message);
            s.stage = Stage.READY;
            if (message.action >= 3)
                s.confirmed = true;
        }
        switch (message.action) {
        case 0 -> advertise(peer, 1);
        case 1 ->
            onOwner(peer, () -> {
                collect((ServerPeer)peer, BEFORE);
                advertise(peer, 3);
            });
        case 3 ->
            onOwner(peer, () -> {
                CLIENT_READY.post(peer);
                advertise(peer, 4);
            });
        case 4 -> onOwner(peer, () -> advance((ServerPeer)peer, HELLO));
        default -> {
        }
        }
    }
    private static void onOwner(Peer p, Runnable action) {
        Runnable run = () -> {
            if (!live(p))
                return;
            try {
                action.run();
            } catch (RuntimeException e) {
                p.unity$connection().disconnect(Component.literal("Unity 配置任务失败 " + e));
            }
        };
        if (p.unity$executor().isSameThread())
            run.run();
        else
            p.unity$executor().execute(run);
    }
    public static void init(Peer p) {
        var state = p.unity$state();
        boolean localClient = p.flow() == PacketFlow.CLIENTBOUND;
        synchronized (ConfigurationNetwork.class) {
            synchronized (state) {
                if (state.terminal || !active.add(p))
                    return;
                state.receivers = globals;
                opened = true;
                if (localClient)
                    client = p;
            }
        }
        if (localClient) {
            CLIENT_INIT.post(p);
            if (!supported(p.unity$connection())) {
                synchronized (state) {
                    if (state.terminal)
                        return;
                    state.stage = Stage.READY;
                }
                CLIENT_READY.post(p);
            }
        }
    }
    private record Task(ConfigurationTask.Type type, Runnable run) implements ConfigurationTask {
        public void start(Consumer<Packet<?>> sender) {
            run.run();
        }
    }
    public static void start(ServerPeer p) {
        var s = p.unity$state();
        synchronized (s) {
            if (s.terminal || s.begun)
                return;
            s.begun = true;
        }
        if (!supported(p.unity$connection()) && BEFORE.empty())
            return;
        var q = p.unity$tasks();
        q.add(new Task(HELLO, () -> {
            if (supported(p.unity$connection())) {
                synchronized (s) {
                    if (s.terminal)
                        return;
                    s.handshakeStart = System.nanoTime();
                    s.stage = Stage.WAITING;
                }
                advertise(p, 0);
            } else {
                synchronized (s) {
                    if (s.terminal)
                        return;
                    s.stage = Stage.READY;
                }
                collect(p, BEFORE);
                advance(p, HELLO);
            }
        }));
    }
    public static void configure(ServerPeer p) {
        start(p);
        var s = p.unity$state();
        synchronized (s) {
            if (s.terminal || s.collected || CONFIGURE.empty())
                return;
            s.collected = true;
        }
        p.unity$tasks().add(new Task(SETUP, () -> {
            collect(p, CONFIGURE);
            advance(p, SETUP);
        }));
    }
    public static void tick(ServerPeer p) {
        var t = p.unity$current();
        if (t != null && t.type().equals(HELLO) && p.unity$state().handshakeStart != 0 &&
            System.nanoTime() - p.unity$state().handshakeStart >
                java.util.concurrent.TimeUnit.SECONDS.toNanos(60))
            p.unity$connection().disconnect(Component.literal("Unity 等待配置通道协商回复超时"));
    }
    private static void collect(ServerPeer p, Events.Signal<ServerPeer> event) {
        var state = p.unity$state();
        var custom = new ArrayList<ConfigurationTask>();
        synchronized (state) {
            if (state.terminal)
                return;
            state.staging = custom;
        }
        try {
            event.post(p);
        } finally {
            synchronized (state) {
                state.staging = null;
            }
        }
        if (!custom.isEmpty() && live(p)) {
            var queue = p.unity$tasks();
            var tail = List.copyOf(queue);
            queue.clear();
            queue.addAll(custom);
            queue.addAll(tail);
        }
    }
    public static void addTask(ServerPeer p, ConfigurationTask task) {
        var s = p.unity$state();
        var id = task.type();
        synchronized (s) {
            if (s.terminal || s.staging == null || id.id().startsWith("unity:") ||
                id.id().startsWith("minecraft:") || !s.tasks.add(id))
                throw new IllegalStateException("Invalid or duplicate configuration task " + id);
            s.owned.add(task);
            s.staging.add(task);
        }
    }
    public static void finish(ServerPeer p, ConfigurationTask.Type type) {
        if (!p.unity$executor().isSameThread()) {
            onOwner(p, () -> finish(p, type));
            return;
        }
        if (!live(p))
            return;
        var s = p.unity$state();
        var current = p.unity$current();
        synchronized (s) {
            if (s.terminal)
                return;
            if (!s.owned.contains(current))
                throw new IllegalStateException("Only a mod configuration task can be completed here");
        }
        advance(p, type);
    }
    private static void advance(ServerPeer p, ConfigurationTask.Type type) {
        if (!live(p))
            return;
        var task = p.unity$current();
        if (task == null || !type.equals(task.type()))
            throw new IllegalStateException("Configuration task is not current: " + type);
        p.unity$finish(type);
    }
    public static void complete(Peer p) {
        close(p, false);
    }
    public static void close(Peer p, boolean disconnect) {
        var s = p.unity$state();
        synchronized (s) {
            if (s.terminal)
                return;
            s.terminal = true;
            s.completingThread = disconnect ? null : Thread.currentThread();
            s.stage = disconnect ? Stage.CLOSED : Stage.COMPLETING;
            s.notices.clear();
        }
        try {
            // COMPLETE runs before vanilla changes protocols, with its original peer context available.
            if (!disconnect && p.flow() == PacketFlow.CLIENTBOUND)
                CLIENT_COMPLETE.post(p);
        } finally {
            synchronized (ConfigurationNetwork.class) {
                synchronized (s) {
                    reset(s);
                    active.remove(p);
                    if (disconnect)
                        negotiated.remove(p.unity$connection());
                    if (client == p)
                        client = null;
                }
            }
        }
        if (disconnect) {
            if (p.flow() == PacketFlow.CLIENTBOUND)
                CLIENT_END.post(p);
            else
                SERVER_END.post((ServerPeer)p);
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean dispatch(Object listener, CustomPacketPayload payload) {
        if (!(listener instanceof Peer p))
            return false;
        var id = payload.type().id();
        var key = new Key(p.flow(), id);
        if (!(payload instanceof Channels) && !types.containsKey(key))
            return false;
        var s = p.unity$state();
        Handler h;
        synchronized (s) {
            if (s.terminal)
                return true;
            h = s.receivers.get(key);
        }
        Runnable run = () -> {
            if (!live(p))
                return;
            try {
                if (payload instanceof Channels c)
                    channels(p, c);
                else {
                    synchronized (s) {
                        if (s.terminal)
                            return;
                        var binding = types.get(key);
                        if (s.stage != Stage.READY || h == null || binding == null ||
                            !binding.version.equals(s.remoteCodecs.get(id)))
                            throw new IllegalStateException("Unexpected configuration payload " + id);
                    }
                    ((Receiver)h.receiver).receive(payload, p);
                }
            } catch (RuntimeException e) {
                p.unity$connection().disconnect(
                    Component.literal("Unity 配置握手失败 " + id + " " + e.getMessage()));
            }
        };
        // Capability state follows wire order. Only game callbacks and task queue changes move to the owning
        // thread.
        if (!(payload instanceof Channels) && (h == null || h.main) && !p.unity$executor().isSameThread())
            p.unity$executor().execute(run);
        else
            run.run();
        return true;
    }
    private static void reset(State s) {
        s.terminal = true;
        s.stage = Stage.CLOSED;
        s.completingThread = null;
        s.confirmed = false;
        s.handshakeStart = 0;
        s.remote = Map.of();
        s.remoteCodecs = Map.of();
        s.receivers = Map.of();
        s.notices.clear();
        s.staging = null;
        s.owned.clear();
        s.tasks.clear();
    }
    public static synchronized void clear() {
        required = false;
        requiredFrozen = false;
        opened = false;
        types = Map.of();
        globals = Map.of();
        negotiated.clear();
        for (var p : active) {
            var s = p.unity$state();
            synchronized (s) {
                reset(s);
            }
        }
        active.clear();
        client = null;
        BEFORE.clear();
        CONFIGURE.clear();
        SERVER_END.clear();
        CLIENT_INIT.clear();
        CLIENT_READY.clear();
        CLIENT_COMPLETE.clear();
        CLIENT_END.clear();
    }
    private static Map<Identifier, String> readChannels(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_CHANNELS)
            throw new IllegalArgumentException("Invalid configuration channel count");
        var map = new HashMap<Identifier, String>();
        for (int i = 0; i < n; i++)
            if (map.putIfAbsent(buf.readIdentifier(), buf.readUtf(128)) != null)
                throw new IllegalArgumentException("Duplicate configuration channel");
        return Map.copyOf(map);
    }
    private static void writeChannels(FriendlyByteBuf buf, Map<Identifier, String> channels) {
        buf.writeVarInt(channels.size());
        channels.forEach((k, v) -> {
            buf.writeIdentifier(k);
            buf.writeUtf(v, 128);
        });
    }
    public static StreamCodec clientbound(StreamCodec vanilla) {
        return wrap(vanilla, PacketFlow.CLIENTBOUND);
    }
    public static StreamCodec serverbound(StreamCodec vanilla) {
        return wrap(vanilla, PacketFlow.SERVERBOUND);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static StreamCodec<FriendlyByteBuf, Object> wrap(StreamCodec fallback, PacketFlow flow) {
        int limit = flow == PacketFlow.SERVERBOUND ? 32767 : 1048576;
        return new StreamCodec<>() {
            public Object decode(FriendlyByteBuf b) {
                if (b instanceof RegistryFriendlyByteBuf || types.isEmpty())
                    return fallback.decode(b);
                int mark = b.readerIndex();
                var id = b.readIdentifier();
                var binding = types.get(new Key(flow, id));
                if (binding == null && !id.equals(CONTROL)) {
                    if (types.containsKey(new Key(flow.getOpposite(), id)))
                        throw new IllegalArgumentException("Wrong configuration packet direction " + id);
                    b.readerIndex(mark);
                    return fallback.decode(b);
                }
                if (b.readableBytes() > limit)
                    throw new IllegalArgumentException("Oversize configuration payload " + id);
                CustomPacketPayload value;
                if (id.equals(CONTROL)) {
                    int action = b.readUnsignedByte();
                    if (action > 4)
                        throw new IllegalArgumentException("Invalid configuration advertisement");
                    value = new Channels(action, readChannels(b), readChannels(b));
                } else
                    value = binding.codec.decode(b);
                if (!id.equals(value.type().id()) || b.isReadable())
                    throw new IllegalArgumentException("Invalid configuration payload " + id);
                return packet(flow, value);
            }
            public void encode(FriendlyByteBuf b, Object packet) {
                if (b instanceof RegistryFriendlyByteBuf || types.isEmpty()) {
                    fallback.encode(b, packet);
                    return;
                }
                var payload = packet instanceof ServerboundCustomPayloadPacket sp
                                  ? sp.payload()
                                  : ((ClientboundCustomPayloadPacket)packet).payload();
                var id = payload.type().id();
                Binding binding = types.get(new Key(flow, id));
                if (binding == null && !(payload instanceof Channels)) {
                    if (types.containsKey(new Key(flow.getOpposite(), id)))
                        throw new IllegalArgumentException("Wrong configuration packet direction " + id);
                    fallback.encode(b, packet);
                    return;
                }
                PayloadBuffers.write(b, id, limit, body -> {
                    if (payload instanceof Channels c) {
                        body.writeByte(c.action);
                        writeChannels(body, c.entries);
                        writeChannels(body, c.sends);
                    } else
                        binding.codec.encode(body, payload);
                });
            }
        };
    }
}
