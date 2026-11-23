package net.fabricmc.fabric.api.creativetab.v1;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.event.Event;
import mc.unity.core.Events;
public final class CreativeModeTabEvents {
    public interface ModifyOutput {
        void modifyOutput(FabricCreativeModeTabOutput entries);
    }
    public interface ModifyOutputAll {
        void modifyOutput(CreativeModeTab tab, FabricCreativeModeTabOutput entries);
    }
    public static final Event<ModifyOutputAll> MODIFY_OUTPUT_ALL = new Event<>() {
        public void register(ModifyOutputAll listener) {
            Events.CREATIVE.listen(e
                -> listener.modifyOutput(
                    e.tab(), new FabricCreativeModeTabOutput((UnityTabContents) e.output())));
        }
    };
    public static Event<ModifyOutput> modifyOutputEvent(ResourceKey<CreativeModeTab> key) {
        return new Event<>() {
            public void register(ModifyOutput listener) {
                Events.CREATIVE.listen(e -> {
                    if (BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(e.tab()).orElseThrow().equals(key))
                        listener.modifyOutput(new FabricCreativeModeTabOutput((UnityTabContents) e.output()));
                });
            }
        };
    }
}
