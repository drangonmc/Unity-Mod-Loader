package qa.fabric;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.*;

public final class FabricPhase {
    static final Identifier ID = Identifier.parse("qa_fabric_ecology:configuration");
    static final CustomPacketPayload.Type<Rules> RULES = new CustomPacketPayload.Type<>(ID);
    static final CustomPacketPayload.Type<Accepted> ACCEPTED = new CustomPacketPayload.Type<>(ID);
    static record Rules(int value) implements CustomPacketPayload {
        public Type<Rules> type() {
            return RULES;
        }
    }
    static record Accepted(String value) implements CustomPacketPayload {
        public Type<Accepted> type() {
            return ACCEPTED;
        }
    }
    static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("qa_fabric_ecology:confirm_rules");
    private static final Map<ServerConfigurationPacketListenerImpl, Integer> pending =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicInteger serial = new AtomicInteger(9100);
    public static void bind() {
        ServerConfigurationConnectionEvents.DISCONNECT.register(
            (l, server) -> System.out.println("QA22 CONFIG END " + l.getOwner().name()));

        PayloadTypeRegistry.clientboundConfiguration().register(
            RULES, StreamCodec.composite(ByteBufCodecs.VAR_INT, Rules::value, Rules::new));
        PayloadTypeRegistry.serverboundConfiguration().register(
            ACCEPTED, StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Accepted::value, Accepted::new));
        ServerConfigurationNetworking.ConfigurationPacketHandler<Accepted> inherited = (ignored, ctx) -> {
            throw new AssertionError("Removed global receiver unexpectedly used");
        };
        if (!ServerConfigurationNetworking.registerGlobalReceiver(ACCEPTED, inherited))
            throw new AssertionError("Global Fabric receiver duplicate");
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((listener, server) -> {
            if (ServerConfigurationNetworking.registerReceiver(listener, ACCEPTED, inherited) ||
                ServerConfigurationNetworking.unregisterReceiver(listener, ID) != inherited)
                throw new AssertionError("Connection must inherit exactly one global receiver");

            if (!ServerConfigurationNetworking.registerReceiver(listener, ACCEPTED, (value, ctx) -> {
                    var required = pending.remove(ctx.packetListener());
                    if (required == null || !value.value().equals("fabric-" + required))
                        throw new IllegalStateException("Wrong Fabric rules");
                    System.out.println("QA21 FABRIC ACK " + required +
                                       " input=" + Thread.currentThread().getName() +
                                       " player=" + ctx.packetListener().getOwner().name());
                    ctx.server().execute(
                        ()
                            -> ((FabricServerConfigurationPacketListenerImpl)ctx.packetListener())
                                   .completeTask(TASK));
                }))
                throw new IllegalStateException("Stale Fabric receiver");
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, RULES))
                throw new IllegalStateException("Fabric receiver unavailable");
            ((FabricServerConfigurationPacketListenerImpl)listener).addTask(new ConfigurationTask() {
                public Type type() {
                    return TASK;
                }
                public void start(Consumer<Packet<?>> send) {
                    int n = serial.incrementAndGet();
                    pending.put(listener, n);
                    System.out.println("QA21 FABRIC OFFER " + n);
                    ServerConfigurationNetworking.send(listener, new Rules(n));
                }
            });
        });
    }
}
