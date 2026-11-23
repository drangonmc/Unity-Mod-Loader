package net.minecraftforge.common.util;
import java.util.*;
public class LazyOptional<T> {
    private static final LazyOptional<?> EMPTY = new LazyOptional<>(null);
    private NonNullSupplier<T> supplier;
    private T value;
    private final Set<NonNullConsumer<LazyOptional<T>>> listeners = new LinkedHashSet<>();
    private boolean valid;
    private LazyOptional(NonNullSupplier<T> supplier) {
        this.supplier = supplier;
        valid = supplier != null;
    }
    public static <T> LazyOptional<T> of(NonNullSupplier<T> supplier) {
        return new LazyOptional<>(Objects.requireNonNull(supplier));
    }
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> empty() {
        return (LazyOptional<T>)EMPTY;
    }
    @SuppressWarnings("unchecked")
    public <X> LazyOptional<X> cast() {
        return (LazyOptional<X>)this;
    }
    public synchronized boolean isPresent() { return valid; }
    public synchronized Optional<T> resolve() {
        if (!valid)
            return Optional.empty();
        if (supplier != null) {
            value = Objects.requireNonNull(supplier.get());
            supplier = null;
        }
        return Optional.of(value);
    }
    public T orElse(T other) { return resolve().orElse(other); }
    public T orElseGet(NonNullSupplier<? extends T> other) { return resolve().orElseGet(other::get); }
    public <X extends Throwable> T orElseThrow(NonNullSupplier<? extends X> error) throws X {
        return resolve().orElseThrow(error::get);
    }
    public void ifPresent(NonNullConsumer<? super T> consumer) { resolve().ifPresent(consumer::accept); }
    public <U> Optional<U> map(NonNullFunction<? super T, ? extends U> function) {
        return resolve().map(function::apply);
    }
    public Optional<T> filter(NonNullPredicate<? super T> predicate) {
        return resolve().filter(predicate::test);
    }
    public void addListener(NonNullConsumer<LazyOptional<T>> listener) {
        synchronized (this) {
            if (valid) {
                listeners.add(listener);
                return;
            }
        }
        listener.accept(this);
    }
    public synchronized void removeListener(NonNullConsumer<LazyOptional<T>> listener) {
        listeners.remove(listener);
    }
    public void invalidate() {
        List<NonNullConsumer<LazyOptional<T>>> callbacks;
        synchronized (this) {
            if (!valid)
                return;
            valid = false;
            value = null;
            supplier = null;
            callbacks = List.copyOf(listeners);
            listeners.clear();
        }
        RuntimeException error = null;
        for (var cb : callbacks)
            try {
                cb.accept(this);
            } catch (RuntimeException e) {
                if (error == null)
                    error = e;
                else
                    error.addSuppressed(e);
            }
        if (error != null)
            throw error;
    }
}
