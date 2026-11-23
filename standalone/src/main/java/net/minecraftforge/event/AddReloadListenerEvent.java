package net.minecraftforge.event;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraftforge.eventbus.api.bus.EventBus;
import mc.unity.adapter.forge.CommandBus;
public final class AddReloadListenerEvent extends net.minecraftforge.eventbus.api.event.MutableEvent {
    public static final EventBus<AddReloadListenerEvent> BUS = new CommandBus<>();
    private final ReloadableServerResources resources;
    private final HolderLookup.Provider lookup;
    private final List<PreparableReloadListener> listeners = new ArrayList<>();
    public AddReloadListenerEvent(ReloadableServerResources resources, HolderLookup.Provider lookup) {
        this.resources = resources;
        this.lookup = lookup;
    }
    public void addListener(PreparableReloadListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }
    public List<PreparableReloadListener> getListeners() { return List.copyOf(listeners); }
    public ReloadableServerResources getServerResources() { return resources; }
    public HolderLookup.Provider getRegistries() { return lookup; }
}
