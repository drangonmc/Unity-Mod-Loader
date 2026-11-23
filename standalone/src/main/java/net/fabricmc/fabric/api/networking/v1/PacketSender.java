package net.fabricmc.fabric.api.networking.v1;
import io.netty.channel.ChannelFutureListener;
import mc.unity.core.ConfigurationNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface PacketSender {
    Packet<?> createPacket(CustomPacketPayload payload);
    void sendPacket(Packet<?> packet, ChannelFutureListener callback);
    default void sendPacket(Packet<?> p) { sendPacket(p, null); }
    default void sendPacket(CustomPacketPayload p) { sendPacket(p, null); }
    default void sendPacket(CustomPacketPayload p, ChannelFutureListener cb) {
        sendPacket(createPacket(p), cb);
    }
    void disconnect(Component reason);
    static PacketSender configuration(ConfigurationNetwork.Peer p) {
        return new PacketSender() {
            public Packet<?> createPacket(CustomPacketPayload v) {
                return ConfigurationNetwork.packet(p.flow().getOpposite(), v);
            }
            public void sendPacket(Packet<?> packet, ChannelFutureListener cb) {
                ConfigurationNetwork.checkPacket(p, packet);
                p.unity$connection().send(packet, cb);
            }
            public void sendPacket(CustomPacketPayload v, ChannelFutureListener cb) {
                ConfigurationNetwork.send(p, v, cb);
            }
            public void disconnect(Component reason) { p.unity$connection().disconnect(reason); }
        };
    }
}
