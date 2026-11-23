package net.fabricmc.fabric.api.client.networking.v1;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import mc.unity.core.ConfigurationNetwork;
import java.util.*;
import net.fabricmc.fabric.api.event.Event;
import mc.unity.adapter.fabric.SessionEvent;
public final class ClientConfigurationConnectionEvents {
    public interface Init {
        void onConfigurationInit(ClientConfigurationPacketListenerImpl listener, Minecraft client);
    }
    public interface Start {
        void onConfigurationStart(ClientConfigurationPacketListenerImpl listener, Minecraft client);
    }
    public interface Ready {
        void onConfigurationReady(ClientConfigurationPacketListenerImpl listener, Minecraft client);
    }
    public interface Complete {
        void onConfigurationComplete(ClientConfigurationPacketListenerImpl listener, Minecraft client);
    }
    public interface Disconnect {
        void onConfigurationDisconnect(ClientConfigurationPacketListenerImpl listener, Minecraft client);
    }
    public static final Event<Init> INIT = new SessionEvent<>(Init.class, list -> (l, e) -> {
        for (var cb : list)
            cb.onConfigurationInit(l, e);
    }, ClientConfigurationConnectionEvents::connectInit);
    public static final Event<Start> START = new SessionEvent<>(Start.class, list -> (l, e) -> {
        for (var cb : list)
            cb.onConfigurationStart(l, e);
    }, ClientConfigurationConnectionEvents::connectStart);
    public static final Event<Ready> READY = new SessionEvent<>(Ready.class, list -> (l, e) -> {
        for (var cb : list)
            cb.onConfigurationReady(l, e);
    }, ClientConfigurationConnectionEvents::connectReady);
    public static final Event<Complete> COMPLETE = new SessionEvent<>(Complete.class, list -> (l, e) -> {
        for (var cb : list)
            cb.onConfigurationComplete(l, e);
    }, ClientConfigurationConnectionEvents::connectComplete);
    public static final Event<Disconnect> DISCONNECT =
        new SessionEvent<>(Disconnect.class, list -> (l, e) -> {
            for (var cb : list)
                cb.onConfigurationDisconnect(l, e);
        }, ClientConfigurationConnectionEvents::connectDisconnect);
    private static void connectInit() {
        ConfigurationNetwork.CLIENT_INIT.listen(
            p
            -> INIT.invoker().onConfigurationInit((ClientConfigurationPacketListenerImpl)p,
                                                  (Minecraft)p.unity$executor()));
    }
    private static void connectStart() {
        ConfigurationNetwork.CLIENT_READY.listen(
            p
            -> START.invoker().onConfigurationStart((ClientConfigurationPacketListenerImpl)p,
                                                    (Minecraft)p.unity$executor()));
    }
    private static void connectReady() {
        ConfigurationNetwork.CLIENT_COMPLETE.listen(
            p
            -> READY.invoker().onConfigurationReady((ClientConfigurationPacketListenerImpl)p,
                                                    (Minecraft)p.unity$executor()));
    }
    private static void connectComplete() {
        ConfigurationNetwork.CLIENT_COMPLETE.listen(
            p
            -> COMPLETE.invoker().onConfigurationComplete((ClientConfigurationPacketListenerImpl)p,
                                                          (Minecraft)p.unity$executor()));
    }
    private static void connectDisconnect() {
        ConfigurationNetwork.CLIENT_END.listen(
            p
            -> DISCONNECT.invoker().onConfigurationDisconnect((ClientConfigurationPacketListenerImpl)p,
                                                              (Minecraft)p.unity$executor()));
    }
}
