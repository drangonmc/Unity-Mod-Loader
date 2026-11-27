package qa.neo27;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

@EventBusSubscriber(modid="unity_neo26_probe",value=Dist.DEDICATED_SERVER)
public final class Neo27FuelProbe {
    private static boolean sample(FurnaceFuelBurnTimeEvent e) {
        var stack=e.getItemStack();
        return stack.is(Items.CLAY_BALL) && stack.has(DataComponents.CUSTOM_DATA)
            && stack.get(DataComponents.CUSTOM_DATA).copyTag().contains("unity_fuel");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void customize(FurnaceFuelBurnTimeEvent e) {
        if (!sample(e)) return;
        if (e.getBurnTime()!=0 || e.getFuelValues()==null || e.getRecipeType()==null)
            throw new AssertionError("Incorrect fuel context");
        e.setBurnTime(240);
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void skipped(FurnaceFuelBurnTimeEvent e) {
        if (sample(e)) throw new AssertionError("Already claimed fuel duration");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void observe(FurnaceFuelBurnTimeEvent e) {
        if (!sample(e)) return;
        if (!e.isCanceled() || e.getBurnTime()!=240) throw new AssertionError("Incorrect fuel dispatch");
        System.out.println("UNITY_NEO27_FUEL_OK duration=240");
    }
}
