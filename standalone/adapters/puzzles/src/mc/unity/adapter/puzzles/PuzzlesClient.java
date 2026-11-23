package mc.unity.adapter.puzzles;
import java.util.Set;
import fuzs.puzzleslib.common.api.client.core.v1.ClientModConstructor;
import fuzs.puzzleslib.common.api.client.event.v1.gui.ItemTooltipCallback;
import fuzs.puzzleslib.common.impl.event.core.EventInvokerImpl;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import mc.unity.core.Events;
final class PuzzlesClient {
    static void construct(String id,ClientModConstructor mod) {
        PuzzlesLifecycle.requireSupported(mod, ClientModConstructor.class,Set.of("onConstructMod","onClientSetup"));
        mod.onConstructMod();
        ModLoadingContext.get().getActiveContainer().getEventBus().addListener(FMLClientSetupEvent.class,e->mod.onClientSetup());
    }
    static void gameEvents() {
        PuzzlesLifecycle.gameEvents();
        EventInvokerImpl.register(ItemTooltipCallback.class,unused -> (phase,callback) ->
            Events.TOOLTIP.listen(PuzzlesLifecycle.priority(phase),e ->
                callback.onItemTooltip(e.stack(),e.lines(),e.context(),Minecraft.getInstance().player,e.flag())),false);
    }
}
