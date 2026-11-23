package net.minecraftforge.eventbus.api.bus;
import java.util.function.*;
import net.minecraftforge.eventbus.api.listener.*;
public interface CancellableEventBus<T> extends EventBus<T> {
    default EventListener addListener(boolean always, Consumer<T> listener) {
        return addListener((byte) 0, always, listener);
    }
    EventListener addListener(byte priority, boolean always, Consumer<T> listener);
    EventListener addListener(Predicate<T> listener);
    EventListener addListener(byte priority, Predicate<T> listener);
    EventListener addListener(ObjBooleanBiConsumer<T> monitor);
}
