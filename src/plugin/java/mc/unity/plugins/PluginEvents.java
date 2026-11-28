package mc.unity.plugins;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.event.*;
import org.bukkit.plugin.*;

/** Uses the API's own HandlerLists, including lists inherited by a family of events. */
final class PluginEvents {
    private static final ClassValue<HandlerList> LISTS = new ClassValue<>() {
        protected HandlerList computeValue(Class<?> event) {
            try {
                Method method = event.getMethod("getHandlerList");
                if (!Modifier.isStatic(method.getModifiers()) ||
                    !HandlerList.class.isAssignableFrom(method.getReturnType()))
                    throw new IllegalArgumentException("Invalid getHandlerList on " + event.getName());
                method.trySetAccessible();
                return (HandlerList)method.invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalPluginAccessException("No accessible HandlerList for " + event.getName() +
                                                       ": " + e);
            }
        }
    };
    static HandlerList handlers(Class<? extends Event> type) {
        return LISTS.get(type);
    }
    static Map<Class<? extends Event>, Set<RegisteredListener>> declared(Listener listener, Plugin plugin) {
        var methods = new LinkedHashSet<>(Arrays.asList(listener.getClass().getMethods()));
        methods.addAll(Arrays.asList(listener.getClass().getDeclaredMethods()));
        var result = new LinkedHashMap<Class<? extends Event>, Set<RegisteredListener>>();
        for (var method : methods) {
            var handler = method.getAnnotation(EventHandler.class);
            if (handler == null || method.isBridge() || method.isSynthetic())
                continue;
            if (method.getParameterCount() != 1 ||
                !Event.class.isAssignableFrom(method.getParameterTypes()[0]))
                throw new IllegalPluginAccessException("Invalid event handler " + method);
            var type = method.getParameterTypes()[0].asSubclass(Event.class);
            if (!method.trySetAccessible())
                throw new IllegalPluginAccessException("Inaccessible event handler " + method);
            EventExecutor executor = (target, event) -> {
                if (!type.isInstance(event))
                    return;
                try {
                    method.invoke(target, event);
                } catch (InvocationTargetException e) {
                    throw new EventException(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new EventException(e);
                }
            };
            result.computeIfAbsent(type, k -> new LinkedHashSet<>())
                .add(new RegisteredListener(listener, executor, handler.priority(), plugin,
                                            handler.ignoreCancelled()));
        }
        return result;
    }
    private static final Set<String> GAME_EVENTS;
    private static final Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static {
        try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(PluginEvents.class.getResourceAsStream("game-events.tsv")),
                java.nio.charset.StandardCharsets.UTF_8))) {
            GAME_EVENTS = lines.lines().map(line -> line.substring(0, line.indexOf('\t'))).map(name -> name.startsWith("io.papermc.") ? name : "org.bukkit.event."+name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private static void checkOrigin(Class<? extends Event> type, Plugin plugin) {
        if (type.getClassLoader() != Event.class.getClassLoader() || GAME_EVENTS.contains(type.getName())) return;
        String message = plugin.getName() + ": Unity 尚未验证此事件的游戏触发路径 " + type.getName();
        if (Boolean.getBoolean("mc.unity.strictBukkitEvents")) throw new IllegalPluginAccessException(message);
        if (warned.add(message)) Host.LOG.warning(message);
    }
    static void listen(Listener listener, Plugin plugin) {
        ensureEnabled(plugin);
        var entries = declared(listener, plugin);
        entries.keySet().forEach(type -> { handlers(type); checkOrigin(type,plugin); });
        entries.forEach((type, registrations) -> handlers(type).registerAll(registrations));
    }
    static void register(Class<? extends Event> type, Listener listener, EventPriority priority,
                         EventExecutor executor, Plugin owner, boolean ignore) {
        ensureEnabled(owner);
        checkOrigin(type,owner);
        handlers(type).register(new RegisteredListener(listener, executor, priority, owner, ignore));
    }
    private static void ensureEnabled(Plugin plugin) {
        if (!plugin.isEnabled())
            throw new IllegalPluginAccessException("Disabled plugin " + plugin.getName());
    }
    static void fire(Event event, boolean primary) {
        if (event.isAsynchronous() == primary)
            throw new IllegalStateException(event.getEventName() +
                                            (primary ? " cannot be fired asynchronously on the server thread"
                                                     : " must be fired on the server thread"));
        boolean capabilityFailed = false;
        for (var registration : event.getHandlers().getRegisteredListeners()) {
            var plugin = registration.getPlugin();
            if (!plugin.isEnabled())
                continue;
            var thread = Thread.currentThread();
            var previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(plugin.getClass().getClassLoader());
                registration.callEvent(event);
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError e)
                    throw e;
                if (failure instanceof ThreadDeath e)
                    throw e;
                if (event instanceof Cancellable cancellable && Views.capabilityFailure(failure)) {
                    capabilityFailed = true;
                    cancellable.setCancelled(true);
                }
                Host.LOG.log(java.util.logging.Level.SEVERE,
                             plugin.getName() + " failed handling " + event.getEventName(), failure);
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
        if (capabilityFailed) ((Cancellable)event).setCancelled(true);
    }
}
