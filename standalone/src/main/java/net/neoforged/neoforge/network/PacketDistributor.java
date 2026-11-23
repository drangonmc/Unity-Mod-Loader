package net.neoforged.neoforge.network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public final class PacketDistributor {
    public static void sendToPlayer(
        ServerPlayer player, CustomPacketPayload first, CustomPacketPayload... rest) {
        mc.unity.core.PlayNetwork.checkPeer(player.connection,first);
        for (var p : rest) mc.unity.core.PlayNetwork.checkPeer(player.connection,p);
        player.connection.send(new ClientboundCustomPayloadPacket(first));
        for (var payload : rest) player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
}
