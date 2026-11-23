package net.fabricmc.fabric.api.networking.v1;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import mc.unity.core.ConfigurationNetwork;
import java.util.*;
import net.fabricmc.fabric.api.event.Event;
import mc.unity.adapter.fabric.SessionEvent;
public final class ServerConfigurationConnectionEvents {
    public interface Configure {
        void onSendConfiguration(ServerConfigurationPacketListenerImpl listener, MinecraftServer server);
    }
    public interface Disconnect {
        void onConfigureDisconnect(ServerConfigurationPacketListenerImpl listener, MinecraftServer server);
    }
    public static final Event<Configure> BEFORE_CONFIGURE =
        new SessionEvent<>(Configure.class, list -> (l, e) -> {
            for (var cb : list)
                cb.onSendConfiguration(l, e);
        }, ServerConfigurationConnectionEvents::connectBeforeConfigure);
    public static final Event<Configure> CONFIGURE = new SessionEvent<>(Configure.class, list -> (l, e) -> {
        for (var cb : list)
            cb.onSendConfiguration(l, e);
    }, ServerConfigurationConnectionEvents::connectConfigure);
    public static final Event<Disconnect> DISCONNECT =
        new SessionEvent<>(Disconnect.class, list -> (l, e) -> {
            for (var cb : list)
                cb.onConfigureDisconnect(l, e);
        }, ServerConfigurationConnectionEvents::connectDisconnect);
    private static void connectBeforeConfigure() {
        ConfigurationNetwork.BEFORE.listen(
            p
            -> BEFORE_CONFIGURE.invoker().onSendConfiguration((ServerConfigurationPacketListenerImpl)p,
                                                              (MinecraftServer)p.unity$executor()));
    }
    private static void connectConfigure() {
        ConfigurationNetwork.CONFIGURE.listen(
            p
            -> CONFIGURE.invoker().onSendConfiguration((ServerConfigurationPacketListenerImpl)p,
                                                       (MinecraftServer)p.unity$executor()));
    }
    private static void connectDisconnect() {
        ConfigurationNetwork.SERVER_END.listen(
            p
            -> DISCONNECT.invoker().onConfigureDisconnect((ServerConfigurationPacketListenerImpl)p,
                                                          (MinecraftServer)p.unity$executor()));
    }
}
