package net.neoforged.bus.api;
public interface ICancellableEvent {
    default boolean isCanceled() {
        return ((Event) this).cancelled;
    }
    default void setCanceled(boolean value) {
        ((Event) this).cancelled = value;
    }
}
