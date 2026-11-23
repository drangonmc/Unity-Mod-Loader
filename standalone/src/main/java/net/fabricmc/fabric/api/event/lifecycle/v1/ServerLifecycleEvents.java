package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import mc.unity.adapter.fabric.SessionEvent;
import net.minecraft.server.MinecraftServer;
public final class ServerLifecycleEvents {
    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }
    public static final Event<ServerStarting> SERVER_STARTING =
        new SessionEvent<>(ServerStarting.class, callbacks -> server -> {
            for (var cb : callbacks)
                cb.onServerStarting(server);
        });
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }
    public static final Event<ServerStarted> SERVER_STARTED =
        new SessionEvent<>(ServerStarted.class, callbacks -> server -> {
            for (var cb : callbacks)
                cb.onServerStarted(server);
        });
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }
    public static final Event<ServerStopping> SERVER_STOPPING =
        new SessionEvent<>(ServerStopping.class, callbacks -> server -> {
            for (var cb : callbacks)
                cb.onServerStopping(server);
        });
    public interface ServerStopped {
        void onServerStopped(MinecraftServer server);
    }
    public static final Event<ServerStopped> SERVER_STOPPED =
        new SessionEvent<>(ServerStopped.class, callbacks -> server -> {
            for (var cb : callbacks)
                cb.onServerStopped(server);
        });
}
