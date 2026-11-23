package net.minecraftforge.event;
import com.mojang.brigadier.CommandDispatcher;
import mc.unity.adapter.forge.CommandBus;
import net.minecraft.commands.*;
import net.minecraftforge.eventbus.api.bus.EventBus;

/** Compatibility ABI for command registration on this standalone host. */
public record RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> getDispatcher,
    Commands.CommandSelection getCommandSelection, CommandBuildContext getBuildContext) {
    public static final EventBus<RegisterCommandsEvent> BUS = CommandBus.INSTANCE;
}
