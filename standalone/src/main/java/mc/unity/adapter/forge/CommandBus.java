package mc.unity.adapter.forge;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.listener.EventListener;

public final class CommandBus<T> implements EventBus<T> {
    public static final CommandBus<RegisterCommandsEvent> INSTANCE = new CommandBus<>();
    private record Subscription(CommandBus<?> owner, Runnable remove) implements EventListener {}
    private final mc.unity.core.Events.Signal<T> signal = new mc.unity.core.Events.Signal<>();
    public EventListener addListener(Consumer<T> listener) {
        return new Subscription(this, signal.listen(listener));
    }
    public EventListener addListener(byte p, Consumer<T> listener) {
        return new Subscription(this, signal.listen(p, listener));
    }
    public static void remove(EventListener token) {
        CancelBus.remove(token);
        if (token instanceof Subscription sub)
            sub.remove.run();
    }
    public boolean post(net.minecraftforge.eventbus.internal.Event e) {
        dispatch((T) e);
        return false;
    }
    public boolean hasListeners() {
        return !empty();
    }
    public boolean empty() {
        return signal.empty();
    }
    public void removeListener(EventListener listener) {
        if (listener instanceof Subscription sub && sub.owner == this)
            sub.remove.run();
    }
    public void dispatch(T event) {
        signal.post(event);
    }
    public void clear() {
        signal.clear();
    }
}
