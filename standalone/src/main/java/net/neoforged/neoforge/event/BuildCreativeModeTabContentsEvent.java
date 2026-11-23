package net.neoforged.neoforge.event;
import mc.unity.core.Events;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ItemLike;
public final class BuildCreativeModeTabContentsEvent extends UnityNeoTabAccess {
    private final Events.Tab event;
    public BuildCreativeModeTabContentsEvent(Events.Tab event) {
        this.event = event;
    }
    protected UnityTabContents entries() {
        return (UnityTabContents) event.output();
    }
    public CreativeModeTab getTab() {
        return event.tab();
    }
    public ResourceKey<CreativeModeTab> getTabKey() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(getTab()).orElseThrow();
    }
    public void accept(ItemLike item) {
        new Output(event.output()).accept(item);
    }
    private static final class Output extends UnityTabAccess {
        Output(Object out) {
            super(out);
        }
    }
}
