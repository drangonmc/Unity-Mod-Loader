package net.minecraftforge.network;
import java.util.function.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.LevelChunk;
public final class PacketDistributor<T> {
    public static final PacketDistributor<Void> SERVER = new PacketDistributor<>(
        PacketFlow.SERVERBOUND, unused -> mc.unity.core.PlayNetwork::sendToServer, true);
    public static final PacketDistributor<ServerPlayer> PLAYER = new PacketDistributor<>(PacketFlow.CLIENTBOUND,
        p -> payload -> p.connection.send(new ClientboundCustomPayloadPacket(payload)), false);
    public static final PacketDistributor<Void> ALL = new PacketDistributor<>(PacketFlow.CLIENTBOUND,
        unused
        -> payload
        -> mc.unity.adapter.forge.GameEvents.server().getPlayerList().broadcastAll(
            new ClientboundCustomPayloadPacket(payload)),
        true);
    public static final PacketDistributor<LevelChunk> TRACKING_CHUNK =
        new PacketDistributor<>(PacketFlow.CLIENTBOUND, chunk -> payload -> {
            var level = (ServerLevel) chunk.getLevel();
            var packet = new ClientboundCustomPayloadPacket(payload);
            for (var player : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false))
                player.connection.send(packet);
        }, false);
    private final PacketFlow flow;
    private final Function<T, Consumer<CustomPacketPayload>> target;
    private final boolean noArg;
    private PacketDistributor(
        PacketFlow flow, Function<T, Consumer<CustomPacketPayload>> target, boolean noArg) {
        this.flow = flow;
        this.target = target;
        this.noArg = noArg;
    }
    public record PacketTarget(PacketFlow flow, Consumer<CustomPacketPayload> consumer) {
        void send(CustomPacketPayload payload) {
            consumer.accept(payload);
        }
    }
    public PacketTarget with(T input) {
        if (!noArg)
            java.util.Objects.requireNonNull(input);
        return new PacketTarget(flow, target.apply(input));
    }
    public PacketTarget noArg() {
        if (!noArg)
            throw new IllegalArgumentException("This distributor requires a target");
        return with(null);
    }
}
