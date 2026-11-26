package io.papermc.paper.plugin.lifecycle.event.types;

import io.papermc.paper.plugin.lifecycle.event.*;
import mc.unity.plugins.Views;

/** Paper's provider interface is package-private. */
public final class UnityEventTypes implements LifecycleEventTypeProvider {
    @Override
    public <O extends LifecycleEventOwner, E extends LifecycleEvent> LifecycleEventType.Monitorable<O, E>
    monitor(String name, Class<? extends O> owner) {
        return Views.of(LifecycleEventType.Monitorable.class, name, (method, args) -> {
            if (method.equals("name"))
                return name;
            throw Views.unsupported(name, method);
        });
    }
    @Override
    public <O extends LifecycleEventOwner, E extends LifecycleEvent> LifecycleEventType.Prioritizable<O, E>
    prioritized(String name, Class<? extends O> owner) {
        return Views.of(LifecycleEventType.Prioritizable.class, name, (method, args) -> {
            if (method.equals("name"))
                return name;
            throw Views.unsupported(name, method);
        });
    }
    @Override
    public TagEventTypeProvider tagProvider() {
        return Views.of(TagEventTypeProvider.class, "tags",
                        (method, args) -> { throw Views.unsupported("tags", method); });
    }
}
