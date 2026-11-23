package net.minecraftforge.event.entity.player;
import mc.unity.adapter.forge.CommandBus;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.bus.EventBus;
public interface PlayerEvent extends net.minecraftforge.event.entity.living.LivingEvent {
    Player getEntity();
    record PlayerLoggedInEvent(Player getEntity)
        implements PlayerEvent, net.minecraftforge.eventbus.api.event.RecordEvent {
        public static final EventBus<PlayerLoggedInEvent> BUS = new CommandBus<>();
    }
    record PlayerLoggedOutEvent(Player getEntity)
        implements PlayerEvent, net.minecraftforge.eventbus.api.event.RecordEvent {
        public static final EventBus<PlayerLoggedOutEvent> BUS = new CommandBus<>();
    }
}
