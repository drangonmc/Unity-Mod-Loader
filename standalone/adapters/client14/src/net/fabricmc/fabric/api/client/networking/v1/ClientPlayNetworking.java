package net.fabricmc.fabric.api.client.networking.v1;

import java.util.Set;
import java.util.Objects;
import mc.unity.core.PlayNetwork;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class ClientPlayNetworking {
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }
    public interface Context {
        Minecraft client();
        LocalPlayer player();
        PacketSender responseSender();
    }
    private record Message(Minecraft client, LocalPlayer player, PacketSender responseSender)
        implements Context {}
    public static <T extends CustomPacketPayload>
        boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return PlayNetwork.receiver(PacketFlow.CLIENTBOUND, type).bind(handler, (value, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            handler.receive(value, new Message(mc, mc.player, PacketSender.play(ctx.connection())));
        });
    }
    public static PlayPayloadHandler<?> unregisterGlobalReceiver(Identifier id) {
        return (PlayPayloadHandler<?>)PlayNetwork
            .receiver(PacketFlow.CLIENTBOUND, new CustomPacketPayload.Type<>(id))
            .unbind();
    }
    public static Set<Identifier> getGlobalReceivers() {
        return PlayNetwork.receivers(PacketFlow.CLIENTBOUND);
    }
    private static Connection connection() {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null)
            throw new IllegalStateException("No client play connection");
        return listener.getConnection();
    }
    public static boolean canSend(Identifier id) {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && PlayNetwork.canSend(listener.getConnection(), PacketFlow.SERVERBOUND, id);
    }
    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return canSend(type.id());
    }
    public static Set<Identifier> getSendable() {
        return PlayNetwork.sendable(connection(), PacketFlow.SERVERBOUND);
    }
    public static <T extends CustomPacketPayload> Packet<ServerCommonPacketListener>
    createServerboundPacket(T payload) {
        return new ServerboundCustomPayloadPacket(payload);
    }
    public static PacketSender getSender() {
        return PacketSender.play(connection());
    }
    public static void send(CustomPacketPayload payload) {
        getSender().sendPacket(payload);
    }
}
