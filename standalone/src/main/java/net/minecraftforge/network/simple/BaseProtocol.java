package net.minecraftforge.network.simple;
import net.minecraft.network.protocol.PacketFlow;
public interface BaseProtocol<F, P> {
    F flow(PacketFlow flow);
    default F serverbound() {
        return flow(PacketFlow.SERVERBOUND);
    }
    default F clientbound() {
        return flow(PacketFlow.CLIENTBOUND);
    }
    default F bidirectional() {
        return flow(null);
    }
}
