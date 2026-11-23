package mc.unity.adapter.puzzles;

import fuzs.puzzleslib.common.api.core.v1.ModConstructor;
import fuzs.puzzleslib.common.api.event.v1.LoadCompleteCallback;
import fuzs.puzzleslib.common.impl.event.core.EventInvokerImpl;
import java.lang.reflect.Method;
import java.util.Set;
import fuzs.puzzleslib.common.api.event.v1.core.EventPhase;
import fuzs.puzzleslib.common.api.event.v1.server.ServerLifecycleEvents;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.*;
import mc.unity.core.Events;

/** Platform-side implementation of the Puzzles Lib constructor SPI. */
public final class PuzzlesLifecycle {
    static void requireSupported(Object mod, Class<?> api, Set<String> supported) {
        for (Method method : api.getMethods()) {
            if (!method.isDefault() || supported.contains(method.getName())) continue;
            try {
                if (mod.getClass().getMethod(method.getName(),method.getParameterTypes()).getDeclaringClass()!=api)
                    throw new IllegalArgumentException("Unsupported Puzzles constructor callback: "+method);
            } catch (NoSuchMethodException failure) { throw new IllegalStateException(failure); }
        }
    }
    static void common(String id, ModConstructor mod) {
        requireSupported(mod,ModConstructor.class,Set.of("onConstructMod","onCommonSetup"));
        var owner=ModLoadingContext.get().getActiveContainer();
        mod.onConstructMod();
        owner.getEventBus().addListener(FMLCommonSetupEvent.class,e->mod.onCommonSetup());
    }
    static void loadingEvents() {
        EventInvokerImpl.register(LoadCompleteCallback.class,unused -> (phase, cb) ->
            ModLoadingContext.get().getActiveContainer().getEventBus().addListener(
                FMLLoadCompleteEvent.class,event -> cb.onLoadComplete()),false);
    }
    static byte priority(EventPhase phase) {
        int result = 0;
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<EventPhase, Boolean>());
        while (phase.parent() != null) {
            if (!seen.add(phase)) throw new IllegalArgumentException("Cyclic Puzzles event phase");
            result -= phase.getOrderingValue();
            phase = phase.parent();
        }
        return (byte)Math.clamp(result,-127,127);
    }
    static void gameEvents() {
        EventInvokerImpl.register(ServerLifecycleEvents.Starting.class,unused -> (phase,cb) ->
            Events.SERVER_STARTING.listen(priority(phase),cb::onServerStarting),false);
        EventInvokerImpl.register(ServerLifecycleEvents.Started.class,unused -> (phase,cb) ->
            Events.SERVER_STARTED.listen(priority(phase),cb::onServerStarted),false);
        EventInvokerImpl.register(ServerLifecycleEvents.Stopping.class,unused -> (phase,cb) ->
            Events.SERVER_STOPPING.listen(priority(phase),cb::onServerStopping),false);
        EventInvokerImpl.register(ServerLifecycleEvents.Stopped.class,unused -> (phase,cb) ->
            Events.SERVER_STOPPED.listen(priority(phase),cb::onServerStopped),false);
    }
}
