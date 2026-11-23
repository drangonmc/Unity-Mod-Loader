package net.fabricmc.fabric.api.attachment.v1;
import java.util.Objects;
import java.util.function.*;
import mc.unity.core.Attachments;
public interface AttachmentTarget extends Attachments.Owner {
    default<A> A getAttached(AttachmentType<A> type) { return Attachments.find(this, type); }
    default<A> A getAttachedOrThrow(AttachmentType<A> type) {
        return Objects.requireNonNull(getAttached(type), type.identifier().toString());
    }
    default<A> A getAttachedOrSet(AttachmentType<A> type, A value) {
        return getAttachedOrCreate(type, () -> value);
    }
    default<A> A getAttachedOrCreate(AttachmentType<A> type, Supplier<A> supplier) {
        var v = getAttached(type);
        if (v == null) {
            v = Objects.requireNonNull(supplier.get());
            Attachments.initialize(this, type, v);
        }
        return v;
    }
    default<A> A getAttachedOrCreate(AttachmentType<A> type) {
        return getAttachedOrCreate(
            type, () -> Objects.requireNonNull(type.initializer(), "No attachment initializer").get());
    }
    default<A> A getAttachedOrElse(AttachmentType<A> type, A fallback) {
        var v = getAttached(type);
        return v != null ? v : fallback;
    }
    default<A> A getAttachedOrGet(AttachmentType<A> type, Supplier<A> supplier) {
        var v = getAttached(type);
        return v != null ? v : supplier.get();
    }
    default<A> A setAttached(AttachmentType<A> type, A value) { return Attachments.set(this, type, value); }
    default boolean hasAttached(AttachmentType<?> type) { return getAttached(type) != null; }
    default<A> A removeAttached(AttachmentType<A> type) { return setAttached(type, null); }
    default<A> A modifyAttached(AttachmentType<A> type, UnaryOperator<A> op) {
        return setAttached(type, op.apply(getAttached(type)));
    }
}
