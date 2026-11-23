package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.fabricmc.fabric.api.event.Event;
import mc.unity.adapter.fabric.SessionEvent;
public final class ClientTickEvents {
    public interface StartTick {
        void onStartTick(Minecraft value);
    }
    public static final Event<StartTick> START_CLIENT_TICK =
        new SessionEvent<>(StartTick.class, list -> value -> {
            for (var cb : list)
                cb.onStartTick(value);
        });
    public interface EndTick {
        void onEndTick(Minecraft value);
    }
    public static final Event<EndTick> END_CLIENT_TICK = new SessionEvent<>(EndTick.class, list -> value -> {
        for (var cb : list)
            cb.onEndTick(value);
    });
    public interface StartLevelTick {
        void onStartTick(ClientLevel value);
    }
    public static final Event<StartLevelTick> START_LEVEL_TICK =
        new SessionEvent<>(StartLevelTick.class, list -> value -> {
            for (var cb : list)
                cb.onStartTick(value);
        });
    public interface EndLevelTick {
        void onEndTick(ClientLevel value);
    }
    public static final Event<EndLevelTick> END_LEVEL_TICK =
        new SessionEvent<>(EndLevelTick.class, list -> value -> {
            for (var cb : list)
                cb.onEndTick(value);
        });
}
