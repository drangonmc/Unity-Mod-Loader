package net.minecraftforge.event.server;
import mc.unity.adapter.forge.CommandBus;
import net.minecraftforge.eventbus.api.bus.EventBus;
public record ServerStoppedEvent(net.minecraft.server.MinecraftServer getServer)
    implements ServerLifecycleEvent {
    public static final EventBus<ServerStoppedEvent> BUS = new CommandBus<>();
}
