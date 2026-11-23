package net.neoforged.neoforge.network.registration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import mc.unity.core.PlayNetwork;
import mc.unity.core.ConfigurationNetwork;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.*;
public final class PayloadRegistrar {
    private final String version;
    private final HandlerThread thread;
    private final boolean optional;
    public PayloadRegistrar(String version) { this(version, HandlerThread.MAIN, false); }
    private PayloadRegistrar(String version, HandlerThread thread, boolean optional) {
        if (version == null || version.isBlank())
            throw new IllegalArgumentException("Empty channel version");
        this.version = version;
        this.thread = Objects.requireNonNull(thread);
        this.optional = optional;
    }
    public PayloadRegistrar optional() { return new PayloadRegistrar(version, thread, true); }
    public PayloadRegistrar versioned(String v) { return new PayloadRegistrar(v, thread, optional); }
    public PayloadRegistrar executesOn(HandlerThread t) { return new PayloadRegistrar(version, t, optional); }
    public <T extends CustomPacketPayload>
        PayloadRegistrar playToClient(CustomPacketPayload.Type<T> t,
                                      StreamCodec<? super RegistryFriendlyByteBuf, T> c,
                                      IPayloadHandler<T> h) {
        return bind(t, c, h, PacketFlow.CLIENTBOUND);
    }
    public <T extends CustomPacketPayload>
        PayloadRegistrar playToServer(CustomPacketPayload.Type<T> t,
                                      StreamCodec<? super RegistryFriendlyByteBuf, T> c,
                                      IPayloadHandler<T> h) {
        return bind(t, c, h, PacketFlow.SERVERBOUND);
    }
    public <T extends CustomPacketPayload>
        PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> t,
                                           StreamCodec<? super RegistryFriendlyByteBuf, T> c,
                                           IPayloadHandler<T> h) {
        return bind(t, c, h, null);
    }
    public <T extends CustomPacketPayload>
        PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type,
                                           StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                           IPayloadHandler<T> server, IPayloadHandler<T> client) {
        return bind(type, codec, (payload, ctx) -> {
            if (ctx.flow() == PacketFlow.SERVERBOUND)
                server.handle(payload, ctx);
            else
                client.handle(payload, ctx);
        }, null);
    }
    private <T extends CustomPacketPayload>
        PayloadRegistrar bind(CustomPacketPayload.Type<T> t,
                              StreamCodec<? super RegistryFriendlyByteBuf, T> c, IPayloadHandler<T> h,
                              PacketFlow flow) {
        Objects.requireNonNull(h);
        PlayNetwork.register(
            t.id(), version, flow, t, (StreamCodec<RegistryFriendlyByteBuf, T>)c,
            (msg, ctx) -> h.handle(msg, new Context(ctx)), thread == HandlerThread.MAIN, optional);
        return this;
    }
    private record
        ClientHandler<T extends CustomPacketPayload>(IPayloadHandler<T> handler, HandlerThread thread) {}
    private static final Map<CustomPacketPayload.Type<?>, ClientHandler<?>> playClients =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<CustomPacketPayload.Type<?>> deferredPlay =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static void clearClients() {
        deferredPlay.clear();
        playClients.clear();
    }
    public <T extends CustomPacketPayload> PayloadRegistrar
    playToClient(CustomPacketPayload.Type<T> t, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        deferredPlay.add(t);
        PlayNetwork.register(t.id(), version, PacketFlow.CLIENTBOUND, t, codec.cast(), (v, ctx) -> {
            @SuppressWarnings("unchecked") var h = (ClientHandler<T>)playClients.get(t);
            if (h == null)
                throw new IllegalStateException("Missing client play handler " + t.id());
            Runnable run = () -> h.handler.handle(v, new Context(ctx));
            if (h.thread == HandlerThread.MAIN && !ctx.executor().isSameThread())
                ctx.executor().execute(run);
            else
                run.run();
        }, false, optional);
        return this;
    }
    public <T extends CustomPacketPayload>
        PayloadRegistrar commonToClient(CustomPacketPayload.Type<T> t,
                                        StreamCodec<? super FriendlyByteBuf, T> c, IPayloadHandler<T> h) {
        playToClient(t, c, h);
        return configurationToClient(t, c, h);
    }
    public <T extends CustomPacketPayload> PayloadRegistrar
    commonToClient(CustomPacketPayload.Type<T> t, StreamCodec<? super FriendlyByteBuf, T> c) {
        playToClient(t, c);
        return configurationToClient(t, c);
    }
    public <T extends CustomPacketPayload>
        PayloadRegistrar commonToServer(CustomPacketPayload.Type<T> t,
                                        StreamCodec<? super FriendlyByteBuf, T> c, IPayloadHandler<T> h) {
        playToServer(t, c, h);
        return configurationToServer(t, c, h);
    }
    public <T extends CustomPacketPayload>
        PayloadRegistrar commonBidirectional(CustomPacketPayload.Type<T> t,
                                             StreamCodec<? super FriendlyByteBuf, T> c,
                                             IPayloadHandler<T> server, IPayloadHandler<T> client) {
        if (client == null) {
            deferredPlay.add(t);
            PlayNetwork.register(t.id(), version, null, t, c.cast(), (v, ctx) -> {
                if (ctx.sender() != null) {
                    Runnable run = () -> server.handle(v, new Context(ctx));
                    if (thread == HandlerThread.MAIN && !ctx.executor().isSameThread())
                        ctx.executor().execute(run);
                    else
                        run.run();
                } else {
                    @SuppressWarnings("unchecked") var binding = (ClientHandler<T>)playClients.get(t);
                    if (binding == null)
                        throw new IllegalStateException("Missing client handler " + t.id());
                    Runnable run = () -> binding.handler.handle(v, new Context(ctx));
                    if (binding.thread == HandlerThread.MAIN && !ctx.executor().isSameThread())
                        ctx.executor().execute(run);
                    else
                        run.run();
                }
            }, false, optional);
        } else
            playBidirectional(t, c, server, client);
        return configurationBidirectional(t, c, server, client);
    }
    public <T extends CustomPacketPayload> PayloadRegistrar commonBidirectional(
        CustomPacketPayload.Type<T> t, StreamCodec<? super FriendlyByteBuf, T> c, IPayloadHandler<T> server) {
        return commonBidirectional(t, c, server, null);
    }
    private <T extends CustomPacketPayload>
        PayloadRegistrar configuration(CustomPacketPayload.Type<T> t,
                                       StreamCodec<? super FriendlyByteBuf, T> codec, PacketFlow direction,
                                       IPayloadHandler<T> handler) {
        ConfigurationNetwork.type(direction, t, codec, version, optional);
        if (handler != null)
            configReceiver(t, direction, thread, handler);
        return this;
    }
    private static <T extends CustomPacketPayload> void configReceiver(CustomPacketPayload.Type<T> t,
                                                                       PacketFlow direction,
                                                                       HandlerThread thread,
                                                                       IPayloadHandler<T> handler) {
        if (!ConfigurationNetwork.receiver(null, direction, t, handler,
                                           (value, p)
                                               -> handler.handle(value, new ConfigurationContext(p)),
                                           thread == HandlerThread.MAIN))
            throw new IllegalStateException("Duplicate configuration receiver " + t.id());
    }
    public static <T extends CustomPacketPayload> void
    configurationClientReceiver(CustomPacketPayload.Type<T> t, HandlerThread thread,
                                IPayloadHandler<T> handler) {
        boolean play = deferredPlay.contains(t);
        if (play && playClients.putIfAbsent(t, new ClientHandler<>(handler, thread)) != null)
            throw new IllegalStateException("Duplicate play receiver " + t.id());
        if (ConfigurationNetwork.declared(PacketFlow.CLIENTBOUND, t.id()))
            configReceiver(t, PacketFlow.CLIENTBOUND, thread, handler);
        else if (!play)
            throw new IllegalStateException("Undeclared client receiver " + t.id());
    }
    public <T extends CustomPacketPayload> PayloadRegistrar configurationToClient(
        CustomPacketPayload.Type<T> t, StreamCodec<? super FriendlyByteBuf, T> c, IPayloadHandler<T> h) {
        return configuration(t, c, PacketFlow.CLIENTBOUND, h);
    }
    public <T extends CustomPacketPayload> PayloadRegistrar
    configurationToClient(CustomPacketPayload.Type<T> t, StreamCodec<? super FriendlyByteBuf, T> c) {
        return configuration(t, c, PacketFlow.CLIENTBOUND, null);
    }
    public <T extends CustomPacketPayload> PayloadRegistrar configurationToServer(
        CustomPacketPayload.Type<T> t, StreamCodec<? super FriendlyByteBuf, T> c, IPayloadHandler<T> h) {
        return configuration(t, c, PacketFlow.SERVERBOUND, Objects.requireNonNull(h));
    }
    public <T extends CustomPacketPayload>
        PayloadRegistrar configurationBidirectional(CustomPacketPayload.Type<T> t,
                                                    StreamCodec<? super FriendlyByteBuf, T> c,
                                                    IPayloadHandler<T> server, IPayloadHandler<T> client) {
        configurationToServer(t, c, server);
        return configurationToClient(t, c, client);
    }
    public <T extends CustomPacketPayload> PayloadRegistrar configurationBidirectional(
        CustomPacketPayload.Type<T> t, StreamCodec<? super FriendlyByteBuf, T> c, IPayloadHandler<T> server) {
        return configurationBidirectional(t, c, server, null);
    }
    private record ConfigurationContext(ConfigurationNetwork.Peer peer) implements IPayloadContext {
        public Connection connection() { return peer.unity$connection(); }
        public net.neoforged.neoforge.common.extensions.ICommonPacketListener listener() {
            return (net.neoforged.neoforge.common.extensions.ICommonPacketListener)peer;
        }
        public Player player() {
            throw new UnsupportedOperationException("Players are unavailable during configuration");
        }
        public PacketFlow flow() { return peer.flow(); }
        public void reply(CustomPacketPayload p) { ConfigurationNetwork.send(peer, p, null); }
        public void disconnect(Component reason) { connection().disconnect(reason); }
        public CompletableFuture<Void> enqueueWork(Runnable work) {
            return enqueueWork(() -> {
                work.run();
                return null;
            });
        }
        public <V> CompletableFuture<V> enqueueWork(Supplier<V> work) {
            if (!peer.unity$executor().isSameThread())
                return peer.unity$executor().submit(work);
            try {
                return CompletableFuture.completedFuture(work.get());
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
            if (!(peer instanceof ConfigurationNetwork.ServerPeer server))
                throw new IllegalStateException("Only the server may finish configuration tasks");
            ConfigurationNetwork.finish(server, type);
        }
    }
    private record Context(PlayNetwork.Context ctx) implements IPayloadContext {
        public Connection connection() { return ctx.connection(); }
        public Player player() { return ctx.sender() != null ? ctx.sender() : PlayNetwork.localPlayer(); }
        public PacketFlow flow() {
            return ctx.sender() == null ? PacketFlow.CLIENTBOUND : PacketFlow.SERVERBOUND;
        }
        public void reply(CustomPacketPayload p) {
            if (flow() == PacketFlow.CLIENTBOUND)
                connection().send(new ServerboundCustomPayloadPacket(p));
            else
                connection().send(new ClientboundCustomPayloadPacket(p));
        }
        public void disconnect(Component reason) { connection().disconnect(reason); }
        public CompletableFuture<Void> enqueueWork(Runnable w) { return ctx.enqueue(w); }
        public <T> CompletableFuture<T> enqueueWork(Supplier<T> w) {
            return ctx.executor().isSameThread() ? CompletableFuture.completedFuture(w.get())
                                                 : ctx.executor().submit(w);
        }
    }
}
