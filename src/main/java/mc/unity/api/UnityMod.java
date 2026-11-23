package mc.unity.api;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/** Process initialization and logical-server callbacks for Minecraft 26.2. */
public interface UnityMod {
    default void initialize(Path dataDirectory) throws Exception {}
    default void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {}
    default void serverStarted(MinecraftServer server) {}
    default void serverStopping(MinecraftServer server) {}
}
