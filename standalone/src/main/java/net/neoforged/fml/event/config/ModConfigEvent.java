package net.neoforged.fml.event.config;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.IModBusEvent;
public abstract class ModConfigEvent extends Event implements IModBusEvent {
    private final ModConfig config;
    protected ModConfigEvent(ModConfig config) { this.config = config; }
    public ModConfig getConfig() { return config; }
    public static final class Loading extends ModConfigEvent {
        public Loading(ModConfig c) { super(c); }
    }
    public static final class Reloading extends ModConfigEvent {
        public Reloading(ModConfig c) { super(c); }
    }
    public static final class Unloading extends ModConfigEvent {
        public Unloading(ModConfig c) { super(c); }
    }
}
