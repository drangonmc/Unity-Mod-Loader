package net.minecraftforge.event;
import java.util.function.Supplier;
import mc.unity.adapter.forge.CommandBus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.bus.EventBus;
public final class BuildCreativeModeTabContentsEvent
    extends UnityTabAccess implements net.minecraftforge.eventbus.internal.Event {
    private final CreativeModeTab tab;
    private BuildCreativeModeTabContentsEvent(CreativeModeTab tab, Object out) {
        super(out);
        this.tab = tab;
    }
    public CreativeModeTab getTab() {
        return tab;
    }
    public static final EventBus<BuildCreativeModeTabContentsEvent> BUS = new CommandBus<>();
    private static final java.util.Map<net.minecraftforge.eventbus.api.bus.BusGroup,
        CommandBus<BuildCreativeModeTabContentsEvent>> groups = new java.util.LinkedHashMap<>();
    public static EventBus<BuildCreativeModeTabContentsEvent> getBus(
        net.minecraftforge.eventbus.api.bus.BusGroup group) {
        return groups.computeIfAbsent(group, k -> new CommandBus<>());
    }
    public static void clear() {
        groups.values().forEach(CommandBus::clear);
        groups.clear();
        ((CommandBus<?>) BUS).clear();
    }
    public ResourceKey<CreativeModeTab> getTabKey() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElseThrow();
    }
    public static void post(CreativeModeTab tab, Object out) {
        var event = new BuildCreativeModeTabContentsEvent(tab, out);
        ((CommandBus<BuildCreativeModeTabContentsEvent>) BUS).dispatch(event);
        groups.values().forEach(bus -> bus.dispatch(event));
    }
}
