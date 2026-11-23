package net.minecraftforge.event.entity;
import java.util.Map;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
public final class EntityAttributeCreationEvent extends net.minecraftforge.eventbus.api.event.MutableEvent {
    public static final net.minecraftforge.eventbus.api.bus.EventBus<EntityAttributeCreationEvent> BUS =
        new mc.unity.adapter.forge.CommandBus<>();
    private final Map<EntityType<? extends LivingEntity>, AttributeSupplier> values;
    public EntityAttributeCreationEvent(Map<EntityType<? extends LivingEntity>, AttributeSupplier> values) {
        this.values = values;
    }
    public void put(EntityType<? extends LivingEntity> type, AttributeSupplier supplier) {
        if (DefaultAttributes.hasSupplier(type) || values.putIfAbsent(type, supplier) != null)
            throw new IllegalStateException("Duplicate attributes for " + type);
    }
}
