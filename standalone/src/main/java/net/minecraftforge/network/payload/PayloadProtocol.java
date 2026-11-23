package net.minecraftforge.network.payload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.network.simple.BaseProtocol;
public interface PayloadProtocol<B extends FriendlyByteBuf, T extends CustomPacketPayload>
    extends BaseProtocol<PayloadFlow<B, T>, PayloadProtocol<B, T>> {}
