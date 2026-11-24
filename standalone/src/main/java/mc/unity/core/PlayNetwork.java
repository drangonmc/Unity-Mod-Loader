package mc.unity.core;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.thread.BlockableEventLoop;

/** Gameplay-only codecs. Configuration, vanilla channels, and unknown IDs keep their native codecs. */
public final class PlayNetwork {
    public record Context(Connection connection, ServerPlayer sender, BlockableEventLoop<?> executor) {
        public CompletableFuture<Void> enqueue(Runnable r) {
            if (executor.isSameThread()) {
                r.run();
                return CompletableFuture.completedFuture(null);
            }
            return executor.submit(r);
        }
    }
    private record Key(Identifier id, PacketFlow flow) {}
    /** A codec may be present on both sides while only one side installs a receiver. */
    public static final class Endpoint<T extends CustomPacketPayload> implements BiConsumer<T, Context> {
        private volatile BiConsumer<T, Context> receiver;
        private Object original;
        private boolean advertised;
        public synchronized boolean bind(Object identity, BiConsumer<T, Context> handler) {
            if (original != null)
                return false;
            if (advertised)
                throw new IllegalStateException("Play receiver registration after login");
            original = Objects.requireNonNull(identity);
            receiver = Objects.requireNonNull(handler);
            return true;
        }
        public synchronized Object unbind() {
            if (advertised)
                throw new IllegalStateException("Play receiver removal after login");
            Object old = original;
            original = null;
            receiver = null;
            return old;
        }
        private synchronized boolean offer() {
            advertised = true;
            return receiver != null;
        }
        public void accept(T value, Context context) {
            var registered = receiver;
            if (registered == null)
                throw new IllegalArgumentException("No Play receiver for " + value.type().id());
            registered.accept(value, context);
        }
    }
    private record Binding<T extends CustomPacketPayload>(Identifier channel, String version, PacketFlow flow,
                                                          CustomPacketPayload.Type<T> type,
                                                          StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                          BiConsumer<T, Context> handler, boolean main,
                                                          boolean optional) {}
    private static final Map<Key, Binding<?>> registrations = new LinkedHashMap<>();
    private static volatile Map<Key, Binding<?>> entries = Map.of();
    private static boolean frozen;
    private static volatile Supplier<Connection> client;
    private static volatile Supplier<net.minecraft.world.entity.player.Player> localPlayer;
    public static void bindPlayer(Supplier<net.minecraft.world.entity.player.Player> supplier) {
        localPlayer = supplier;
    }
    public static net.minecraft.world.entity.player.Player localPlayer() {
        return Objects.requireNonNull(localPlayer.get(), "No active player");
    }
    public static void bindClient(Supplier<Connection> connection) {
        client = connection;
    }
    public interface Peer {
        boolean hasChannel(CustomPacketPayload.Type<?> type);
    }
    public static void checkPeer(Object listener, CustomPacketPayload payload) {
        var binding = lookup(payload.type().id(),
                             ((net.minecraft.network.PacketListener)listener).flow().getOpposite());
        if (binding != null && binding.optional && !((Peer)listener).hasChannel(payload.type()))
            throw new IllegalStateException("Peer did not advertise optional payload " + payload.type().id());
    }
    public static void sendToServer(CustomPacketPayload payload) {
        var transport = client;
        if (transport == null)
            throw new IllegalStateException("No client transport");
        var connection = transport.get();
        var binding = lookup(payload.type().id(), PacketFlow.SERVERBOUND);
        if (binding != null && binding.optional && !allowed(connection, binding))
            throw new IllegalStateException("Server did not advertise optional payload " +
                                            payload.type().id());
        connection.send(new ServerboundCustomPayloadPacket(payload));
    }
    public static synchronized<T extends CustomPacketPayload> void
    add(Identifier channel, int version, PacketFlow flow, CustomPacketPayload.Type<T> type,
        StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, Context> handler, boolean main) {
        register(channel, Integer.toString(version), flow, type, codec, handler, main);
    }
    public static synchronized<T extends CustomPacketPayload> void register(
        Identifier channel, String version, PacketFlow flow, CustomPacketPayload.Type<T> type,
        StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, Context> handler, boolean main) {
        register(channel, version, flow, type, codec, handler, main, false);
    }
    public static synchronized<T extends CustomPacketPayload> void register(
        Identifier channel, String version, PacketFlow flow, CustomPacketPayload.Type<T> type,
        StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, Context> handler, boolean main,
        boolean optional) {
        put(new Binding<>(channel, version, flow, type, codec, handler, main, optional));
    }
    private static synchronized void put(Binding<?> binding) {
        var id = binding.type.id();
        var flow = binding.flow;
        if (frozen || id.getNamespace().equals("minecraft") || registrations.containsKey(new Key(id, null)) ||
            flow == null && (registrations.containsKey(new Key(id, PacketFlow.SERVERBOUND)) ||
                             registrations.containsKey(new Key(id, PacketFlow.CLIENTBOUND))) ||
            registrations.putIfAbsent(new Key(id, flow), binding) != null)
            throw new IllegalStateException("Closed or conflicting play registration " + id);
    }
    private static Binding<?> lookup(Identifier id, PacketFlow flow) {
        var view = entries;
        var match = view.get(new Key(id, flow));
        return match == null ? view.get(new Key(id, null)) : match;
    }
    public static <T extends CustomPacketPayload> void
    endpoint(PacketFlow flow, CustomPacketPayload.Type<T> type,
             StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        put(new Binding<>(type.id(), "fabric/1", flow, type, codec.cast(), new Endpoint<T>(), true, true));
    }
    @SuppressWarnings("unchecked")
    public static synchronized<T extends CustomPacketPayload> Endpoint<T>
    receiver(PacketFlow flow, CustomPacketPayload.Type<T> type) {
        var b = registrations.get(new Key(type.id(), flow));
        if (b == null || !(b.handler instanceof Endpoint<?> receiver))
            throw new IllegalArgumentException("Undeclared Play codec " + flow + " " + type.id());
        return (Endpoint<T>)receiver;
    }
    public static synchronized Set<Identifier> receivers(PacketFlow flow) {
        var ids = new HashSet<Identifier>();
        registrations.forEach((k, b) -> {
            if (k.flow == flow && b.handler instanceof Endpoint<?> endpoint && endpoint.receiver != null)
                ids.add(k.id);
        });
        return Set.copyOf(ids);
    }
    public static synchronized List<String> manifest() {
        if (!frozen) {
            entries = Map.copyOf(registrations);
            var tokens = new TreeMap<String, Key>();
            for (var b : entries.values())
                if (b.optional)
                    tokens.put(token(b), new Key(b.type.id(), b.flow));
            if (tokens.size() > 256)
                throw new IllegalStateException("Optional channel limit exceeded");
            optionalTokens = Map.copyOf(tokens);
        }
        frozen = true;
        return entries.values()
            .stream()
            .filter(b -> !b.optional)
            .map(b -> b.channel + " " + b.version + " " + b.flow + " " + b.type.id())
            .sorted()
            .toList();
    }
    private static final Map<Connection, Set<Key>> agreed = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Map<String, Key> optionalTokens = Map.of();
    private static String token(Binding<?> b) {
        try {
            String description = b.type.id() + " " + b.channel + " " + b.version + " " + b.flow;
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(
                description.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
    static Set<String> offers(PacketFlow incoming) {
        var tokens = new HashSet<String>();
        optionalTokens.forEach((token, key) -> {
            if (key.flow != incoming || !(entries.get(key).handler instanceof Endpoint<?> e) || e.offer())
                tokens.add(token);
        });
        return Set.copyOf(tokens);
    }
    static void agree(Connection c, Collection<String> tokens) {
        var ids = new HashSet<Key>();
        for (var token : tokens) {
            var id = optionalTokens.get(token);
            if (id != null)
                ids.add(id);
        }
        agreed.put(c, Set.copyOf(ids));
    }
    public static boolean hasChannel(Connection c, CustomPacketPayload.Type<?> type) {
        var binding = lookup(type.id(), c.getSending());
        if (binding == null)
            binding = lookup(type.id(), c.getReceiving());
        return binding != null && allowed(c, binding);
    }
    private static boolean allowed(Connection c, Binding<?> b) {
        if (!b.optional)
            return true;
        if (c.isMemoryConnection())
            return !(b.handler instanceof Endpoint<?> e) || e.receiver != null;
        return agreed.getOrDefault(c, Set.of()).contains(new Key(b.type.id(), b.flow));
    }
    public static boolean canSend(Connection connection, PacketFlow direction, Identifier id) {
        var b = lookup(id, direction);
        return b != null && b.handler instanceof Endpoint<?> && allowed(connection, b);
    }
    public static Set<Identifier> sendable(Connection connection, PacketFlow direction) {
        var ids = new HashSet<Identifier>();
        entries.forEach((k, b) -> {
            if (k.flow == direction && b.handler instanceof Endpoint<?> && allowed(connection, b))
                ids.add(k.id);
        });
        return Set.copyOf(ids);
    }
    public static void check(Identifier channel, CustomPacketPayload payload, PacketFlow direction) {
        var b = lookup(payload.type().id(), direction);
        if (b == null || !b.channel.equals(channel) || b.flow != null && b.flow != direction)
            throw new IllegalArgumentException("Unregistered message " + payload.type().id());
    }
    public static synchronized void clear() {
        entries = Map.of();
        registrations.clear();
        agreed.clear();
        optionalTokens = Map.of();
        frozen = false;
        client = null;
        localPlayer = null;
    }
    public static StreamCodec serverbound(StreamCodec vanilla) {
        return wrap(vanilla, PacketFlow.SERVERBOUND);
    }
    public static StreamCodec clientbound(StreamCodec vanilla) {
        return wrap(vanilla, PacketFlow.CLIENTBOUND);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static StreamCodec<FriendlyByteBuf, Object> wrap(StreamCodec fallback, PacketFlow flow) {
        int limit = flow == PacketFlow.SERVERBOUND ? 32767 : 1048576;
        return new StreamCodec<>() {
            public Object decode(FriendlyByteBuf buf) {
                if (!(buf instanceof RegistryFriendlyByteBuf game) || entries.isEmpty())
                    return fallback.decode(buf);
                int mark = buf.readerIndex();
                Identifier id = buf.readIdentifier();
                Binding b = lookup(id, flow);
                if (b == null) {
                    if (lookup(id, flow.getOpposite()) != null)
                        throw new IllegalArgumentException("Wrong Play payload direction " + id);
                    buf.readerIndex(mark);
                    return fallback.decode(buf);
                }
                if (b.flow != null && b.flow != flow || buf.readableBytes() > limit)
                    throw new IllegalArgumentException("Invalid play payload " + id);
                var payload = (CustomPacketPayload)b.codec.decode(game);
                if (!payload.type().id().equals(id) || buf.isReadable())
                    throw new IllegalArgumentException("Invalid play codec " + id);
                return flow == PacketFlow.SERVERBOUND ? new ServerboundCustomPayloadPacket(payload)
                                                      : new ClientboundCustomPayloadPacket(payload);
            }
            public void encode(FriendlyByteBuf buf, Object packet) {
                CustomPacketPayload payload = packet instanceof ServerboundCustomPayloadPacket s
                                                  ? s.payload()
                                                  : ((ClientboundCustomPayloadPacket)packet).payload();
                Binding b = lookup(payload.type().id(), flow);
                if (b == null && lookup(payload.type().id(), flow.getOpposite()) != null &&
                    buf instanceof RegistryFriendlyByteBuf)
                    throw new IllegalArgumentException("Wrong Play payload direction " + payload.type().id());
                if (b == null || !(buf instanceof RegistryFriendlyByteBuf game)) {
                    fallback.encode(buf, packet);
                    return;
                }
                check(b.channel, payload, flow);
                PayloadBuffers.write(buf, payload.type().id(), limit,
                                     body -> b.codec.encode((RegistryFriendlyByteBuf)body, payload));
            }
        };
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean dispatch(CustomPacketPayload payload, Connection connection, ServerPlayer sender,
                                   BlockableEventLoop<?> executor) {
        var flow = sender == null ? PacketFlow.CLIENTBOUND : PacketFlow.SERVERBOUND;
        Binding b = lookup(payload.type().id(), flow);
        if (b == null)
            return false;
        check(b.channel, payload, flow);
        if (!allowed(connection, b))
            throw new IllegalArgumentException("Unnegotiated channel " + payload.type().id());
        var ctx = new Context(connection, sender, executor);
        Runnable run = () -> {
            if (!connection.isConnected())
                return;
            b.handler.accept(payload, ctx);
            if (Boolean.getBoolean("mc.unity.contentQA") || Boolean.getBoolean("mc.unity.standaloneQA"))
                System.out.println("[Unity] play " + flow + " " + b.type.id() + " " +
                                   Thread.currentThread().getName());
        };
        if (b.main && !executor.isSameThread())
            executor.execute(run);
        else
            run.run();
        return true;
    }
}
