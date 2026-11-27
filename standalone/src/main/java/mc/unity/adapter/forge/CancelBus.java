package mc.unity.adapter.forge;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.listener.EventListener;
import net.minecraftforge.eventbus.api.listener.ObjBooleanBiConsumer;
import net.minecraftforge.eventbus.internal.Event;
/** Normal listeners short-circuit on true. Monitors see the final verdict exactly once. */
public final class CancelBus<T> implements CancellableEventBus<T> {
    private record
        Slot(Class<?> type, byte priority, Predicate<Object> run, ObjBooleanBiConsumer<Object> monitor) {}
    private record Token(CancelBus<?> owner, Slot slot) implements EventListener {}
    private final Class<T> type;
    private static final class Family {
        final ArrayList<Slot> registrations = new ArrayList<>();
        final List<CancelBus<?>> views = new ArrayList<>();
        void publish() {
            for (var view : views)
                view.applicable =
                    registrations.stream().filter(s -> s.type.isAssignableFrom(view.type)).toList();
        }
    }
    private final Family family;
    private volatile List<Slot> applicable = List.of();
    public CancelBus(Class<T> type) {
        this(type, new Family());
    }
    private CancelBus(Class<T> type, Family family) {
        this.type = type;
        this.family = family;
        synchronized (family) {
            family.views.add(this);
            family.publish();
        }
    }
    public <E extends T> CancelBus<E> child(Class<E> type) {
        return new CancelBus<>(type, family);
    }
    private EventListener add(Slot s) {
        synchronized (family) {
            int at = 0;
            while (at < family.registrations.size() && family.registrations.get(at).priority >= s.priority)
                at++;
            family.registrations.add(at, s);
            family.publish();
        }
        return new Token(this, s);
    }
    public EventListener addListener(Consumer<T> c) {
        return addListener((byte) 0, c);
    }
    public EventListener addListener(byte p, Consumer<T> c) {
        Objects.requireNonNull(c);
        if (p == Byte.MIN_VALUE)
            return addListener((v, cancelled) -> c.accept(v));
        return addListener(p, (Predicate<T>) v -> {
            c.accept(v);
            return false;
        });
    }
    public EventListener addListener(byte p, boolean always, Consumer<T> c) {
        if (!always)
            throw new IllegalArgumentException("Use Consumer overload");
        Objects.requireNonNull(c);
        return addListener(p, (Predicate<T>) v -> {
            c.accept(v);
            return true;
        });
    }
    public EventListener addListener(Predicate<T> c) {
        return addListener((byte) 0, c);
    }
    public EventListener addListener(byte p, Predicate<T> c) {
        if (p == Byte.MIN_VALUE)
            throw new IllegalArgumentException("Monitor cannot cancel");
        Objects.requireNonNull(c);
        return add(new Slot(type, p, o -> c.test(type.cast(o)), null));
    }
    public EventListener addListener(ObjBooleanBiConsumer<T> c) {
        Objects.requireNonNull(c);
        return add(new Slot(type, Byte.MIN_VALUE, null, (o, b) -> c.accept(type.cast(o), b)));
    }
    public boolean post(Event e) {
        return post(e,false);
    }
    public boolean post(Event e,boolean cancelled) {
        var matches = applicable;
        if (e.getClass() != type) {
            synchronized (family) {
                matches = family.registrations.stream().filter(s -> s.type.isInstance(e)).toList();
            }
        }
        for (var s : matches) {
            if (s.monitor != null)
                s.monitor.accept(e, cancelled);
            else if (!cancelled)
                cancelled = s.run.test(e);
        }
        return cancelled;
    }
    public Event fire(Event e) {
        post(e);
        return e;
    }
    public boolean hasListeners() {
        return !applicable.isEmpty();
    }
    public void clear() {
        synchronized (family) {
            family.registrations.removeIf(s -> s.type == type);
            family.publish();
        }
    }
    public void clearFamily() {
        synchronized (family) {
            family.registrations.clear();
            family.publish();
        }
    }
    public void removeListener(EventListener e) {
        if (e instanceof Token t && t.owner == this)
            remove(e);
    }
    public static void remove(EventListener e) {
        if (e instanceof Token t)
            synchronized (t.owner.family) {
                t.owner.family.registrations.remove(t.slot);
                t.owner.family.publish();
            }
    }
}
