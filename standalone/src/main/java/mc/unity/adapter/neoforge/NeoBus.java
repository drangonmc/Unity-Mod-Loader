package mc.unity.adapter.neoforge;
import java.util.*;
import java.util.function.Consumer;
import mc.unity.core.Events;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.server.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class NeoBus implements IEventBus {
    private final String owner;
    private static final Set<String> clientCallbacks=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static boolean clientEvent(Class<?> type){return clientCallbacks.contains(type.getName());}

    private final Events.Signal<Event> handlers = new Events.Signal<>();
    private final Map<Class<?>, Runnable> bridges = new HashMap<>();
    private static final Set<NeoBus> sessions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Object, List<Runnable>> subscriptions = new IdentityHashMap<>();

    @Override public void register(Object target) { register(target, false); }

    public void register(Object target, boolean splitByBus) {
        Objects.requireNonNull(target);
        boolean statics = target instanceof Class<?>;
        var type = statics ? (Class<?>) target : target.getClass();
        synchronized (sessions) {
            if (subscriptions.containsKey(target)) return;
            List<Runnable> removed = new ArrayList<>();
            try {
                var methods = new LinkedHashSet<>(Arrays.asList(type.getMethods()));
                methods.addAll(Arrays.asList(type.getDeclaredMethods()));
                for (var method : methods) {
                    var annotation = method.getAnnotation(SubscribeEvent.class);
                    if (annotation == null || method.isBridge() || method.isSynthetic()) continue;
                    if (statics != java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                    if (method.getParameterCount() != 1 || method.getReturnType() != void.class
                            || !Event.class.isAssignableFrom(method.getParameterTypes()[0]))
                        throw new IllegalArgumentException("Invalid NeoForge listener " + method);
                    var event = method.getParameterTypes()[0].asSubclass(Event.class);
                    if (splitByBus && (owner != null) != net.neoforged.fml.event.IModBusEvent.class.isAssignableFrom(event)) continue;
                    var handle = java.lang.invoke.MethodHandles.privateLookupIn(method.getDeclaringClass(),
                            java.lang.invoke.MethodHandles.lookup()).unreflect(method);
                    var callback = statics ? handle : handle.bindTo(target);
                    removed.add(listen(annotation.priority(), annotation.receiveCanceled(), event, value -> {
                        try { callback.invoke(value); }
                        catch (RuntimeException | Error e) { throw e; }
                        catch (Throwable e) { throw new IllegalStateException("NeoForge listener " + method, e); }
                    }));
                }
                if (!removed.isEmpty()) subscriptions.put(target, removed);
            } catch (IllegalAccessException | RuntimeException | Error e) {
                removed.forEach(Runnable::run);
                if (e instanceof RuntimeException failure) throw failure;
                if (e instanceof Error failure) throw failure;
                throw new IllegalArgumentException("Cannot register " + type, e);
            }
        }
    }

    @Override public void unregister(Object target) {
        synchronized (sessions) {
            var removed = subscriptions.remove(target);
            if (removed != null) removed.forEach(Runnable::run);
        }
    }

    public NeoBus(String owner) {
        this.owner = owner;
    }
    public String owner() {
        return owner;
    }
    private record
        Lifecycle(Class<? extends ServerLifecycleEvent> type, Events.Signal<MinecraftServer> signal,
                  java.util.function.Function<MinecraftServer, ServerLifecycleEvent> create) {}
    private static final List<Lifecycle> LIFECYCLE =
        List.of(new Lifecycle(ServerAboutToStartEvent.class, Events.SERVER_ABOUT_TO_START,
                              ServerAboutToStartEvent::new),
                new Lifecycle(ServerStartingEvent.class, Events.SERVER_STARTING, ServerStartingEvent::new),
                new Lifecycle(ServerStartedEvent.class, Events.SERVER_STARTED, ServerStartedEvent::new),
                new Lifecycle(ServerStoppingEvent.class, Events.SERVER_STOPPING, ServerStoppingEvent::new),
                new Lifecycle(ServerStoppedEvent.class, Events.SERVER_STOPPED, ServerStoppedEvent::new));
    public <T extends Event> void addListener(EventPriority priority, boolean receive, Class<T> kind,
                                              Consumer<T> cb) {
        synchronized (sessions) {
            subscriptions.computeIfAbsent(cb, key -> new ArrayList<>()).add(listen(priority, receive, kind, cb));
        }
    }

    private <T extends Event> Runnable listen(EventPriority priority, boolean receive, Class<T> kind, Consumer<T> cb) {
        Objects.requireNonNull(cb);
        synchronized (sessions) {
            sessions.add(this);
            boolean supported = owner == null && kind.isAssignableFrom(net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent.class);
            if (owner == null)
                for (var phase : LIFECYCLE) {
                    if (kind.isAssignableFrom(phase.type)) {
                        bridges.computeIfAbsent(
                            phase.type, k -> phase.signal.listen(server -> post(phase.create.apply(server))));
                        supported = true;
                    }
                }

            if (owner == null && kind.isAssignableFrom(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre.class)) {
                bridges.computeIfAbsent(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre.class,
                    k -> Events.SERVER_TICK_PRE.listenScoped((s,t) -> post(new net.neoforged.neoforge.event.tick.ServerTickEvent.Pre(t,s))));
                supported = true;
            }
            if (owner == null && kind.isAssignableFrom(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class)) {
                bridges.computeIfAbsent(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class,
                    k -> Events.SERVER_TICK_POST.listenScoped((s,t) -> post(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(t,s))));
                supported = true;
            }
            if (owner!=null && kind.isAssignableFrom(net.neoforged.neoforge.registries.RegisterEvent.class)) {
                bridges.computeIfAbsent(net.neoforged.neoforge.registries.RegisterEvent.class,k->Events.REGISTRY.listen(registry->{
                    int before=registry.size();
                    post(net.neoforged.neoforge.registries.UnityRegistration.event(registry));
                    if (registry.size()!=before)mc.unity.core.Content.include(owner);
                }));supported=true;
            }
            if(owner==null && kind.isAssignableFrom(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class)){
                bridges.computeIfAbsent(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                    k->Events.PLAYER_LOGIN.listen(p->post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p))));supported=true;
            }
            if(owner==null && kind.isAssignableFrom(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class)){
                bridges.computeIfAbsent(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class,
                    k->Events.PLAYER_LOGOUT.listen(p->post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p))));supported=true;
            }
            if(owner==null)for(var entry:Map.of(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.class,
                    mc.unity.core.Actions.Kind.LEFT_BLOCK,net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class,
                    mc.unity.core.Actions.Kind.RIGHT_BLOCK).entrySet()){
                if(kind.isAssignableFrom(entry.getKey())){bridges.computeIfAbsent(entry.getKey(),
                    k->mc.unity.core.Actions.attachScoped(new MachineInteractions(this,entry.getValue())));supported=true;}
            }
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
            if(owner==null) supported |= gameplay(kind);
            if(owner==null && Set.of("net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent",
                    "net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent").contains(kind.getName())){
                clientCallbacks.add(kind.getName());supported=true;
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
                   kind.getName().equals("net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent") ||
                   kind.getName().equals("net.neoforged.neoforge.client.event.RegisterItemModelsEvent") ||
                   kind.getName().equals("net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent") ||
                   kind.getName().equals("net.neoforged.neoforge.client.event.RegisterMenuScreensEvent") ||
                   kind == net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.class ||
                   kind == net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent.class ||
                   kind == net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent.class ||
                   kind == net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry.class ||
                   kind == net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent.class)) &&
                kind.getName().startsWith("net.neoforged."))
                throw new IllegalArgumentException("Unsupported NeoForge event or bus: " + kind);
            return handlers.listen((byte)(2 - priority.ordinal()), e -> {
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
    private static final class ClientBreakBinding {}
    private boolean gameplay(Class<?> kind) {
        boolean bound=false;
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.level.LevelEvent.Load.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.level.LevelEvent.Load.class,k->{
                java.util.function.Consumer<net.minecraft.world.level.Level> dispatch=level->post(new net.neoforged.neoforge.event.level.LevelEvent.Load(level));
                var server=Events.LEVEL_LOAD.listen(dispatch::accept);
                var client=Events.CLIENT_LEVEL_LOAD.listen(dispatch);
                return ()->{client.run();server.run();};
            });
            bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.level.LevelEvent.Unload.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.level.LevelEvent.Unload.class,k->{
                java.util.function.Consumer<net.minecraft.world.level.Level> dispatch=level->post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(level));
                var server=Events.LEVEL_UNLOAD.listen(dispatch::accept);
                var client=Events.CLIENT_LEVEL_UNLOAD.listen(dispatch);
                return ()->{client.run();server.run();};
            });
            bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.level.LevelEvent.Save.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.level.LevelEvent.Save.class,k->Events.LEVEL_SAVE.listen(level->post(new net.neoforged.neoforge.event.level.LevelEvent.Save(level))));
            bound=true;
        }

        if(kind.isAssignableFrom(net.neoforged.neoforge.event.level.block.BreakBlockEvent.class)){
            bridges.computeIfAbsent(ClientBreakBinding.class,k->Events.CLIENT_BREAK.listen(new NeoActions(this,mc.unity.core.Actions.Kind.BREAK)::accept));bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class,k->Events.ENTITY_JOIN.listen(v->
                v.cancelled=post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(v.entity,v.entity.level(),v.loaded)).isCanceled()));bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.level.ChunkEvent.Load.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.level.ChunkEvent.Load.class,k->Events.CHUNK_LOAD.listen(v->
                post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(v.chunk(),v.fresh()))));bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.level.ChunkEvent.Unload.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.level.ChunkEvent.Unload.class,k->Events.CHUNK_UNLOAD.listen(v->
                post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(v))));bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre.class,k->Events.LEVEL_TIME_PRE.listen(v->
                post(new net.neoforged.neoforge.event.tick.LevelTickEvent.Pre(v.time(),v.level()))));bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.tick.LevelTickEvent.Post.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.tick.LevelTickEvent.Post.class,k->Events.LEVEL_TIME_POST.listen(v->
                post(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(v.time(),v.level()))));bound=true;
        }
        for(var entry:Map.of(
                net.neoforged.neoforge.event.level.block.BreakBlockEvent.class,mc.unity.core.Actions.Kind.BREAK,
                net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class,mc.unity.core.Actions.Kind.PLACE,
                net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent.class,mc.unity.core.Actions.Kind.PLACE,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem.class,mc.unity.core.Actions.Kind.RIGHT_ITEM,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class,mc.unity.core.Actions.Kind.INTERACT_ENTITY,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty.class,mc.unity.core.Actions.Kind.LEFT_EMPTY,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickEmpty.class,mc.unity.core.Actions.Kind.RIGHT_EMPTY,
                net.neoforged.neoforge.event.entity.player.AttackEntityEvent.class,mc.unity.core.Actions.Kind.ATTACK_ENTITY).entrySet()) {
            if (!kind.isAssignableFrom(entry.getKey()))continue;
            var key=entry.getValue()==mc.unity.core.Actions.Kind.PLACE?net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class:entry.getKey();
            bridges.computeIfAbsent(key,k->mc.unity.core.Actions.attachScoped(new NeoActions(this,entry.getValue())));bound=true;
        }
        if(List.of(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent.class,net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent.class,
                net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre.class,
                net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class).stream().anyMatch(kind::isAssignableFrom)){
            bridges.computeIfAbsent(NeoDamage.class,k->mc.unity.core.LivingFlow.bind((e,s,a)->new NeoDamage(this,e,s,a)));bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class,k->
                mc.unity.core.LivingFlow.DEATH.listen(v->v.cancelled=post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(v.entity,v.source)).isCanceled()));bound=true;
        }
        if(kind.isAssignableFrom(net.neoforged.neoforge.event.entity.living.LivingDropsEvent.class)){
            bridges.computeIfAbsent(net.neoforged.neoforge.event.entity.living.LivingDropsEvent.class,k->
                mc.unity.core.LivingFlow.DROPS.listen(v->v.cancelled=post(new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(v.entity,v.source,v.items,v.recentlyHit)).isCanceled()));bound=true;
        }
        return bound;
    }
    public <T extends Event> T post(T event) {
        handlers.post(event);
        return event;
    }
    public static void clear() {
        synchronized (sessions) {
            for (var bus : sessions) {
                bus.subscriptions.clear();
                bus.handlers.clear();
                bus.bridges.values().forEach(Runnable::run);
                bus.bridges.clear();
            }
            sessions.clear();clientCallbacks.clear();
        }
        ListenerTypes.clear();
    }
}
