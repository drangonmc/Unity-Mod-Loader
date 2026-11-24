package net.fabricmc.fabric.api.networking.v1;

import java.util.Set;
import mc.unity.core.ConfigurationNetwork;
import mc.unity.core.PlayNetwork;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class ServerPlayNetworking {
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }
    public interface Context {
        MinecraftServer server();
        ServerPlayer player();
        PacketSender responseSender();
    }
    private record Message(MinecraftServer server, ServerPlayer player, PacketSender responseSender)
        implements Context {}
    public static <T extends CustomPacketPayload>
        boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return PlayNetwork.receiver(PacketFlow.SERVERBOUND, type)
            .bind(handler,
                  (value, ctx)
                      -> handler.receive(value, new Message((MinecraftServer)ctx.executor(), ctx.sender(),
                                                            getSender(ctx.sender()))));
    }
    public static PlayPayloadHandler<?> unregisterGlobalReceiver(Identifier id) {
        return (PlayPayloadHandler<?>)PlayNetwork
            .receiver(PacketFlow.SERVERBOUND, new CustomPacketPayload.Type<>(id))
            .unbind();
    }
    public static Set<Identifier> getGlobalReceivers() {
        return PlayNetwork.receivers(PacketFlow.SERVERBOUND);
    }
    private static Connection connection(ServerGamePacketListenerImpl listener) {
        return ((ConfigurationNetwork.Link)listener).unity$connection();
    }
    public static boolean canSend(ServerPlayer player, Identifier id) {
        return canSend(player.connection, id);
    }
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return canSend(player, type.id());
    }
    public static boolean canSend(ServerGamePacketListenerImpl listener, Identifier id) {
        return PlayNetwork.canSend(connection(listener), PacketFlow.CLIENTBOUND, id);
    }
    public static boolean canSend(ServerGamePacketListenerImpl listener, CustomPacketPayload.Type<?> type) {
        return canSend(listener, type.id());
    }
    public static Set<Identifier> getSendable(ServerPlayer player) {
        return getSendable(player.connection);
    }
    public static Set<Identifier> getSendable(ServerGamePacketListenerImpl listener) {
        return PlayNetwork.sendable(connection(listener), PacketFlow.CLIENTBOUND);
    }
    public static <T extends CustomPacketPayload> Packet<ClientCommonPacketListener>
    createClientboundPacket(T payload) {
        return new ClientboundCustomPayloadPacket(payload);
    }
    public static PacketSender getSender(ServerPlayer player) {
        return getSender(player.connection);
    }
    public static PacketSender getSender(ServerGamePacketListenerImpl listener) {
        return PacketSender.play(connection(listener));
    }
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        getSender(player).sendPacket(payload);
    }
}
