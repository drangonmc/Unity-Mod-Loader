package net.neoforged.neoforge.event;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.neoforged.neoforge.resource.VanillaServerListeners;
public class AddServerReloadListenersEvent extends SortedReloadListenerEvent {
    private final ReloadableServerResources resources;
    private final RegistryAccess registries;
    public AddServerReloadListenersEvent(ReloadableServerResources resources, RegistryAccess registries,
                                         java.util.Map<?, ?> retained) {
        super(resources.listeners(), listener -> VanillaServerListeners.getNameForClass(listener.getClass()));
        this.resources = resources;
        this.registries = registries;
    }
    public ReloadableServerResources getServerResources() { return resources; }
    public RegistryAccess getRegistryAccess() { return registries; }
}
