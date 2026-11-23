package net.minecraftforge.event.server;
import mc.unity.adapter.forge.CommandBus;
import net.minecraftforge.eventbus.api.bus.EventBus;
public record ServerStartingEvent(net.minecraft.server.MinecraftServer getServer)
    implements ServerLifecycleEvent {
    public static final EventBus<ServerStartingEvent> BUS = new CommandBus<>();
}
