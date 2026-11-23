package net.minecraftforge.fml.event.config;
import java.util.*;
import mc.unity.adapter.forge.CommandBus;
import net.minecraftforge.eventbus.api.bus.*;
import net.minecraftforge.fml.config.ModConfig;

public interface ModConfigEvent
    extends net.minecraftforge.fml.event.IModBusEvent, net.minecraftforge.fml.config.IConfigEvent {
    record Loading(ModConfig getConfig) implements ModConfigEvent {
        public static EventBus<Loading> getBus(BusGroup group) { return Buses.forType(group, Loading.class); }
    }
    record Reloading(ModConfig getConfig) implements ModConfigEvent {
        public static EventBus<Reloading> getBus(BusGroup group) {
            return Buses.forType(group, Reloading.class);
        }
    }
    record Unloading(ModConfig getConfig) implements ModConfigEvent {
        public static EventBus<Unloading> getBus(BusGroup group) {
            return Buses.forType(group, Unloading.class);
        }
    }
    final class Buses {
        private static final Map<BusGroup, Map<Class<?>, CommandBus<?>>> groups = new IdentityHashMap<>();
        @SuppressWarnings("unchecked")
        static synchronized<E> CommandBus<E> forType(BusGroup group, Class<E> type) {
            return (CommandBus<E>)groups.computeIfAbsent(group, k -> new HashMap<>())
                .computeIfAbsent(type, k -> new CommandBus<>());
        }
        public static synchronized void clear() {
            groups.values().forEach(map -> map.values().forEach(CommandBus::clear));
            groups.clear();
        }
    }
}
