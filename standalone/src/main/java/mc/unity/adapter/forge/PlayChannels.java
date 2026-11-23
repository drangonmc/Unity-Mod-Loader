package mc.unity.adapter.forge;
import java.util.*;
import java.util.function.BiConsumer;
import mc.unity.core.PlayNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.network.CustomPayloadEvent.Context;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.payload.*;

/** Connect Forge play codecs and typed handlers to the host connection's direction and executor. */
public final class PlayChannels {
    public static PayloadConnection<CustomPacketPayload> create(Identifier id, int version) {
        return new PlayChannel(id, version);
    }
    private static final class PlayChannel
        extends Channel<CustomPacketPayload> implements PayloadConnection<CustomPacketPayload> {
        private final Identifier name;
        private final int version;
        private boolean built;
        PlayChannel(Identifier name, int version) {
            this.name = name;
            this.version = version;
        }
        public PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play() {
            return new Flow(this, null);
        }
        protected CustomPacketPayload checked(CustomPacketPayload message, PacketFlow direction) {
            if (!built)
                throw new IllegalStateException("Channel is not built");
            PlayNetwork.check(name, message, direction);
            return message;
        }
    }
    private record Flow(PlayChannel channel, PacketFlow direction)
        implements PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> {
        public Flow flow(PacketFlow flow) {
            return new Flow(channel, flow);
        }
        private <M extends CustomPacketPayload> Flow bind(CustomPacketPayload.Type<M> type,
            StreamCodec<RegistryFriendlyByteBuf, M> codec, BiConsumer<M, Context> handler, boolean main) {
            if (channel.built)
                throw new IllegalStateException("Channel already built " + channel.name);
            PlayNetwork.add(channel.name, channel.version, direction, type, codec,
                (msg, ctx) -> handler.accept(msg, new Context(ctx)), main);
            return this;
        }
        public <M extends CustomPacketPayload> Flow addMain(CustomPacketPayload.Type<M> type,
            StreamCodec<RegistryFriendlyByteBuf, M> codec, BiConsumer<M, Context> handler) {
            return bind(type, codec, handler, true);
        }
        public <M extends CustomPacketPayload> Flow add(CustomPacketPayload.Type<M> type,
            StreamCodec<RegistryFriendlyByteBuf, M> codec, BiConsumer<M, Context> handler) {
            return bind(type, codec, handler, false);
        }
        public Channel<CustomPacketPayload> build() {
            channel.built = true;
            return channel;
        }
    }
}