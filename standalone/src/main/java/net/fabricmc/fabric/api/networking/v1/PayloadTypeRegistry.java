package net.fabricmc.fabric.api.networking.v1;
import mc.unity.core.ConfigurationNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {
    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
        CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);
    static PayloadTypeRegistry<FriendlyByteBuf> serverboundConfiguration() {
        return Registry.SERVER;
    }
    static PayloadTypeRegistry<FriendlyByteBuf> clientboundConfiguration() {
        return Registry.CLIENT;
    }
    static PayloadTypeRegistry<net.minecraft.network.RegistryFriendlyByteBuf> serverboundPlay() {
        return Play.SERVER;
    }
    static PayloadTypeRegistry<net.minecraft.network.RegistryFriendlyByteBuf> clientboundPlay() {
        return Play.CLIENT;
    }
    enum Play implements PayloadTypeRegistry < net
    .minecraft.network.RegistryFriendlyByteBuf> {
        SERVER(PacketFlow.SERVERBOUND), CLIENT(PacketFlow.CLIENTBOUND);
        private final PacketFlow flow;
        Play(PacketFlow flow) {
            this.flow = flow;
        }
        public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,T>
        register(CustomPacketPayload.Type<T> type, StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,T> codec) {
            mc.unity.core.PlayNetwork.endpoint(flow, type, codec);
            return new CustomPacketPayload.TypeAndCodec<net.minecraft.network.RegistryFriendlyByteBuf, T>(
                type, codec.cast());
        }
    }
    enum Registry implements PayloadTypeRegistry<FriendlyByteBuf> {
        SERVER(PacketFlow.SERVERBOUND),
        CLIENT(PacketFlow.CLIENTBOUND);
        private final PacketFlow direction;
        Registry(PacketFlow direction) {
            this.direction = direction;
        }
        public <T extends CustomPacketPayload>
            CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, T> register(
                CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
            ConfigurationNetwork.type(direction, type, codec, "fabric", true);
            return new CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, T>(type, codec.cast());
        }
    }
}
