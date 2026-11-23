package net.neoforged.neoforge.network.configuration;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public interface ICustomConfigurationTask extends net.minecraft.server.network.ConfigurationTask {
    void run(Consumer<CustomPacketPayload> sender);
    default void start(Consumer<Packet<?>> sender) {
        run(payload -> sender.accept(new ClientboundCustomPayloadPacket(payload)));
    }
}
