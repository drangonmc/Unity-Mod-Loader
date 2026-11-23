package net.neoforged.neoforge.common.extensions;
import mc.unity.core.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public interface ICommonPacketListener extends PacketListener {
    default Connection getConnection() { return ((ConfigurationNetwork.Link)this).unity$connection(); }
    void send(Packet<?> packet);
    default void send(CustomPacketPayload payload) {
        if (this instanceof ConfigurationNetwork.Peer p)
            ConfigurationNetwork.send(p, payload, null);
        else
            getConnection().send(ConfigurationNetwork.packet(flow().getOpposite(), payload));
    }
    default void disconnect(Component reason) { getConnection().disconnect(reason); }
    default boolean hasChannel(Identifier id) {
        return this instanceof ConfigurationNetwork.Peer p
            ? ConfigurationNetwork.canSend(p, id)
            : PlayNetwork.hasChannel(getConnection(), new CustomPacketPayload.Type<>(id));
    }
    default boolean hasChannel(CustomPacketPayload.Type<?> type) { return hasChannel(type.id()); }
    default boolean hasChannel(CustomPacketPayload p) { return hasChannel(p.type()); }
}
