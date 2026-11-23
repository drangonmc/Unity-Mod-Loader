package net.neoforged.neoforge.attachment;
@FunctionalInterface
public interface IAttachmentCopyHandler<T> {
    T copy(T value, IAttachmentHolder target, net.minecraft.core.HolderLookup.Provider lookup);
}
