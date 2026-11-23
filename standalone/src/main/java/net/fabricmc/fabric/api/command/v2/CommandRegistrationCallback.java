package net.fabricmc.fabric.api.command.v2;
import com.mojang.brigadier.CommandDispatcher;
import mc.unity.core.Events;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.*;
public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = new Event<>() {
        public void register(CommandRegistrationCallback cb) {
            Events.COMMANDS.listen(e -> cb.register(e.dispatcher(), e.context(), e.selection()));
        }
    };
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
        Commands.CommandSelection environment);
}
