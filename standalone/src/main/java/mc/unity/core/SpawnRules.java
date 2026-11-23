package mc.unity.core;

import java.lang.invoke.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.SpawnPlacements.SpawnPredicate;
import net.minecraft.world.level.levelgen.Heightmap;

/** Rebuild only modified vanilla spawn rows, after all entity types have been bound. */
public final class SpawnRules {
    public static class Row<T extends Entity> {
        private SpawnPlacementType placement;
        private Heightmap.Types height;
        private final SpawnPredicate<T> original;
        private final List<SpawnPredicate<T>> or = new ArrayList<>(), and = new ArrayList<>();
        private SpawnPredicate<T> replacement;
        private boolean changed;
        public Row(SpawnPredicate<T> predicate, SpawnPlacementType placement, Heightmap.Types height) {
            original = Objects.requireNonNull(predicate);
            this.placement = Objects.requireNonNull(placement);
            this.height = Objects.requireNonNull(height);
        }
        public SpawnPlacementType getSpawnType() { return placement; }
        public Heightmap.Types getHeightmapType() { return height; }
        public void merge(String op, SpawnPredicate<T> predicate, SpawnPlacementType newType,
                          Heightmap.Types newHeight) {
            Objects.requireNonNull(predicate);
            if (!op.equals("REPLACE") && (newType != null || newHeight != null))
                throw new IllegalStateException("Only REPLACE changes placement or heightmap");
            switch (op) {
            case "OR" -> or.add(predicate);
            case "AND" -> and.add(predicate);
            case "REPLACE" -> replacement = predicate;
            default -> throw new IllegalArgumentException(op);
            }
            if (newType != null)
                placement = newType;
            if (newHeight != null)
                height = newHeight;
            changed = true;
        }
        public SpawnPredicate<T> build() {
            if (replacement != null)
                return replacement;
            return (type, level, reason, pos, random) -> {
                boolean allowed = original.test(type, level, reason, pos, random);
                if (!allowed)
                    for (var p : or)
                        if (p.test(type, level, reason, pos, random)) {
                            allowed = true;
                            break;
                        }
                if (allowed)
                    for (var p : and)
                        if (!p.test(type, level, reason, pos, random))
                            return false;
                return allowed;
            };
        }
    }
    public interface Factory<R extends Row<?>> {
        R make(SpawnPredicate<?> predicate, SpawnPlacementType placement, Heightmap.Types height);
    }
    private static final Map<EntityType<?>, Object> before = new IdentityHashMap<>();
    private static Map<EntityType<?>, Object> vanilla;
    @SuppressWarnings("unchecked")
    public static <R extends Row<?>> void edit(Factory<R> factory, Consumer<Map<EntityType<?>, R>> dispatch) {
        try {
            var lookup = MethodHandles.privateLookupIn(SpawnPlacements.class, MethodHandles.lookup());
            var data = lookup.findClass(SpawnPlacements.class.getName() + "$Data");
            vanilla = (Map<EntityType<?>, Object>)lookup
                          .findStaticVarHandle(SpawnPlacements.class, "DATA_BY_TYPE", Map.class)
                          .get();
            var read = lookup.findVirtual(data, "predicate", MethodType.methodType(SpawnPredicate.class));
            var create = lookup.findConstructor(data, MethodType.methodType(void.class, Heightmap.Types.class,
                                                                            SpawnPlacementType.class,
                                                                            SpawnPredicate.class));
            var rows = new LinkedHashMap<EntityType<?>, R>();
            for (var entry : vanilla.entrySet()) {
                var type = entry.getKey();
                rows.put(type, factory.make((SpawnPredicate<?>)read.invoke(entry.getValue()),
                                            SpawnPlacements.getPlacementType(type),
                                            SpawnPlacements.getHeightmapType(type)));
            }
            dispatch.accept(rows);
            for (var e : rows.entrySet()) {
                Row<?> row = e.getValue();
                if (!row.changed && vanilla.containsKey(e.getKey()))
                    continue;
                if (!before.containsKey(e.getKey()))
                    before.put(e.getKey(), vanilla.get(e.getKey()));
                vanilla.put(e.getKey(), create.invoke(row.height, row.placement, row.build()));
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("26.2 spawn placement registry", e);
        }
    }
    static void clear() {
        if (vanilla != null)
            before.forEach((key, value) -> {
                if (value == null)
                    vanilla.remove(key);
                else
                    vanilla.put(key, value);
            });
        before.clear();
        vanilla = null;
    }
}
