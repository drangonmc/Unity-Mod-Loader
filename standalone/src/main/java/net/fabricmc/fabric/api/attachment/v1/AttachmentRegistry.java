package net.fabricmc.fabric.api.attachment.v1;
import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.function.*;
import mc.unity.core.Attachments;
import net.minecraft.resources.Identifier;
public final class AttachmentRegistry {
    public interface Builder<A> {
        Builder<A> copyOnDeath();
        Builder<A> persistent(Codec<A> codec);
        Builder<A> initializer(Supplier<A> supplier);
        AttachmentType<A> buildAndRegister(Identifier id);
    }
    private record Type<A>(Identifier identifier, Codec<A> persistenceCodec, Supplier<A> initializer,
                           boolean copyOnDeath) implements AttachmentType<A> {}
    public static <A> Builder<A> builder() {
        return new Builder<>() {
            private boolean death;
            public Builder<A> copyOnDeath() {
                death = true;
                return this;
            }
            private Codec<A> codec;
            private Supplier<A> initial;
            public Builder<A> persistent(Codec<A> value) {
                codec = Objects.requireNonNull(value);
                return this;
            }
            public Builder<A> initializer(Supplier<A> supplier) {
                initial = Objects.requireNonNull(supplier);
                return this;
            }
            public AttachmentType<A> buildAndRegister(Identifier id) {
                var type = new Type<>(Objects.requireNonNull(id), codec, initial, death);
                Attachments.register(type);
                return type;
            }
        };
    }
    public static <A> AttachmentType<A> create(Identifier id, Consumer<Builder<A>> configure) {
        Builder<A> b = builder();
        configure.accept(b);
        return b.buildAndRegister(id);
    }
    public static <A> AttachmentType<A> create(Identifier id) {
        return AttachmentRegistry.<A>builder().buildAndRegister(id);
    }
    public static <A> AttachmentType<A> createDefaulted(Identifier id, Supplier<A> supplier) {
        return AttachmentRegistry.<A>builder().initializer(supplier).buildAndRegister(id);
    }
    public static <A> AttachmentType<A> createPersistent(Identifier id, Codec<A> codec) {
        return AttachmentRegistry.<A>builder().persistent(codec).buildAndRegister(id);
    }
}
