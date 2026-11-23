package net.minecraftforge.event.entity.living;
import mc.unity.adapter.forge.CancelBus;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
public final class LivingHurtEvent extends MutableEvent implements LivingEvent, Cancellable {
    public static final CancellableEventBus<LivingHurtEvent> BUS = new CancelBus<>(LivingHurtEvent.class);
    private final LivingEntity entity;
    private final DamageSource source;
    private float amount;
    public LivingHurtEvent(LivingEntity entity, DamageSource source, float amount) {
        this.entity = entity;
        this.source = source;
        this.amount = amount;
    }
    public LivingEntity getEntity() {
        return entity;
    }
    public DamageSource getSource() {
        return source;
    }
    public float getAmount() {
        return amount;
    }
    public void setAmount(float v) {
        amount = v;
    }
}
