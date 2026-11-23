package net.fabricmc.fabric.api.event.lifecycle.v1;
import mc.unity.core.Events;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
public final class ServerTickEvents {
    public interface StartTick {
        void onStartTick(MinecraftServer server);
    }
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }
    public interface StartLevelTick {void onStartTick(net.minecraft.server.level.ServerLevel level);}
    public interface EndLevelTick {void onEndTick(net.minecraft.server.level.ServerLevel level);}
    public static final Event<StartTick> START_SERVER_TICK = new mc.unity.adapter.fabric.SessionEvent<>(StartTick.class,list->value->{for(var cb:list)cb.onStartTick(value);});
    public static final Event<EndTick> END_SERVER_TICK = new mc.unity.adapter.fabric.SessionEvent<>(EndTick.class,list->value->{for(var cb:list)cb.onEndTick(value);});
    public static final Event<StartLevelTick> START_LEVEL_TICK = new mc.unity.adapter.fabric.SessionEvent<>(StartLevelTick.class,list->value->{for(var cb:list)cb.onStartTick(value);});
    public static final Event<EndLevelTick> END_LEVEL_TICK = new mc.unity.adapter.fabric.SessionEvent<>(EndLevelTick.class,list->value->{for(var cb:list)cb.onEndTick(value);});
}
