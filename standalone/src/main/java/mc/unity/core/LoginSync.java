package mc.unity.core;
import java.util.*;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.login.custom.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
public final class LoginSync {
    public static final int TX = 0x554e4954;
    public static final int TX_DIFF = TX + 1;
    private static final long DIAGNOSTIC_TIMEOUT = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    public static boolean ownsTransaction(int id) {
        return id == TX || id == TX_DIFF;
    }
    private static final Map<Connection, Pending> waiting = new IdentityHashMap<>();
    private static final Set<Connection> matched = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Connection> verified = Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Pending {
        final Runnable resume;
        boolean consumed, diagnosing;
        long diagnosticDeadline;
        Offer offer;
        Pending(Runnable resume) {
            this.resume = resume;
        }
        synchronized boolean consume() {
            if (consumed)
                return false;
            consumed = true;
            return true;
        }
    }
    public interface Resume {
        void unity$resume();
    }
    public static synchronized void clear() {
        waiting.clear();
        verified.clear();
        matched.clear();
    }
    public static synchronized void forget(Connection c) {
        waiting.remove(c);
        matched.remove(c);
        verified.remove(c);
    }
    private static final int MAX_PLAY = 256;
    private static final String LEGACY_CONFIGURATION = "0bda8cf7e29e4514b67cf34451c991bf";
    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("[0-9a-f]{32}");
    record Offer(Set<String> play, boolean configuration) {
        Offer {
            play = Set.copyOf(play);
            if (play.size() > MAX_PLAY)
                throw new IllegalArgumentException("Too many Play channels");
        }
        static Offer local(net.minecraft.network.protocol.PacketFlow incoming) {
            return new Offer(PlayNetwork.offers(incoming), ConfigurationNetwork.enabled());
        }
        boolean extended() {
            return configuration && play.size() == MAX_PLAY || play.contains(LEGACY_CONFIGURATION);
        }
        String query(String digest) {
            var tokens = new TreeSet<>(play);
            if (extended())
                return "registry/" + digest + "/" + String.join(".", tokens) + "/cfg" +
                    (configuration ? "1" : "0");
            if (configuration)
                tokens.add(LEGACY_CONFIGURATION);
            return "registry/" + digest + (tokens.isEmpty() ? "" : "/" + String.join(".", tokens));
        }
    }
    public record Reply(String registry, Set<String> optional, boolean configuration, boolean extended)
        implements CustomQueryAnswerPayload {
        public Reply {
            optional = Set.copyOf(optional);
        }
        public Reply(String registry, Set<String> legacy) {
            this(registry, withoutMarker(legacy), legacy.contains(LEGACY_CONFIGURATION), false);
        }
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(registry, 64);
            var fields = new TreeSet<>(optional);
            if (configuration && !extended)
                fields.add(LEGACY_CONFIGURATION);
            if (fields.size() > MAX_PLAY)
                throw new IllegalArgumentException("Too many login channels");
            if (extended || !fields.isEmpty()) {
                buf.writeVarInt(fields.size());
                for (String token : fields)
                    buf.writeUtf(token, 32);
                if (extended)
                    buf.writeByte(configuration ? 1 : 0);
            }
        }
    }
    private static Set<String> withoutMarker(Set<String> legacy) {
        var values = new HashSet<>(legacy);
        values.remove(LEGACY_CONFIGURATION);
        return values;
    }
    public static CustomQueryAnswerPayload readReply(FriendlyByteBuf buf) {
        return readReply(TX, buf);
    }
    public static CustomQueryAnswerPayload readReply(int transaction, FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            if (buf.isReadable())
                throw new IllegalArgumentException("Unexpected registry reply data");
            return null;
        }
        String registry = buf.readUtf(64);
        if (transaction == TX_DIFF) {
            if (!registry.equals(LoginInventory.MAGIC))
                throw new IllegalArgumentException("Invalid diagnostic reply");
            return LoginInventory.read(buf);
        }
        if (!registry.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid registry reply");
        int n = buf.isReadable() ? buf.readVarInt() : 0;
        if (n < 0 || n > MAX_PLAY)
            throw new IllegalArgumentException("Invalid optional channel count");
        var fields = new HashSet<String>();
        for (int i = 0; i < n; i++) {
            String token = buf.readUtf(32);
            if (!TOKEN.matcher(token).matches() || !fields.add(token))
                throw new IllegalArgumentException("Invalid optional channel");
        }
        if (!buf.isReadable())
            return new Reply(registry, fields);
        if (buf.readableBytes() != 1)
            throw new IllegalArgumentException("Trailing login data");
        int flags = buf.readUnsignedByte();
        if (flags > 1)
            throw new IllegalArgumentException("Unknown login capability flag");
        return new Reply(registry, fields, flags == 1, true);
    }
    static Reply negotiate(String path, String digest, Offer local) {
        String[] parts = path.split("/", -1);
        boolean extended = parts.length == 4;
        if (parts.length < 2 || parts.length > 4 || !parts[0].equals("registry") || !parts[1].equals(digest))
            return null;
        if (extended && !parts[3].equals("cfg0") && !parts[3].equals("cfg1"))
            return null;
        var fields = new HashSet<String>();
        if (parts.length > 2 && !parts[2].isEmpty()) {
            String[] list = parts[2].split("\\.", -1);
            if (list.length > MAX_PLAY)
                return null;
            for (String t : list)
                if (!TOKEN.matcher(t).matches() || !fields.add(t))
                    return null;
        }
        boolean config = extended ? parts[3].equals("cfg1") : fields.remove(LEGACY_CONFIGURATION);
        fields.retainAll(local.play);
        return new Reply(digest, fields, config && local.configuration, extended);
    }
    public static synchronized boolean ready(Connection connection, Resume listener,
                                             net.minecraft.server.MinecraftServer server) {
        if (connection.isMemoryConnection() || verified.contains(connection))
            return true;
        if (waiting.containsKey(connection))
            return false;
        String digest = Content.digest();
        var offer = Offer.local(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        if (digest.isEmpty() && offer.play.isEmpty() && !offer.configuration)
            return true;
        var entry = new Pending(() -> server.execute(() -> {
            synchronized (LoginSync.class) {
                if (waiting.remove(connection) == null || !connection.isConnected())
                    return;
                verified.add(connection);
            }
            listener.unity$resume();
        }));
        entry.offer = offer;
        waiting.put(connection, entry);
        connection.send(new ClientboundCustomQueryPacket(
            TX,
            new DiscardedQueryPayload(Identifier.fromNamespaceAndPath("unity", offer.query(wireDigest())))));
        return false;
    }
    public static synchronized boolean answer(ServerLoginPacketListenerImpl listener, Connection connection,
                                              ServerboundCustomQueryAnswerPacket packet) {
        int transaction = packet.transactionId();
        if (!ownsTransaction(transaction))
            return false;
        var entry = waiting.get(connection);
        if (entry == null)
            return true;
        if (transaction == TX_DIFF) {
            if (!entry.diagnosing)
                return true;
            String message = packet.payload() instanceof LoginInventory peer
                                 ? Content.inventory.difference(peer)
                                 : "Unity: content registry mismatch";
            forget(connection);
            listener.disconnect(Component.literal(message));
            return true;
        }
        if (!entry.consume())
            return true;
        if (packet.payload() == null && Content.digest().isEmpty()) {
            entry.resume.run();
            return true;
        }
        var reply = packet.payload() instanceof Reply decoded ? decoded : null;
        if (reply == null || !wireDigest().equals(reply.registry()) ||
            (reply.extended() != entry.offer.extended() ||
             reply.configuration() && !entry.offer.configuration ||
             !reply.configuration() && ConfigurationNetwork.required() ||
             !entry.offer.play.containsAll(reply.optional()))) {
            System.out.println("[Unity] Registry handshake rejected: payload=" +
                               (packet.payload() == null ? "absent" : packet.payload().getClass().getName()) +
                               " decoded=" + (reply != null) +
                               " digestMatch=" + (reply != null && wireDigest().equals(reply.registry())) +
                               " config=" + (reply != null && reply.configuration()) +
                               " required=" + ConfigurationNetwork.required());
            entry.diagnosing = true;
            entry.diagnosticDeadline = System.nanoTime() + DIAGNOSTIC_TIMEOUT;
            try {
                connection.send(new ClientboundCustomQueryPacket(
                    TX_DIFF, new DiscardedQueryPayload(Identifier.parse("unity:registry_diff/v1"))));
            } catch (RuntimeException failure) {
                forget(connection);
                listener.disconnect(Component.literal("Unity: content registry mismatch — 诊断查询发送失败"));
            }
            return true;
        }
        PlayNetwork.agree(connection, reply.optional());
        ConfigurationNetwork.agree(connection, reply.configuration());
        if (Boolean.getBoolean("mc.unity.contentQA"))
            System.out.println("[Unity] Login capabilities play=" + reply.optional().size() +
                               " configuration=" + reply.configuration() + " extended=" + reply.extended());
        entry.resume.run();
        System.out.println("[Unity] Verified client content registry " + Content.digest());
        return true;
    }
    public static boolean diagnosticTimeout(ServerLoginPacketListenerImpl listener, Connection connection) {
        synchronized (LoginSync.class) {
            var entry = waiting.get(connection);
            if (entry == null || !entry.diagnosing || System.nanoTime() - entry.diagnosticDeadline < 0)
                return false;
            forget(connection);
        }
        listener.disconnect(
            Component.literal("Unity: content registry mismatch — 未收到客户端诊断清单（超时）"));
        return true;
    }
    public static synchronized boolean rejectMissing(Connection connection,
                                                     ClientboundLoginFinishedPacket packet) {
        if (connection.isMemoryConnection() || matched.remove(connection) || Content.digest().isEmpty())
            return false;
        connection.disconnect(Component.literal("Unity: server did not negotiate Unity content"));
        return true;
    }
    private static String wireDigest() {
        return Content.digest().isEmpty() ? "0".repeat(64) : Content.digest();
    }
    public static synchronized boolean query(Connection connection, ClientboundCustomQueryPacket packet) {
        var id = packet.payload().id();
        if (packet.transactionId() == TX_DIFF && id.toString().equals("unity:registry_diff/v1")) {
            connection.send(new ServerboundCustomQueryAnswerPacket(TX_DIFF, Content.inventory));
            return true;
        }
        if (packet.transactionId() != TX || !id.getNamespace().equals("unity") ||
            !id.getPath().startsWith("registry/"))
            return false;
        var reply = negotiate(id.getPath(), wireDigest(),
                              Offer.local(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND));
        if (reply != null) {
            PlayNetwork.agree(connection, reply.optional());
            ConfigurationNetwork.agree(connection, reply.configuration());
            matched.add(connection);
        }
        connection.send(new ServerboundCustomQueryAnswerPacket(TX, reply));
        System.out.println("[Unity] Server registry match=" + (reply != null) + " local=" + Content.digest());
        return true;
    }
}
