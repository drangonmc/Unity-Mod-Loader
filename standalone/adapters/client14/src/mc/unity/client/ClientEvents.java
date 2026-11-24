package mc.unity.client;
import mc.unity.core.Events;
import mc.unity.adapter.fabric.LoaderView;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
public final class ClientEvents {
    public static void wire() {
        if (LoaderView.upstream())
            return;
        Events.CLIENT_TICK_PRE.listen(
            v -> ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(Minecraft.getInstance()));
        Events.CLIENT_TICK_POST.listen(
            v -> ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(Minecraft.getInstance()));
    }
    public static void started(Minecraft mc) {
        if (!LoaderView.upstream())
            ClientLifecycleEvents.CLIENT_STARTED.invoker().onClientStarted(mc);
    }
    public static void stopping(Minecraft mc) {
        if (!LoaderView.upstream())
            ClientLifecycleEvents.CLIENT_STOPPING.invoker().onClientStopping(mc);
    }
    public static void levelStart(ClientLevel l) {
        if (!LoaderView.upstream())
            ClientTickEvents.START_LEVEL_TICK.invoker().onStartTick(l);
    }
    public static void levelEnd(ClientLevel l) {
        if (!LoaderView.upstream())
            ClientTickEvents.END_LEVEL_TICK.invoker().onEndTick(l);
    }
}
