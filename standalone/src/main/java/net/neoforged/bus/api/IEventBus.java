package net.neoforged.bus.api;
import java.util.function.Consumer;
import mc.unity.adapter.neoforge.ListenerTypes;
public interface IEventBus {
    <T extends Event> void addListener(
        EventPriority priority, boolean receiveCanceled, Class<T> type, Consumer<T> listener);
    default<T extends Event> void addListener(Class<T> type, Consumer<T> cb) {
        addListener(EventPriority.NORMAL, false, type, cb);
    }
    default<T extends Event> void addListener(Consumer<T> cb) {
        addListener(ListenerTypes.type(cb), cb);
    }
    default<T extends Event> void addListener(EventPriority p, Consumer<T> cb) {
        addListener(p, false, ListenerTypes.type(cb), cb);
    }
    default<T extends Event> void addListener(boolean receive, Consumer<T> cb) {
        addListener(EventPriority.NORMAL, receive, ListenerTypes.type(cb), cb);
    }
    default<T extends Event> void addListener(boolean receive, Class<T> t, Consumer<T> cb) {
        addListener(EventPriority.NORMAL, receive, t, cb);
    }
    default<T extends Event> void addListener(EventPriority p, Class<T> t, Consumer<T> cb) {
        addListener(p, false, t, cb);
    }
    default<T extends Event> void addListener(EventPriority p, boolean receive, Consumer<T> cb) {
        addListener(p, receive, ListenerTypes.type(cb), cb);
    }
    <T extends Event> T post(T event);
}
