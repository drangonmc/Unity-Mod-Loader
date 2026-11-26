package net.minecraftforge.client.event;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import mc.unity.adapter.forge.CommandBus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import net.minecraftforge.eventbus.api.event.characteristic.SelfDestructing;

public final class RegisterKeyMappingsEvent extends MutableEvent implements SelfDestructing {
    private static final CommandBus<RegisterKeyMappingsEvent> listeners = new CommandBus<>();
    public static final EventBus<RegisterKeyMappingsEvent> BUS = listeners;
    private static final VarHandle KEYS;
    static {
        try {
            KEYS =
                MethodHandles.publicLookup().findVarHandle(Options.class, "keyMappings", KeyMapping[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private final Options options;
    public RegisterKeyMappingsEvent(Options options) {
        this.options = options;
    }
    public void register(KeyMapping mapping) {
        var keys = options.keyMappings;
        var next = Arrays.copyOf(keys, keys.length + 1);
        next[keys.length] = Objects.requireNonNull(mapping);
        KEYS.set(options, next);
    }
    public static void fire(Options options) {
        try {
            listeners.dispatch(new RegisterKeyMappingsEvent(options));
        } finally {
            clear();
        }
    }
    public static void clear() {
        listeners.clear();
    }
}
