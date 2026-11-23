package net.neoforged.neoforge.client.network;
import mc.unity.core.PlayNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public final class ClientPacketDistributor {
    public static void sendToServer(CustomPacketPayload first, CustomPacketPayload... rest) {
        PlayNetwork.sendToServer(first);
        for (var payload : rest) PlayNetwork.sendToServer(payload);
    }
}
