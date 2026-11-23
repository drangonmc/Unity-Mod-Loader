package net.minecraftforge.fml.config;

import java.nio.file.Path;
import java.util.Locale;
import com.electronwill.nightconfig.core.*;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public final class ModConfig {
    public enum Type {
        COMMON,
        CLIENT,
        SERVER;
        public String extension() { return name().toLowerCase(Locale.ROOT); }
    }
    private final IConfigSpec<?> spec;
    private final Type type;
    private final String owner, name;
    private net.neoforged.fml.config.ModConfig backing;
    public ModConfig(Type type, IConfigSpec<?> spec, BusGroup group, String name) {
        this.type = type;
        this.spec = spec;
        this.owner = group.name();
        this.name = name;
        var adapter = new net.neoforged.fml.config.IConfigSpec() {
            public boolean isEmpty() { return spec.isEmpty(); }
            public void validateSpec(net.neoforged.fml.config.ModConfig config) { backing = config; }
            public boolean isCorrect(UnmodifiableCommentedConfig values) {
                return spec.isCorrect((CommentedConfig)values);
            }
            public void correct(CommentedConfig values) { spec.correct(values); }
            public void acceptConfig(ILoadedConfig loaded) {
                if (spec instanceof net.minecraftforge.common.ForgeConfigSpec forge)
                    forge.unitySave(loaded == null ? null : loaded::save);
                spec.acceptConfig(loaded == null ? null : loaded.config());
                if (loaded != null)
                    spec.afterReload();
            }
        };
        net.neoforged.fml.config.ModConfigs.register(
            owner, net.neoforged.fml.config.ModConfig.Type.valueOf(type.name()), adapter, name, event -> {
                if (event instanceof net.neoforged.fml.event.config.ModConfigEvent.Loading)
                    ModConfigEvent.Loading.getBus(group).post(new ModConfigEvent.Loading(this));
                else if (event instanceof net.neoforged.fml.event.config.ModConfigEvent.Reloading)
                    ModConfigEvent.Reloading.getBus(group).post(new ModConfigEvent.Reloading(this));
                else
                    ModConfigEvent.Unloading.getBus(group).post(new ModConfigEvent.Unloading(this));
            });
    }
    public String getModId() { return owner; }
    public String getFileName() { return name; }
    public Type getType() { return type; }
    @SuppressWarnings("unchecked")
    public <T extends IConfigSpec<T>> IConfigSpec<T> getSpec() {
        return (IConfigSpec<T>)spec;
    }
    public CommentedConfig getConfigData() {
        var loaded = backing.getLoadedConfig();
        return loaded == null ? null : loaded.config();
    }
    public Path getFullPath() { return backing.getFullPath(); }
    public void save() { backing.getLoadedConfig().save(); }
}
