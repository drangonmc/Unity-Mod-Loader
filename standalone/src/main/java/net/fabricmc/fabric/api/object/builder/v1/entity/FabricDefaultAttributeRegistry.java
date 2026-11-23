package net.fabricmc.fabric.api.object.builder.v1.entity;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
public final class FabricDefaultAttributeRegistry {
    public static void register(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder builder) {
        register(type, builder.build());
    }
    public static void register(EntityType<? extends LivingEntity> type, AttributeSupplier attributes) {
        mc.unity.core.EntityAttributes.register(type, attributes);
    }
}
