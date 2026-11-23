package net.fabricmc.fabric.api.networking.v1;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import mc.unity.core.ConfigurationNetwork;
import java.util.*;
public final class ServerConfigurationNetworking {
    public interface ConfigurationPacketHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }
    public interface Context {
        MinecraftServer server();
        ServerConfigurationPacketListenerImpl packetListener();
        PacketSender responseSender();
    }
    private record Message(MinecraftServer server, ServerConfigurationPacketListenerImpl packetListener,
                           PacketSender responseSender) implements Context {}
    private static <T extends CustomPacketPayload> boolean bind(ConfigurationNetwork.Peer listener,
                                                                CustomPacketPayload.Type<T> type,
                                                                ConfigurationPacketHandler<T> cb) {
        return ConfigurationNetwork.receiver(
            listener, PacketFlow.SERVERBOUND, type, cb,
            (v, p)
                -> cb.receive(v, new Message((MinecraftServer)p.unity$executor(),
                                             (ServerConfigurationPacketListenerImpl)p,
                                             PacketSender.configuration(p))),
            false);
    }
    public static <T extends CustomPacketPayload>
        boolean registerGlobalReceiver(CustomPacketPayload.Type<T> t, ConfigurationPacketHandler<T> h) {
        return bind(null, t, h);
    }
    public static ConfigurationPacketHandler<?> unregisterGlobalReceiver(Identifier id) {
        return (ConfigurationPacketHandler<?>)ConfigurationNetwork.unregister(null, PacketFlow.SERVERBOUND,
                                                                              id);
    }
    public static Set<Identifier> getGlobalReceivers() {
        return ConfigurationNetwork.received(null, PacketFlow.SERVERBOUND);
    }
    public static <T extends CustomPacketPayload>
        boolean registerReceiver(ServerConfigurationPacketListenerImpl listener,
                                 CustomPacketPayload.Type<T> t, ConfigurationPacketHandler<T> h) {
        return bind((ConfigurationNetwork.Peer)listener, t, h);
    }
    public static ConfigurationPacketHandler<?>
    unregisterReceiver(ServerConfigurationPacketListenerImpl listener, Identifier id) {
        return (ConfigurationPacketHandler<?>)ConfigurationNetwork.unregister(
            (ConfigurationNetwork.Peer)listener, PacketFlow.SERVERBOUND, id);
    }
    public static Set<Identifier> getReceived(ServerConfigurationPacketListenerImpl listener) {
        return ConfigurationNetwork.received((ConfigurationNetwork.Peer)listener, PacketFlow.SERVERBOUND);
    }
    public static Set<Identifier> getSendable(ServerConfigurationPacketListenerImpl listener) {
        return ConfigurationNetwork.sendable((ConfigurationNetwork.Peer)listener);
    }
    public static boolean canSend(ServerConfigurationPacketListenerImpl listener, Identifier id) {
        return ConfigurationNetwork.canSend((ConfigurationNetwork.Peer)listener, id);
    }
    public static boolean canSend(ServerConfigurationPacketListenerImpl listener,
                                  CustomPacketPayload.Type<?> t) {
        return canSend(listener, t.id());
    }
    public static Packet<net.minecraft.network.protocol.common.ClientCommonPacketListener>
    createClientboundPacket(CustomPacketPayload p) {
        return new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(p);
    }
    public static PacketSender getSender(ServerConfigurationPacketListenerImpl listener) {
        return PacketSender.configuration((ConfigurationNetwork.Peer)listener);
    }
    public static void send(ServerConfigurationPacketListenerImpl listener, CustomPacketPayload p) {
        ConfigurationNetwork.send((ConfigurationNetwork.Peer)listener, p, null);
    }
    public static MinecraftServer getServer(ServerConfigurationPacketListenerImpl listener) {
        return (MinecraftServer)((ConfigurationNetwork.Peer)listener).unity$executor();
    }
}
