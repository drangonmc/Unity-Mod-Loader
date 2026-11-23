package net.minecraftforge.registries;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
public final class RegistryObject<T> implements Supplier<T> {
    private final ResourceKey<T> key;
    private final Supplier<? extends T> factory;
    private T value;
    RegistryObject(ResourceKey<T> key, Supplier<? extends T> factory) {
        this.key = key;
        this.factory = factory;
    }
    public T get() {
        if (value == null)
            throw new IllegalStateException("Unbound registry object " + key);
        return value;
    }
    public boolean isPresent() {
        return value != null;
    }
    private net.minecraft.core.Holder<T> holder;
    public java.util.Optional<net.minecraft.core.Holder<T>> getHolder() {
        return java.util.Optional.ofNullable(holder);
    }
    public ResourceKey<T> getKey() {
        return key;
    }
    public Identifier getId() {
        return key.identifier();
    }
    void bind(Registry<? super T> registry) {
        if (value != null || registry.containsKey(getId()))
            throw new IllegalStateException("Duplicate entry " + key);
        T object = java.util.Objects.requireNonNull(factory.get());
        Registry.register(registry, getId(), object);
        value = object;
        holder = (net.minecraft.core.Holder<T>) registry.get(getId()).orElseThrow();
    }
}
