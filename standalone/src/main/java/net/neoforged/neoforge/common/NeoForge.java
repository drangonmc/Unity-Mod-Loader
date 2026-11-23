package net.neoforged.neoforge.common;
import mc.unity.adapter.neoforge.NeoBus;
import net.neoforged.bus.api.IEventBus;
public final class NeoForge {
    public static final IEventBus EVENT_BUS = new NeoBus(null);
}
