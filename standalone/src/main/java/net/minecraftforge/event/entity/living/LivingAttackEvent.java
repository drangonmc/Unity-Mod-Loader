package net.minecraftforge.event.entity.living;
import mc.unity.adapter.forge.CancelBus;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.*;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
public record LivingAttackEvent(LivingEntity getEntity, DamageSource getSource, float getAmount)
    implements LivingEvent, RecordEvent, Cancellable {
    public static final CancellableEventBus<LivingAttackEvent> BUS = new CancelBus<>(LivingAttackEvent.class);
}
