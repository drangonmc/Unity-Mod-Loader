package net.minecraftforge.registries;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import mc.unity.adapter.forge.CommandBus;
import net.minecraftforge.eventbus.api.bus.EventBus;
public interface DataPackRegistryEvent {
    final class NewRegistry
        extends net.minecraftforge.eventbus.api.event.MutableEvent implements DataPackRegistryEvent {
        public static final EventBus<NewRegistry> BUS = new CommandBus<>();
        public <T> void dataPackRegistry(ResourceKey<Registry<T>> key, Codec<T> file) {
            dataPackRegistry(key, file, null);
        }
        public <T> void dataPackRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> file, Codec<T> sync) {
            mc.unity.core.WorldRegistries.add(key, file, sync, false);
        }
        public static void fire() {
            try {
                ((CommandBus<NewRegistry>)BUS).dispatch(new NewRegistry());
            } finally {
                clear();
            }
        }
        public static void clear() { ((CommandBus<?>)BUS).clear(); }
    }
}
