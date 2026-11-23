package net.fabricmc.fabric.api.networking.v1;
import mc.unity.core.ConfigurationNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {
    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
        CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);
    static PayloadTypeRegistry<FriendlyByteBuf> serverboundConfiguration() { return Registry.SERVER; }
    static PayloadTypeRegistry<FriendlyByteBuf> clientboundConfiguration() { return Registry.CLIENT; }
    enum Registry implements PayloadTypeRegistry<FriendlyByteBuf> {
        SERVER(PacketFlow.SERVERBOUND),
        CLIENT(PacketFlow.CLIENTBOUND);
        private final PacketFlow direction;
        Registry(PacketFlow direction) { this.direction = direction; }
        public <T extends CustomPacketPayload>
            CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, T> register(
                CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
            ConfigurationNetwork.type(direction, type, codec, "fabric", true);
            return new CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, T>(type, codec.cast());
        }
    }
}
