package net.minecraftforge.event.level;

import mc.unity.adapter.forge.CommandBus;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.InheritableEvent;

public interface LevelEvent extends InheritableEvent {
    EventBus<LevelEvent> BUS=new CommandBus<>();
    LevelAccessor getLevel();
    record Load(LevelAccessor getLevel) implements LevelEvent {
        public static final EventBus<Load> BUS=new CommandBus<>();
    }
    record Unload(LevelAccessor getLevel) implements LevelEvent {
        public static final EventBus<Unload> BUS=new CommandBus<>();
    }
    record Save(LevelAccessor getLevel) implements LevelEvent {
        public static final EventBus<Save> BUS=new CommandBus<>();
    }
}
