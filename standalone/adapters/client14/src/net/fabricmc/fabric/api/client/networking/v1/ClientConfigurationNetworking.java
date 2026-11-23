package net.fabricmc.fabric.api.client.networking.v1;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import mc.unity.core.ConfigurationNetwork;
import java.util.*;
public final class ClientConfigurationNetworking {
    public interface ConfigurationPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }
    public interface Context {
        Minecraft client();
        ClientConfigurationPacketListenerImpl packetListener();
        PacketSender responseSender();
    }
    private record Message(Minecraft client, ClientConfigurationPacketListenerImpl packetListener,
                           PacketSender responseSender) implements Context {}
    private static <T extends CustomPacketPayload> boolean bind(ConfigurationNetwork.Peer listener,
                                                                CustomPacketPayload.Type<T> type,
                                                                ConfigurationPayloadHandler<T> cb) {
        return ConfigurationNetwork.receiver(
            listener, PacketFlow.CLIENTBOUND, type, cb,
            (v, p)
                -> cb.receive(v, new Message((Minecraft)p.unity$executor(),
                                             (ClientConfigurationPacketListenerImpl)p,
                                             PacketSender.configuration(p))),
            false);
    }
    public static <T extends CustomPacketPayload>
        boolean registerGlobalReceiver(CustomPacketPayload.Type<T> t, ConfigurationPayloadHandler<T> h) {
        return bind(null, t, h);
    }
    public static ConfigurationPayloadHandler<?> unregisterGlobalReceiver(CustomPacketPayload.Type<?> t) {
        return (ConfigurationPayloadHandler<?>)ConfigurationNetwork.unregister(null, PacketFlow.CLIENTBOUND,
                                                                               t.id());
    }
    public static Set<Identifier> getGlobalReceivers() {
        return ConfigurationNetwork.received(null, PacketFlow.CLIENTBOUND);
    }
    public static <T extends CustomPacketPayload> boolean registerReceiver(CustomPacketPayload.Type<T> t,
                                                                           ConfigurationPayloadHandler<T> h) {
        return bind(ConfigurationNetwork.client(), t, h);
    }
    public static ConfigurationPayloadHandler<?> unregisterReceiver(Identifier id) {
        return (ConfigurationPayloadHandler<?>)ConfigurationNetwork.unregister(ConfigurationNetwork.client(),
                                                                               PacketFlow.CLIENTBOUND, id);
    }
    public static Set<Identifier> getReceived() {
        return ConfigurationNetwork.received(ConfigurationNetwork.client(), PacketFlow.CLIENTBOUND);
    }
    public static Set<Identifier> getSendable() {
        return ConfigurationNetwork.sendable(ConfigurationNetwork.client());
    }
    public static boolean canSend(Identifier id) {
        return ConfigurationNetwork.canSend(ConfigurationNetwork.client(), id);
    }
    public static boolean canSend(CustomPacketPayload.Type<?> t) { return canSend(t.id()); }
    public static Packet<net.minecraft.network.protocol.common.ServerCommonPacketListener>
    createServerboundPacket(CustomPacketPayload p) {
        return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(p);
    }
    public static PacketSender getSender() {
        return PacketSender.configuration(ConfigurationNetwork.client());
    }
    public static void send(CustomPacketPayload p) {
        ConfigurationNetwork.send(ConfigurationNetwork.client(), p, null);
    }
}
