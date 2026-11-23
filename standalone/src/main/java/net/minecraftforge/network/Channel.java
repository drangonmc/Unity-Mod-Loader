package net.minecraftforge.network;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public abstract class Channel<M> {
    public interface VersionTest {
        enum Status { MISSING, VANILLA, PRESENT }
        boolean accepts(Status status, int remoteVersion);
        static VersionTest exact(int version) {
            return new Exact(version);
        }
        record Exact(int version) implements VersionTest {
            public boolean accepts(Status status, int remoteVersion) {
                return status == Status.PRESENT && version == remoteVersion;
            }
        }
    }
    protected abstract CustomPacketPayload checked(M message, PacketFlow direction);
    public void send(M message, Connection connection) {
        var direction = connection.getSending();
        var payload = checked(message, direction);
        if (direction == PacketFlow.SERVERBOUND)
            connection.send(new ServerboundCustomPayloadPacket(payload));
        else
            connection.send(new ClientboundCustomPayloadPacket(payload));
    }
    public void send(M message, PacketDistributor.PacketTarget target) {
        target.send(checked(message, target.flow()));
    }
}
