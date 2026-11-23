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
    private record Binding<T extends CustomPacketPayload>(Identifier channel, String version, PacketFlow flow,
                                                          CustomPacketPayload.Type<T> type,
                                                          StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                          BiConsumer<T, Context> handler, boolean main,
                                                          boolean optional) {}
    private static final Map<Identifier, Binding<?>> registrations = new LinkedHashMap<>();
    private static volatile Map<Identifier, Binding<?>> entries = Map.of();
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
        var binding = entries.get(payload.type().id());
        if (binding != null && binding.optional && !((Peer)listener).hasChannel(payload.type()))
            throw new IllegalStateException("Peer did not advertise optional payload " + payload.type().id());
    }
    public static void sendToServer(CustomPacketPayload payload) {
        var transport = client;
        if (transport == null)
            throw new IllegalStateException("No client transport");
        var connection = transport.get();
        var binding = entries.get(payload.type().id());
        if (binding != null && binding.optional && !hasChannel(connection, payload.type()))
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
        if (frozen || type.id().getNamespace().equals("minecraft") ||
            registrations.putIfAbsent(type.id(), new Binding<>(channel, version, flow, type, codec, handler,
                                                               main, optional)) != null)
            throw new IllegalStateException("Closed or conflicting play registration " + type.id());
    }
    public static synchronized List<String> manifest() {
        if (!frozen) {
            entries = Map.copyOf(registrations);
            var tokens = new TreeMap<String, Identifier>();
            for (var b : entries.values())
                if (b.optional)
                    tokens.put(token(b), b.type.id());
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
    private static final Map<Connection, Set<Identifier>> agreed =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Map<String, Identifier> optionalTokens = Map.of();
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
    static Set<String> offers() {
        return optionalTokens.keySet();
    }
    static void agree(Connection c, Collection<String> tokens) {
        var ids = new HashSet<Identifier>();
        for (var token : tokens) {
            var id = optionalTokens.get(token);
            if (id != null)
                ids.add(id);
        }
        agreed.put(c, Set.copyOf(ids));
    }
    public static boolean hasChannel(Connection c, CustomPacketPayload.Type<?> type) {
        var binding = entries.get(type.id());
        return binding != null && (!binding.optional || c.isMemoryConnection() ||
                                   agreed.getOrDefault(c, Set.of()).contains(type.id()));
    }
    public static void check(Identifier channel, CustomPacketPayload payload, PacketFlow direction) {
        var b = entries.get(payload.type().id());
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
                Binding b = entries.get(id);
                if (b == null) {
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
                Binding b = entries.get(payload.type().id());
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
        Binding b = entries.get(payload.type().id());
        if (b == null)
            return false;
        var flow = sender == null ? PacketFlow.CLIENTBOUND : PacketFlow.SERVERBOUND;
        check(b.channel, payload, flow);
        if (!hasChannel(connection, payload.type()))
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
