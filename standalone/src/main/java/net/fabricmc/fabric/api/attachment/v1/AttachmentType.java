package net.fabricmc.fabric.api.attachment.v1;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import mc.unity.core.Attachments;
import net.minecraft.resources.Identifier;
public interface AttachmentType<A> extends Attachments.Key<A> {
    Identifier identifier();
    Codec<A> persistenceCodec();
    Supplier<A> initializer();
    boolean copyOnDeath();
    default boolean isPersistent() { return persistenceCodec() != null; }
    default boolean isSynced() { return false; }
    default Identifier id() { return identifier(); }
    default Codec<A> codec() { return persistenceCodec(); }
}
