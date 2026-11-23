package net.minecraftforge.event.entity.living;
public interface LivingEvent extends net.minecraftforge.event.entity.EntityEvent {
    net.minecraft.world.entity.LivingEntity getEntity();
}
