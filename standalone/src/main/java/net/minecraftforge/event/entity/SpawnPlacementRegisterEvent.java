package net.minecraftforge.event.entity;
import java.util.*;
import mc.unity.core.SpawnRules;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.SpawnPlacements.SpawnPredicate;
import net.minecraft.world.level.levelgen.Heightmap;
public final class SpawnPlacementRegisterEvent extends net.minecraftforge.eventbus.api.event.MutableEvent {
    public static final net.minecraftforge.eventbus.api.bus.EventBus<SpawnPlacementRegisterEvent> BUS =
        new mc.unity.adapter.forge.CommandBus<>();
    public enum Operation { AND, OR, REPLACE }
    public static class MergedSpawnPredicate<T extends Entity> extends SpawnRules.Row<T> {
        public MergedSpawnPredicate(SpawnPredicate<T> p, SpawnPlacementType type, Heightmap.Types height) {
            super(p, type, height);
        }
    }
    private final Map<EntityType<?>, MergedSpawnPredicate<?>> rules;
    public SpawnPlacementRegisterEvent(Map<EntityType<?>, MergedSpawnPredicate<?>> rules) {
        this.rules = rules;
    }
    public <T extends Entity> void register(EntityType<T> t, SpawnPredicate<T> p) {
        register(t, p, Operation.OR);
    }
    public <T extends Entity> void register(EntityType<T> t, SpawnPredicate<T> p, Operation op) {
        register(t, null, null, p, op);
    }
    @SuppressWarnings("unchecked")
    public <T extends Entity> void register(EntityType<T> t, SpawnPlacementType type, Heightmap.Types height,
                                            SpawnPredicate<T> p, Operation op) {
        var current = (MergedSpawnPredicate<T>)rules.get(t);
        if (current == null)
            rules.put(t, new MergedSpawnPredicate<>(p, type, height));
        else
            current.merge(op.name(), p, type, height);
    }
}
