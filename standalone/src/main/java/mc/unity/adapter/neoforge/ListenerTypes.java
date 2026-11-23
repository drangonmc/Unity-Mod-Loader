package mc.unity.adapter.neoforge;
import java.lang.ref.*;
import java.util.*;
import java.util.function.Consumer;
import net.neoforged.bus.api.Event;
public final class ListenerTypes {
    private static final ReferenceQueue<Consumer<?>> expired = new ReferenceQueue<>();
    private static final Map<Key, Class<?>> types = new HashMap<>();
    private static final class Key extends WeakReference<Consumer<?>> {
        final int hash;
        Key(Consumer<?> c) {
            super(c, expired);
            hash = System.identityHashCode(c);
        }
        public int hashCode() {
            return hash;
        }
        public boolean equals(Object o) {
            return this == o || o instanceof Key k && get() != null && get() == k.get();
        }
    }
    private static void drain() {
        for (Reference<?> r; (r = expired.poll()) != null;) types.remove(r);
    }
    public static void capture(Consumer<?> listener, Class<?> type) {
        if (Event.class.isAssignableFrom(type))
            synchronized (types) {
                drain();
                types.put(new Key(listener), type);
            }
    }
    public static void clear() {
        synchronized (types) {
            types.clear();
            drain();
        }
    }
    @SuppressWarnings("unchecked")
    public static <T extends Event> Class<T> type(Consumer<T> listener) {
        synchronized (types) {
            drain();
            var found = types.get(new Key(listener));
            if (found != null)
                return (Class<T>) found;
        }
        var candidates = new HashSet<Class<?>>();
        for (var m : listener.getClass().getMethods())
            if (m.getName().equals("accept") && !m.isBridge() && m.getParameterCount() == 1
                && Event.class.isAssignableFrom(m.getParameterTypes()[0]))
                candidates.add(m.getParameterTypes()[0]);
        if (candidates.size() == 1)
            return (Class<T>) candidates.iterator().next();
        throw new IllegalArgumentException("Unknown event type for erased consumer; use the Class overload");
    }
}
