package net.minecraftforge.fml;
import java.util.Objects;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.config.*;
public class ModLoadingContext {
    private static final ThreadLocal<ModLoadingContext> active = new ThreadLocal<>();
    private final BusGroup group;
    protected ModLoadingContext(BusGroup group) { this.group = Objects.requireNonNull(group); }
    public static ModLoadingContext get() {
        return Objects.requireNonNull(active.get(), "No active Forge mod constructor");
    }
    public static ModLoadingContext enter(ModLoadingContext next) {
        var previous = active.get();
        if (next == null)
            active.remove();
        else
            active.set(next);
        return previous;
    }
    public String getActiveNamespace() { return group.name(); }
    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec) {
        registerConfig(type, spec, group.name() + "-" + type.extension() + ".toml");
    }
    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec, String filename) {
        new ModConfig(type, spec, group, filename);
    }
}
