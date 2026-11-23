package mc.unity.core;
import java.util.*;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;

/** Unity-owned, non-world registries for platform services such as attachment types and loot codecs. */
public final class ExtraRegistries {
    private static final Map<ResourceKey<?>, MappedRegistry<?>> registries = new LinkedHashMap<>();
    public static <T> Registry<T> create(ResourceKey<Registry<T>> key) {
        Content.checkOpen();
        var registry = new MappedRegistry<T>(key, Lifecycle.stable());
        if (registries.putIfAbsent(key, registry) != null)
            throw new IllegalStateException("Duplicate registry " + key);
        return registry;
    }
    static Collection<MappedRegistry<?>> all() { return registries.values(); }
    static void freeze() {
        for (var r : registries.values()) {
            r.bindAllTagsToEmpty();
            r.freeze();
            for (var value : r)
                if (value instanceof Attachments.Key<?> key)
                    Attachments.register(key);
        }
    }
    static void clear() {
        registries.clear();
        Attachments.clear();
    }
}
