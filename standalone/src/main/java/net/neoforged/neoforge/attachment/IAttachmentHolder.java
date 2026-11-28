package net.neoforged.neoforge.attachment;
import java.util.*;
import java.util.function.Supplier;
import mc.unity.core.Attachments;
public interface IAttachmentHolder extends Attachments.Owner {
    default void syncData(AttachmentType<?> key){AttachmentWire.changed(this,key);}
    default void syncData(Supplier<? extends AttachmentType<?>> key){syncData(key.get());}
    default boolean hasAttachments() {
        var data = unity$attachments(false);
        return data != null && data.any();
    }
    default boolean hasData(AttachmentType<?> type) { return Attachments.find(this, type) != null; }
    default<T> boolean hasData(Supplier<AttachmentType<T>> type) { return hasData(type.get()); }
    default<T> T getExistingDataOrNull(AttachmentType<T> type) { return Attachments.find(this, type); }
    default<T> T getExistingDataOrNull(Supplier<AttachmentType<T>> type) {
        return getExistingDataOrNull(type.get());
    }
    default<T> Optional<T> getExistingData(AttachmentType<T> type) {
        return Optional.ofNullable(getExistingDataOrNull(type));
    }
    default<T> Optional<T> getExistingData(Supplier<AttachmentType<T>> type) {
        return getExistingData(type.get());
    }
    default<T> T getData(AttachmentType<T> type) {
        var result = getExistingDataOrNull(type);
        if (result == null) {
            result = Objects.requireNonNull(type.initializer.apply(this));
            Attachments.initialize(this, type, result);
            syncData(type);
        }
        return result;
    }
    default<T> T getData(Supplier<AttachmentType<T>> type) { return getData(type.get()); }
    default<T> T setData(AttachmentType<T> type, T value) {
        T previous=Attachments.set(this, type, Objects.requireNonNull(value));syncData(type);return previous;
    }
    default<T> T setData(Supplier<AttachmentType<T>> type, T value) { return setData(type.get(), value); }
    default<T> T removeData(AttachmentType<T> type) { T old=Attachments.set(this,type,null);if(old!=null)syncData(type);return old; }
    default<T> T removeData(Supplier<AttachmentType<T>> type) { return removeData(type.get()); }
}
