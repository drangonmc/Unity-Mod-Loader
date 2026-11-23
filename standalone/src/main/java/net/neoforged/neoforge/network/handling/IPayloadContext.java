package net.neoforged.neoforge.network.handling;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
public interface IPayloadContext {
    default net.neoforged.neoforge.common.extensions.ICommonPacketListener listener() {
        return (net.neoforged.neoforge.common.extensions.ICommonPacketListener)connection()
            .getPacketListener();
    }
    default ConnectionProtocol protocol() { return listener().protocol(); }
    default void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
        throw new IllegalStateException("No server configuration task in this phase");
    }
    Connection connection();
    Player player();
    void reply(CustomPacketPayload payload);
    void disconnect(Component reason);
    CompletableFuture<Void> enqueueWork(Runnable work);
    <T> CompletableFuture<T> enqueueWork(Supplier<T> work);
    PacketFlow flow();
}
