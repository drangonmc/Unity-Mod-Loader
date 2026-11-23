package net.fabricmc.fabric.api.entity.event.v1;
import mc.unity.core.Events;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
public final class ServerLivingEntityEvents {
    public interface AfterDeath {
        void afterDeath(LivingEntity entity, DamageSource source);
    }
    public static final Event<AfterDeath> AFTER_DEATH = new Event<>() {
        public void register(AfterDeath listener) {
            Events.AFTER_DEATH.listen(d -> listener.afterDeath(d.entity(), d.source()));
        }
    };
}
