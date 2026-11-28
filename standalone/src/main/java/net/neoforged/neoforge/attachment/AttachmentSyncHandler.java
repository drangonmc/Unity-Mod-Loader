package net.neoforged.neoforge.attachment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public interface AttachmentSyncHandler<T> {
    default boolean sendToPlayer(IAttachmentHolder holder,ServerPlayer to){return true;}
    void write(RegistryFriendlyByteBuf buffer,T value,boolean initialSync);
    T read(IAttachmentHolder holder,RegistryFriendlyByteBuf buffer,T previous);
}
