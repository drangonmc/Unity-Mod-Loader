package net.minecraftforge.network.payload;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface PayloadConnection<T extends CustomPacketPayload> {
    PayloadProtocol<RegistryFriendlyByteBuf, T> play();
    default PayloadConnection<T> play(Consumer<PayloadProtocol<RegistryFriendlyByteBuf, T>> consumer) {
        consumer.accept(play());
        return this;
    }
}
