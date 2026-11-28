package net.minecraftforge.network;
import mc.unity.adapter.forge.PlayChannels;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.payload.PayloadConnection;
public final class ChannelBuilder {
    private final Identifier name;
    private int version;
    private Channel.VersionTest accepted;
    private ChannelBuilder(Identifier name) {
        this.name = name;
    }
    public static ChannelBuilder named(Identifier name) {
        return new ChannelBuilder(name);
    }
    public static ChannelBuilder named(String name) {
        return named(Identifier.parse(name));
    }
    public ChannelBuilder networkProtocolVersion(int version) {
        this.version = version;
        return this;
    }
    public ChannelBuilder acceptedVersions(Channel.VersionTest policy) {
        accepted = policy;
        return this;
    }
    public PayloadConnection<CustomPacketPayload> payloadChannel() {
        if (accepted != null
            && (!(accepted instanceof Channel.VersionTest.Exact exact) || exact.version() != version))
            throw new IllegalArgumentException(
                "This Beta supports exact Forge play channel versions: " + name);
        return PlayChannels.create(name, version);
    }
}
