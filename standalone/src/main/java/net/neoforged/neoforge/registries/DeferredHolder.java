package net.neoforged.neoforge.registries;
import java.util.function.Supplier;
import net.minecraft.resources.*;
public class DeferredHolder<R, T extends R> implements net.minecraft.core.Holder<R>, Supplier<T> {
    final ResourceKey<R> key;
    final Supplier<? extends T> factory;
    T value;
    private net.minecraft.core.Holder<R> delegate;
    public net.minecraft.core.Holder<R> getDelegate() {
        return java.util.Objects.requireNonNull(delegate, "Unbound " + key);
    }
    public boolean areComponentsBound() {
        return delegate != null && delegate.areComponentsBound();
    }
    public boolean is(Identifier id) {
        return key.identifier().equals(id);
    }
    public boolean is(ResourceKey<R> other) {
        return key.equals(other);
    }
    public boolean is(java.util.function.Predicate<ResourceKey<R>> fn) {
        return fn.test(key);
    }
    public boolean is(net.minecraft.tags.TagKey<R> tag) {
        return getDelegate().is(tag);
    }
    public boolean is(net.minecraft.core.Holder<R> h) {
        return h == this || h.unwrapKey().filter(key::equals).isPresent();
    }
    public java.util.stream.Stream<net.minecraft.tags.TagKey<R>> tags() {
        return getDelegate().tags();
    }
    public net.minecraft.core.component.DataComponentMap components() {
        return getDelegate().components();
    }
    public com.mojang.datafixers.util.Either<ResourceKey<R>, R> unwrap() {
        return com.mojang.datafixers.util.Either.left(key);
    }
    public java.util.Optional<ResourceKey<R>> unwrapKey() {
        return java.util.Optional.of(key);
    }
    public Kind kind() {
        return Kind.REFERENCE;
    }
    public boolean canSerializeIn(net.minecraft.core.HolderOwner<R> o) {
        return getDelegate().canSerializeIn(o);
    }
    public java.util.Optional<T> asOptional() {
        return java.util.Optional.ofNullable(value);
    }

    DeferredHolder(ResourceKey<R> key, Supplier<? extends T> factory) {
        this.key = key;
        this.factory = factory;
    }
    public T get() {
        return java.util.Objects.requireNonNull(value, "Unbound " + key);
    }
    public T value() {
        return get();
    }
    public boolean isBound() {
        return value != null;
    }
    public Identifier getId() {
        return key.identifier();
    }
    public ResourceKey<R> getKey() {
        return key;
    }
    void bind(net.minecraft.core.Registry<R> registry) {
        if (value != null || registry.containsKey(getId()))
            throw new IllegalStateException("Duplicate " + key);
        T v = factory.get();
        net.minecraft.core.Registry.register(registry, key, v);
        delegate = registry.get(key).orElseThrow();
        value = v;
    }
}
