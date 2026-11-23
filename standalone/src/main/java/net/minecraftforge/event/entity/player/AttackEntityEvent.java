package net.minecraftforge.event.entity.player;
import mc.unity.adapter.forge.CancelBus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.*;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
public record AttackEntityEvent(Player getEntity, Entity getTarget)
    implements PlayerEvent, RecordEvent, Cancellable {
    public static final CancellableEventBus<AttackEntityEvent> BUS = new CancelBus<>(AttackEntityEvent.class);
}
