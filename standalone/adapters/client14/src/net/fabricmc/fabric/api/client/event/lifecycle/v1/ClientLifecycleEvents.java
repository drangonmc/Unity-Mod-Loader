package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.fabricmc.fabric.api.event.Event;
import mc.unity.adapter.fabric.SessionEvent;
public final class ClientLifecycleEvents {
    public interface ClientStarted {
        void onClientStarted(Minecraft value);
    }
    public static final Event<ClientStarted> CLIENT_STARTED =
        new SessionEvent<>(ClientStarted.class, list -> value -> {
            for (var cb : list)
                cb.onClientStarted(value);
        });
    public interface ClientStopping {
        void onClientStopping(Minecraft value);
    }
    public static final Event<ClientStopping> CLIENT_STOPPING =
        new SessionEvent<>(ClientStopping.class, list -> value -> {
            for (var cb : list)
                cb.onClientStopping(value);
        });
}
