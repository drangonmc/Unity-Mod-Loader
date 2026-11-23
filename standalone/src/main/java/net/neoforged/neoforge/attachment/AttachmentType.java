package net.neoforged.neoforge.attachment;
import com.mojang.serialization.*;
import java.util.Objects;
import java.util.function.*;
import mc.unity.core.Attachments;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
public final class AttachmentType<T> implements Attachments.Key<T> {
    final Function<IAttachmentHolder, T> initializer;
    private final Codec<T> codec;
    private final boolean death;
    private final IAttachmentCopyHandler<T> copy;
    public boolean copyOnDeath() { return death; }
    public T copy(T value, Attachments.Owner owner, net.minecraft.core.HolderLookup.Provider registries) {
        return copy == null ? Attachments.Key.super.copy(value, owner, registries)
                            : copy.copy(value, (IAttachmentHolder)owner, registries);
    }
    private AttachmentType(Builder<T> builder) {
        initializer = builder.initializer;
        codec = builder.codec;
        death = builder.death;
        copy = builder.copy;
    }
    public Identifier id() {
        return Objects.requireNonNull(NeoForgeRegistries.ATTACHMENT_TYPES.getKey(this),
                                      "Unregistered attachment");
    }
    public Codec<T> codec() { return codec; }
    public static <T> Builder<T> builder(Supplier<T> initializer) {
        return builder(holder -> initializer.get());
    }
    public static <T> Builder<T> builder(Function<IAttachmentHolder, T> initializer) {
        return new Builder<>(initializer);
    }
    public static final class Builder<T> {
        private final Function<IAttachmentHolder, T> initializer;
        private Codec<T> codec;
        private boolean death;
        private IAttachmentCopyHandler<T> copy;
        public Builder<T> copyOnDeath() {
            death = true;
            return this;
        }
        public Builder<T> copyHandler(IAttachmentCopyHandler<T> handler) {
            copy = Objects.requireNonNull(handler);
            return this;
        }
        private Builder(Function<IAttachmentHolder, T> initializer) {
            this.initializer = Objects.requireNonNull(initializer);
        }
        public Builder<T> serialize(MapCodec<T> codec) {
            if (this.codec != null)
                throw new IllegalStateException("Duplicate attachment codec");
            this.codec = codec.codec();
            return this;
        }
        public AttachmentType<T> build() { return new AttachmentType<>(this); }
    }
}
