package net.minecraftforge.event.server;
import mc.unity.adapter.forge.CommandBus;
import net.minecraftforge.eventbus.api.bus.EventBus;
public interface ServerLifecycleEvent extends net.minecraftforge.eventbus.api.event.InheritableEvent {
    EventBus<ServerLifecycleEvent> BUS = new CommandBus<>();
    net.minecraft.server.MinecraftServer getServer();
}
