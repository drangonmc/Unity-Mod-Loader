package net.neoforged.neoforge.registries.datamaps;
import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
public class DataMapType<R, T> {
    private final ResourceKey<Registry<R>> registry;
    private final Identifier id;
    private final Codec<T> codec, network;
    private final boolean mandatory;
    private DataMapType(Builder<T, R> b) {
        registry = b.registry;
        id = b.id;
        codec = b.codec;
        network = b.network;
        mandatory = b.mandatory;
    }
    public static <T, R> Builder<T, R> builder(Identifier id, ResourceKey<Registry<R>> registry,
                                               Codec<T> codec) {
        return new Builder<>(id, registry, codec);
    }
    public ResourceKey<Registry<R>> registryKey() { return registry; }
    public Identifier id() { return id; }
    public Codec<T> codec() { return codec; }
    public Codec<T> networkCodec() { return network; }
    public boolean mandatorySync() { return mandatory; }
    public static class Builder<T, R> {
        private final ResourceKey<Registry<R>> registry;
        private final Identifier id;
        private final Codec<T> codec;
        private Codec<T> network;
        private boolean mandatory;
        private Builder(Identifier id, ResourceKey<Registry<R>> registry, Codec<T> codec) {
            this.registry = Objects.requireNonNull(registry);
            this.id = Objects.requireNonNull(id);
            this.codec = Objects.requireNonNull(codec);
        }
        public Builder<T, R> synced(Codec<T> network, boolean mandatory) {
            this.network = Objects.requireNonNull(network);
            this.mandatory = mandatory;
            return this;
        }
        public DataMapType<R, T> build() { return new DataMapType<>(this); }
    }
}
