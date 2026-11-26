package qa.login;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import mc.unity.api.TickingMod;
import mc.unity.core.LoginSync;

/** Runs on a real server tick. A recording transport models loss of an optional diagnostic response. */
public final class ReviewLoginProbe implements TickingMod {
    private final List<Integer> issued = new ArrayList<>();
    private final Connection wire = new Connection(PacketFlow.SERVERBOUND) {
        public void send(Packet<?> packet) {
            issued.add(((ClientboundCustomQueryPacket)packet).transactionId());
        }
    };
    private ServerLoginPacketListenerImpl listener;
    private String reason;
    private long started;
    private int disconnects;
    public void serverStarted(MinecraftServer server) {
        listener = new ServerLoginPacketListenerImpl(server, wire, false) {
            public void disconnect(Component message) {
                disconnects++;
                reason = message.getString();
            }
        };
        if (LoginSync.ready(wire, () -> { throw new AssertionError("Rejected login resumed"); }, server))
            throw new AssertionError("Expected handshake");
        var wrong = new ServerboundCustomQueryAnswerPacket(LoginSync.TX,
                                                           new LoginSync.Reply("f".repeat(64), Set.of()));
        LoginSync.answer(listener, wire, new ServerboundCustomQueryAnswerPacket(LoginSync.TX_DIFF, null));
        LoginSync.answer(listener, wire, wrong);
        LoginSync.answer(listener, wire, wrong);
        if (reason != null || !issued.equals(List.of(LoginSync.TX, LoginSync.TX_DIFF)))
            throw new AssertionError(issued.toString());
        started = System.nanoTime();
    }
    public void serverTick(MinecraftServer server) {
        if (listener == null)
            return;
        listener.tick();
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (reason == null) {
            if (ms > 12000)
                throw new AssertionError("Diagnostic deadline missed");
            return;
        }
        if (!reason.contains("超时") || ms < 4900 || disconnects != 1)
            throw new AssertionError(reason + " " + ms);
        LoginSync.answer(listener, wire, new ServerboundCustomQueryAnswerPacket(LoginSync.TX_DIFF, null));
        if (disconnects != 1)
            throw new AssertionError("Late diagnostic consumed twice");
        System.out.println("QA24 DISTINCT LOGIN IDS, NO-REPLY TIMEOUT " + ms + "ms");
        listener = null;
    }
}
