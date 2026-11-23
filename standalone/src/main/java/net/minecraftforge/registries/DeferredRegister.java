package net.minecraftforge.registries;
import java.util.*;
import java.util.function.Supplier;
import mc.unity.core.Content;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import net.minecraftforge.eventbus.api.bus.BusGroup;
public final class DeferredRegister<T> {
    public static final class RegistryHolder<V> implements Supplier<IForgeRegistry<V>> {
        private final IForgeRegistry<V> registry;
        RegistryHolder(IForgeRegistry<V> registry){this.registry=registry;}
        public ResourceKey<? extends Registry<V>> getKey(){return registry.getRegistryKey();}
        public IForgeRegistry<V> get(){return registry;}
    }
    private final ResourceKey<? extends Registry<T>> type;
    private final String namespace;
    private final Map<String, RegistryObject<? extends T>> entries = new LinkedHashMap<>();
    private DeferredRegister(ResourceKey<? extends Registry<T>> type, String namespace) {
        this.type = type;
        this.namespace = namespace;
    }
    public static <T> DeferredRegister<T> create(IForgeRegistry<T> view, String ns) {
        return create(view.getRegistryKey(), ns);
    }
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> type, String ns) {
        return new DeferredRegister<>(type, ns);
    }
    public ResourceKey<T> key(String name) {
        return ResourceKey.create(type, Identifier.fromNamespaceAndPath(namespace, name));
    }
    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> factory) {
        Content.checkOpen();
        ResourceKey<I> key =
            ResourceKey.create((ResourceKey) type, Identifier.fromNamespaceAndPath(namespace, name));
        var entry = new RegistryObject<I>(key, factory);
        if (entries.putIfAbsent(name, entry) != null)
            throw new IllegalStateException("Duplicate " + key);
        return entry;
    }
    public void register(BusGroup group) {
        Content.attach(group.name(), type, registry -> bind((Registry<T>) registry));
    }
    public void bind(Registry<T> registry) {
        for (var object : entries.values()) object.bind(registry);
    }
}
