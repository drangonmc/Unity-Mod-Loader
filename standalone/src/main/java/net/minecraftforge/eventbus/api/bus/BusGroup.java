package net.minecraftforge.eventbus.api.bus;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraftforge.eventbus.api.listener.EventListener;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
public interface BusGroup {
    BusGroup DEFAULT = create("default");
    static BusGroup create(String id) {
        return () -> id;
    }
    String name();
    default Collection<EventListener> register(MethodHandles.Lookup lookup, Class<?> type) {
        return subscribe(lookup, type, null, false);
    }
    default Collection<EventListener> register(MethodHandles.Lookup lookup, Object object) {
        return subscribe(lookup, object.getClass(), object, false);
    }
    default Collection<EventListener> subscribe(
        MethodHandles.Lookup lookup, Class<?> type, Object instance, boolean skipOther) {
        var result = new ArrayList<EventListener>();
        try {
            var methods = type.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
            for (var m : methods) {
                var sub = m.getAnnotation(SubscribeEvent.class);
                if (sub == null)
                    continue;
                boolean monitor = m.getParameterCount() == 2
                    && m.getParameterTypes()[1] == boolean.class && sub.priority() == Byte.MIN_VALUE;
                if ((instance == null) != Modifier.isStatic(m.getModifiers())
                    || (!monitor && m.getParameterCount() != 1))
                    throw new IllegalArgumentException("Invalid event handler " + m);
                var handle = lookup.unreflect(m);
                final var call = instance == null ? handle : handle.bindTo(instance);
                var evt = m.getParameterTypes()[0];
                EventBus<Object> bus;
                Method getter = null;
                try {
                    getter = evt.getMethod("getBus", BusGroup.class);
                } catch (NoSuchMethodException ignored) {
                }
                if (getter == null)
                    evt.getField("BUS"); // Unknown event types fail even for a BOTH registration.
                if ((getter == null) != (this == DEFAULT)) {
                    if (skipOther)
                        continue;
                    throw new IllegalArgumentException("Wrong bus for " + evt.getName() + " on " + name());
                }
                bus = (EventBus<Object>) (getter == null ? evt.getField("BUS").get(null)
                                                         : getter.invoke(null, this));
                EventListener token;
                if (monitor) {
                    if (!(bus instanceof CancellableEventBus<Object> cb)
                        || m.getReturnType() != void.class || sub.alwaysCancelling())
                        throw new IllegalArgumentException("Invalid monitor " + m);
                    token = cb.addListener((e, cancelled) -> invoke(call, e, cancelled));
                } else if (m.getReturnType() == boolean.class) {
                    if (!(bus instanceof CancellableEventBus<Object> cb) || sub.alwaysCancelling())
                        throw new IllegalArgumentException("Invalid predicate " + m);
                    token = cb.addListener(
                        sub.priority(), (java.util.function.Predicate<Object>) e -> (boolean) invoke(call, e));
                } else if (m.getReturnType() == void.class) {
                    java.util.function.Consumer<Object> handler = e -> invoke(call, e);
                    if (sub.alwaysCancelling()) {
                        if (!(bus instanceof CancellableEventBus<Object> cb))
                            throw new IllegalArgumentException("Not cancellable " + m);
                        token = cb.addListener(sub.priority(), true, handler);
                    } else
                        token = bus.addListener(sub.priority(), handler);
                } else
                    throw new IllegalArgumentException("Invalid return type " + m);
                result.add(token);
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException e) {
            unregister(result);
            throw new IllegalArgumentException("Invalid event subscriber " + type, e);
        }
    }
    private static Object invoke(MethodHandle handle, Object... args) {
        try {
            return handle.invokeWithArguments(args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
    default void unregister(Collection<EventListener> listeners) {
        for (var token : listeners) mc.unity.adapter.forge.CommandBus.remove(token);
    }
}
