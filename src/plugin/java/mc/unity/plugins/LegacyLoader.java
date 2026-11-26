package mc.unity.plugins;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.event.*;
import org.bukkit.plugin.*;

/** Provider used by Paper's JavaPlugin#getPluginLoader compatibility accessor. */
public final class LegacyLoader implements PluginLoader {
    public Plugin loadPlugin(File file) throws InvalidPluginException {
        throw new InvalidPluginException("Unity loads plugins during dedicated-server startup");
    }
    public PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
        try {
            return PluginFile.read(file.toPath()).description();
        } catch (Exception e) {
            throw new InvalidDescriptionException(e);
        }
    }
    public Pattern[] getPluginFileFilters() {
        return new Pattern[] {Pattern.compile("(?i)\\.jar$")};
    }
    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener l,
                                                                                          Plugin p) {
        return PluginEvents.declared(l, p);
    }
    public void enablePlugin(Plugin p) {
        throw Views.unsupported("PluginLoader", "runtime enablePlugin");
    }
    public void disablePlugin(Plugin p) {
        throw Views.unsupported("PluginLoader", "runtime disablePlugin");
    }
}
