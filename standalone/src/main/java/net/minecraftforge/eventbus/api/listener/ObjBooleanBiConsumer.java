package net.minecraftforge.eventbus.api.listener;
@FunctionalInterface
public interface ObjBooleanBiConsumer<T> {
    void accept(T value, boolean cancelled);
}
