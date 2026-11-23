package net.neoforged.neoforge.registries;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
public abstract class DataPackRegistryEvent extends Event implements IModBusEvent {
    public static final class NewRegistry extends DataPackRegistryEvent {
        public <T> void dataPackRegistry(ResourceKey<Registry<T>> key, Codec<T> file) {
            dataPackRegistry(key, file, null);
        }
        public <T> void dataPackRegistry(ResourceKey<Registry<T>> key, Codec<T> file, Codec<T> sync) {
            mc.unity.core.WorldRegistries.add(key, file, sync, false);
        }
    }
}
