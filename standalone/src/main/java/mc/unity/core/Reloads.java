package mc.unity.core;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
public record Reloads(ReloadableServerResources resources, RegistryAccess registries,
                      List<PreparableReloadListener> listeners) {
    public static final Events.Signal<Reloads> COLLECT = new Events.Signal<>();
    public static List<PreparableReloadListener> collect(ReloadableServerResources resources,
                                                         RegistryAccess registries) {
        var vanilla = resources.listeners();
        if (COLLECT.empty())
            return vanilla;
        var batch = new Reloads(resources, registries, new ArrayList<>(vanilla));
        COLLECT.post(batch);
        return List.copyOf(batch.listeners);
    }
}
