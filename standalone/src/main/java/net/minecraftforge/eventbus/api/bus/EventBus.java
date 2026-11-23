package net.minecraftforge.eventbus.api.bus;
import java.util.function.Consumer;
import net.minecraftforge.eventbus.api.listener.EventListener;
public interface EventBus<T> {
    default boolean post(net.minecraftforge.eventbus.internal.Event event) {
        throw new UnsupportedOperationException("Posting not supported by this bus");
    }
    default net.minecraftforge.eventbus.internal.Event fire(net.minecraftforge.eventbus.internal.Event event) {
        post(event);
        return event;
    }
    default boolean hasListeners() {
        throw new UnsupportedOperationException("Unsupported event bus");
    }
    EventListener addListener(Consumer<T> listener);
    EventListener addListener(byte priority, Consumer<T> listener);
    void removeListener(EventListener listener);
}
