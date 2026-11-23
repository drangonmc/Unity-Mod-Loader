package mc.unity.core;

import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;

/** Mod entity defaults share one registry on both physical sides. */
public final class EntityAttributes {
    public static final Events
        .Signal<Map<EntityType<? extends LivingEntity>, AttributeSupplier>> REGISTRATION =
        new Events.Signal<>();
    private static final Map<EntityType<? extends LivingEntity>, AttributeSupplier> pending =
        new LinkedHashMap<>();
    private static volatile Map<EntityType<? extends LivingEntity>, AttributeSupplier> active = Map.of();
    private static boolean frozen;
    public static void register(EntityType<? extends LivingEntity> type, AttributeSupplier supplier) {
        if (frozen)
            throw new IllegalStateException("Entity attributes are frozen");
        pending.put(Objects.requireNonNull(type), Objects.requireNonNull(supplier));
    }
    static void finish() {
        REGISTRATION.post(pending);
        active = Map.copyOf(pending);
        frozen = true;
    }
    public static Object lookup(Map<?, ?> vanilla, Object type) {
        var custom = active.get(type);
        return custom == null ? vanilla.get(type) : custom;
    }
    public static boolean contains(Map<?, ?> vanilla, Object type) {
        return active.containsKey(type) || vanilla.containsKey(type);
    }
    static void clear() {
        REGISTRATION.clear();
        pending.clear();
        active = Map.of();
        frozen = false;
    }
}
