package net.neoforged.neoforge.event.entity;
import java.util.Map;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
public class EntityAttributeCreationEvent
    extends net.neoforged.bus.api.Event implements net.neoforged.fml.event.IModBusEvent {

    private final Map<EntityType<? extends LivingEntity>, AttributeSupplier> values;
    public EntityAttributeCreationEvent(Map<EntityType<? extends LivingEntity>, AttributeSupplier> values) {
        this.values = values;
    }
    public void put(EntityType<? extends LivingEntity> type, AttributeSupplier supplier) {
        if (DefaultAttributes.hasSupplier(type) || values.putIfAbsent(type, supplier) != null)
            throw new IllegalStateException("Duplicate attributes for " + type);
    }
}
