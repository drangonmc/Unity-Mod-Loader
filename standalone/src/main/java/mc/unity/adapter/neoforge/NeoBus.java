package mc.unity.adapter.neoforge;
import java.util.*;
import java.util.function.Consumer;
import mc.unity.core.Events;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class NeoBus implements IEventBus {
    private final String owner;
    private final Events.Signal<Event> handlers = new Events.Signal<>();
    private final Map<Class<?>, Runnable> bridges = new HashMap<>();
    private static final Set<NeoBus> sessions = Collections.newSetFromMap(new IdentityHashMap<>());
    public NeoBus(String owner) {
        this.owner = owner;
    }
    public String owner() {
        return owner;
    }
    public <T extends Event> void addListener(EventPriority priority, boolean receive, Class<T> kind,
                                              Consumer<T> cb) {
        Objects.requireNonNull(cb);
        synchronized (sessions) {
            sessions.add(this);
            boolean supported = false;
            if (owner == null && kind.isAssignableFrom(AddServerReloadListenersEvent.class)) {
                bridges.computeIfAbsent(AddServerReloadListenersEvent.class,
                                        k -> mc.unity.core.Reloads.COLLECT.listen((byte)1, r -> {
                                            var event = new AddServerReloadListenersEvent(
                                                r.resources(), r.registries(), new HashMap<>());
                                            post(event);
                                            r.listeners().clear();
                                            r.listeners().addAll(event.unityOrder());
                                        }));
                supported = true;
            }
            if (owner == null && kind.isAssignableFrom(RegisterCommandsEvent.class)) {
                bridges.computeIfAbsent(RegisterCommandsEvent.class,
                                        k -> Events.COMMANDS.listen(e -> post(new RegisterCommandsEvent(e))));
                supported = true;
            }
            if (owner != null && kind.isAssignableFrom(BuildCreativeModeTabContentsEvent.class)) {
                bridges.computeIfAbsent(
                    BuildCreativeModeTabContentsEvent.class,
                    k -> Events.CREATIVE.listen(e -> post(new BuildCreativeModeTabContentsEvent(e))));
                supported = true;
            }
            if (owner != null &&
                kind.isAssignableFrom(
                    net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent.class)) {
                bridges.computeIfAbsent(
                    net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent.class,
                    k -> mc.unity.core.ConfigurationNetwork.CONFIGURE.listen(peer -> {
                        var event = new net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent(
                            (net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener)
                                peer);
                        post(event);
                        event.getConfigurationTasks().forEach(
                            task -> mc.unity.core.ConfigurationNetwork.addTask(peer, task));
                    }));
                supported = true;
            }
            if (owner != null &&
                kind.isAssignableFrom(
                    net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent.class)) {
                bridges.computeIfAbsent(
                    net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent.class,
                    k
                    -> Events.CLIENT_SETUP.listen(v
                                                  -> post(new net.neoforged.neoforge.client.network.event
                                                              .RegisterClientPayloadHandlersEvent())));
                supported = true;
            }
            if (owner != null && kind.isAssignableFrom(RegisterPayloadHandlersEvent.class)) {
                bridges.computeIfAbsent(
                    RegisterPayloadHandlersEvent.class,
                    k -> Events.PLAY_REGISTRATION.listen(v -> post(new RegisterPayloadHandlersEvent())));
                supported = true;
            }
            if (!supported &&
                !(owner != null &&
                  (net.neoforged.fml.event.config.ModConfigEvent.class.isAssignableFrom(kind) ||
                   net.neoforged.fml.event.lifecycle.ParallelDispatchEvent.class.isAssignableFrom(kind) ||
                   net.neoforged.neoforge.data.event.GatherDataEvent.class.isAssignableFrom(kind) ||
                   kind.getName().equals(
                       "net.neoforged.neoforge.client.event.EntityRenderersEvent$RegisterLayerDefinitions") ||
                   kind.getName().equals(
                       "net.neoforged.neoforge.client.event.EntityRenderersEvent$RegisterRenderers") ||
                   kind.getName().equals("net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent") ||
                   kind == net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.class ||
                   kind == net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent.class ||
                   kind == net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent.class ||
                   kind == net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry.class ||
                   kind == net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent.class)) &&
                kind.getName().startsWith("net.neoforged."))
                throw new IllegalArgumentException("Unsupported NeoForge event or bus: " + kind);
            handlers.listen((byte)(2 - priority.ordinal()), e -> {
                if (!kind.isInstance(e) || !receive && e instanceof ICancellableEvent c && c.isCanceled())
                    return;
                if (owner == null) {
                    cb.accept(kind.cast(e));
                    return;
                }
                var container = net.neoforged.fml.ModList.get().getModContainerById(owner).orElse(null);
                var previous = net.neoforged.fml.ModLoadingContext.enter(container);
                try {
                    cb.accept(kind.cast(e));
                } finally {
                    net.neoforged.fml.ModLoadingContext.enter(previous);
                }
            });
        }
    }
    public <T extends Event> T post(T event) {
        handlers.post(event);
        return event;
    }
    public static void clear() {
        synchronized (sessions) {
            for (var bus : sessions) {
                bus.handlers.clear();
                bus.bridges.values().forEach(Runnable::run);
                bus.bridges.clear();
            }
            sessions.clear();
        }
        ListenerTypes.clear();
    }
}
