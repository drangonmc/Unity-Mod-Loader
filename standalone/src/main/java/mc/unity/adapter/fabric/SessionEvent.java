package mc.unity.adapter.fabric;
import java.util.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.*;
import net.minecraft.resources.Identifier;

/** Use Fabric's array-backed invoker and phase ordering, with a process-session reset. */
public final class SessionEvent<T> extends Event<T> {
    private static final Set<SessionEvent<?>> events = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Class<T> type;
    private final Function<T[], T> factory;
    private Event<T> delegate;
    private final Runnable onFirst;
    private volatile boolean subscribed;
    public SessionEvent(Class<T> type, Function<T[], T> factory) {
        this(type,factory,()->{});
    }
    public SessionEvent(Class<T> type,Function<T[],T> factory,Runnable onFirst){
        this.onFirst=Objects.requireNonNull(onFirst);
        this.type = type;
        this.factory = factory;
        reset();
        synchronized (events) { events.add(this); }
    }
    public synchronized void register(T callback) { register(DEFAULT_PHASE, callback); }
    public synchronized void register(Identifier phase, T callback) {
        delegate.register(phase, callback);
        invoker = delegate.invoker();
        if(!subscribed)onFirst.run();
        subscribed = true;
    }
    public synchronized void addPhaseOrdering(Identifier a, Identifier b) {
        delegate.addPhaseOrdering(a, b);
        invoker = delegate.invoker();
    }
    public boolean subscribed() { return subscribed; }
    private synchronized void reset() {
        delegate = EventFactory.createArrayBacked(type, factory);
        invoker = delegate.invoker();
        subscribed = false;
    }
    public static void clear() {
        synchronized (events) { events.forEach(SessionEvent::reset); }
    }
}
