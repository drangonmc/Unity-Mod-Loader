package mc.unity.adapter.fabric;
import mc.unity.core.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.world.InteractionResult;
public final class FabricGameEvents implements Actions.Adapter {
    public static void wire() {
        Events.SERVER_ABOUT_TO_START.listen(
            s -> ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(s));
        Events.SERVER_STARTED.listen(s -> ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(s));
        Events.SERVER_STOPPING.listen(s -> ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(s));
        Events.SERVER_STOPPED.listen(s -> ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(s));
        Events.BLOCK_ENTITY_LOAD.listen(e->ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(e.entity(),e.level()));
        Events.BLOCK_ENTITY_UNLOAD.listen(e->ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(e.entity(),e.level()));
        Events.LEVEL_LOAD.listen(
            level -> ServerLevelEvents.LOAD.invoker().onLevelLoad(level.getServer(), level));
        Events.LEVEL_UNLOAD.listen(
            level -> ServerLevelEvents.UNLOAD.invoker().onLevelUnload(level.getServer(), level));
        Events.LEVEL_TICK_PRE.listen(level -> ServerTickEvents.START_LEVEL_TICK.invoker().onStartTick(level));
        Events.LEVEL_TICK_POST.listen(level -> ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(level));
        Events.SERVER_TICK_PRE.listen(
            (server, time) -> ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(server));
        Events.SERVER_TICK_POST.listen(
            (server, time) -> ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server));
        Actions.attach(new FabricGameEvents());
    }
    public boolean handles(Actions.Kind kind) {
        return kind == Actions.Kind.RIGHT_BLOCK && ((SessionEvent<?>)UseBlockCallback.EVENT).subscribed() ||
            kind == Actions.Kind.RIGHT_ITEM && ((SessionEvent<?>)UseItemCallback.EVENT).subscribed();
    }
    public void accept(Actions.Action a) {
        if (a.level.isClientSide())
            return;
        var result = a.kind == Actions.Kind.RIGHT_BLOCK
                         ? UseBlockCallback.EVENT.invoker().interact(a.player, a.level, a.hand, a.hit)
                         : UseItemCallback.EVENT.invoker().interact(a.player, a.level, a.hand);
        if (result != InteractionResult.PASS) {
            a.result = result;
            a.cancelled = true;
        }
    }
}
