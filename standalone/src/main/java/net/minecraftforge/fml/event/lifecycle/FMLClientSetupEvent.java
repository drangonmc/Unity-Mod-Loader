package net.minecraftforge.fml.event.lifecycle;
import java.util.*;
import java.util.concurrent.*;
import mc.unity.adapter.forge.CommandBus;
import net.minecraftforge.eventbus.api.bus.*;
public final class FMLClientSetupEvent {
    private static final Map<BusGroup, CommandBus<FMLClientSetupEvent>> buses = new LinkedHashMap<>();
    public static EventBus<FMLClientSetupEvent> getBus(BusGroup group) {
        return buses.computeIfAbsent(group, k -> new CommandBus<>());
    }
    public static void fire() {
        var event = new FMLClientSetupEvent();
        buses.values().forEach(bus -> bus.dispatch(event));
    }
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        work.run();
        return CompletableFuture.completedFuture(null);
    }
    public static void clear() {
        buses.values().forEach(CommandBus::clear);
        buses.clear();
    }
}
