package net.neoforged.neoforge.registries.datamaps;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
public final class RegisterDataMapTypesEvent extends Event implements IModBusEvent {
    public <T, R> void register(DataMapType<R, T> type) { mc.unity.adapter.neoforge.DataMaps.register(type); }
}
