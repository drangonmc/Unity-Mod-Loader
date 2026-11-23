package net.neoforged.fml.config;

import java.util.*;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import mc.unity.core.Events;
import mc.unity.core.PlayNetwork;

/** The configuration protocol delivers the first snapshot before the client enters the level. */
public final class ConfigSync {
    private static final CustomPacketPayload.Type<Snapshot> ID =
        new CustomPacketPayload.Type<>(Identifier.parse("unity:server_configs"));
    static final int MAX_TEXT = 131072;
    private static final int MAX_TOTAL = 524288, MAX_FILES = 128;
    private static Map<String, ModConfig> specs = Map.of();
    private static volatile Snapshot current;
    private static Connection remote;
    private static Snapshot applied;
    private record Snapshot(Map<String, String> files) implements CustomPacketPayload {
        public Type<Snapshot> type() { return ID; }
    }
    static synchronized void register(Set<ModConfig> configs) {
        if (configs.isEmpty())
            return;
        var byId = new TreeMap<String, ModConfig>();
        for (var c : configs)
            byId.put(c.getModId() + "/" + c.getFileName().replace('\\', '/'), c);
        if (byId.size() > MAX_FILES)
            throw new IllegalArgumentException("Too many SERVER configs");
        specs = Collections.unmodifiableMap(byId);
        var codec = new StreamCodec<RegistryFriendlyByteBuf, Snapshot>() {
            public void encode(RegistryFriendlyByteBuf buf, Snapshot s) { write(buf, s); }
            public Snapshot decode(RegistryFriendlyByteBuf buf) { return read(buf); }
        };
        PlayNetwork.register(ID.id(), "1 " + byId.keySet(), PacketFlow.CLIENTBOUND, ID, codec,
                             (s, ctx) -> receive(s, ctx.connection()), true);
        // Cover a reload between startConfiguration and placeNewPlayer. Equal snapshots are idempotent.
        Events.PLAYER_LOGIN.listen(p -> {
            if (current != null && !((mc.unity.core.ConfigurationNetwork.Link)p.connection)
                                        .unity$connection()
                                        .isMemoryConnection())
                p.connection.send(new ClientboundCustomPayloadPacket(current));
        });
    }
    static synchronized void checkReload(ModConfig config, CommentedConfig next) {
        var currentSnapshot = current;
        if (currentSnapshot == null || config.getType() != ModConfig.Type.SERVER)
            return;
        var writer = new java.io.StringWriter();
        new com.electronwill.nightconfig.toml.TomlWriter().write(next, writer);
        String candidate = writer.toString(),
               key = config.getModId() + "/" + config.getFileName().replace('\\', '/');
        int bytes = 5;
        for (var e : currentSnapshot.files.entrySet()) {
            String text = e.getKey().equals(key) ? candidate : e.getValue();
            bytes += text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length +
                     e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 10;
        }
        if (candidate.length() > MAX_TEXT || bytes > MAX_TOTAL)
            throw new IllegalArgumentException("SERVER config exceeds packet limit: " + key);
    }
    static synchronized void snapshot(MinecraftServer server) {
        if (specs.isEmpty())
            return;
        var files = new TreeMap<String, String>();
        int bytes = 0;
        for (var e : specs.entrySet()) {
            String text = e.getValue().snapshot();
            if (text.length() > MAX_TEXT || e.getKey().length() > 512)
                throw new IllegalStateException("SERVER config exceeds packet limit: " + e.getKey());
            bytes += text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length +
                     e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 10;
            if (bytes > MAX_TOTAL - 5)
                throw new IllegalStateException("SERVER configs exceed packet limit");
            files.put(e.getKey(), text);
        }
        var next = new Snapshot(Map.copyOf(files));
        if (next.equals(current))
            return;
        current = next;
        if (server.getPlayerList() != null)
            for (var p : server.getPlayerList().getPlayers())
                if (!((mc.unity.core.ConfigurationNetwork.Link)p.connection)
                         .unity$connection()
                         .isMemoryConnection())
                    p.connection.send(new ClientboundCustomPayloadPacket(next));
    }
    public static void initial(Connection c) {
        Snapshot s = current;
        if (s != null && !c.isMemoryConnection())
            c.send(new ClientboundCustomPayloadPacket(s));
    }
    private static void write(FriendlyByteBuf b, Snapshot s) {
        b.writeVarInt(s.files.size());
        s.files.forEach((id, text) -> {
            b.writeUtf(id, 512);
            b.writeUtf(text, MAX_TEXT);
        });
    }
    private static Snapshot read(FriendlyByteBuf b) {
        if (b.readableBytes() > MAX_TOTAL)
            throw new IllegalArgumentException("Oversized SERVER config snapshot");
        int n = b.readVarInt();
        if (n < 1 || n > MAX_FILES)
            throw new IllegalArgumentException("Invalid SERVER config count");
        var files = new HashMap<String, String>();
        for (int i = 0; i < n; i++)
            if (files.putIfAbsent(b.readUtf(512), b.readUtf(MAX_TEXT)) != null)
                throw new IllegalArgumentException("Duplicate SERVER config");
        if (b.isReadable())
            throw new IllegalArgumentException("Trailing SERVER config data");
        return new Snapshot(Map.copyOf(files));
    }
    public static StreamCodec<FriendlyByteBuf, ClientboundCustomPayloadPacket>
    configurationCodec(StreamCodec<FriendlyByteBuf, ClientboundCustomPayloadPacket> vanilla) {
        return new StreamCodec<>() {
            public void encode(FriendlyByteBuf b, ClientboundCustomPayloadPacket packet) {
                if (packet.payload() instanceof Snapshot s) {
                    b.writeIdentifier(ID.id());
                    write(b, s);
                } else
                    vanilla.encode(b, packet);
            }
            public ClientboundCustomPayloadPacket decode(FriendlyByteBuf b) {
                int start = b.readerIndex();
                if (b.readIdentifier().equals(ID.id()))
                    return new ClientboundCustomPayloadPacket(read(b));
                b.readerIndex(start);
                return vanilla.decode(b);
            }
        };
    }
    public static synchronized boolean receive(CustomPacketPayload payload, Connection c) {
        if (!(payload instanceof Snapshot s))
            return false;
        if (c.isMemoryConnection() || !c.isConnected())
            return true;
        if (current != null || !s.files.keySet().equals(specs.keySet()) || remote != null && remote != c)
            throw new IllegalArgumentException("Unexpected SERVER configuration");
        if (s.equals(applied))
            return true;
        var staged = new LinkedHashMap<ModConfig, CommentedConfig>();
        specs.forEach((id, config) -> {
            var text = s.files.get(id);
            if (applied == null || !text.equals(applied.files.get(id)))
                staged.put(config, config.parse(text));
        });
        boolean first = remote == null;
        remote = c;
        staged.forEach((config, values) -> config.accept(values, true));
        applied = s;
        staged.keySet().forEach(config -> config.notifyLoaded(first));
        return true;
    }
    public static synchronized void disconnected(Connection c) {
        if (remote != c)
            return;
        remote = null;
        applied = null;
        specs.values().forEach(ModConfig::unload);
    }
    static synchronized void endWorld() { current = null; }
    static synchronized void clear() {
        current = null;
        applied = null;
        remote = null;
        specs = Map.of();
    }
}
