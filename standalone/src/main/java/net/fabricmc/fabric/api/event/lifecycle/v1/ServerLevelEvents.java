package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import mc.unity.adapter.fabric.SessionEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
public final class ServerLevelEvents {
    public interface Load {
        void onLevelLoad(MinecraftServer server, ServerLevel level);
    }
    public interface Unload {
        void onLevelUnload(MinecraftServer server, ServerLevel level);
    }
    public static final Event<Load> LOAD = new SessionEvent<>(Load.class, list -> (server, level) -> {
        for (var cb : list)
            cb.onLevelLoad(server, level);
    });
    public static final Event<Unload> UNLOAD = new SessionEvent<>(Unload.class, list -> (server, level) -> {
        for (var cb : list)
            cb.onLevelUnload(server, level);
    });
}
