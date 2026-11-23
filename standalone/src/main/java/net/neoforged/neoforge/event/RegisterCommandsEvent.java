package net.neoforged.neoforge.event;
import com.mojang.brigadier.CommandDispatcher;
import mc.unity.core.Events;
import net.minecraft.commands.*;
public final class RegisterCommandsEvent extends net.neoforged.bus.api.Event {
    private final Events.CommandsReady event;
    public RegisterCommandsEvent(Events.CommandsReady event) {
        this.event = event;
    }
    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return event.dispatcher();
    }
    public Commands.CommandSelection getCommandSelection() {
        return event.selection();
    }
    public CommandBuildContext getBuildContext() {
        return event.context();
    }
}
