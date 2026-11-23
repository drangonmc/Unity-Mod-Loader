package net.minecraftforge.network.payload;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.event.network.CustomPayloadEvent.Context;
import net.minecraftforge.network.Channel;
public interface PayloadFlow<B extends FriendlyByteBuf, T extends CustomPacketPayload>
    extends PayloadProtocol<B, T> {
    <M extends T> PayloadFlow<B, T> addMain(
        CustomPacketPayload.Type<M> type, StreamCodec<B, M> codec, BiConsumer<M, Context> handler);
    <M extends T> PayloadFlow<B, T> add(
        CustomPacketPayload.Type<M> type, StreamCodec<B, M> codec, BiConsumer<M, Context> handler);
    Channel<T> build();
}
