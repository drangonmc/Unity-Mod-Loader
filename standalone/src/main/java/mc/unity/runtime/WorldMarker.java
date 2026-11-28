package mc.unity.runtime;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayDeque;
import mc.unity.api.TickingMod;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Blocks;

public final class WorldMarker implements TickingMod {
    private static final Identifier COUNT = Identifier.fromNamespaceAndPath("unity_example", "marks");
    private record Request(CommandSourceStack source, BlockPos position) {}
    private final ArrayDeque<Request> pending = new ArrayDeque<>();

    @Override
    public void initialize(java.nio.file.Path data) {
    }
    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("unitymark")
                .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("count").executes(c -> {
                    int n = c.getSource().getServer().getCommandStorage().get(COUNT).getIntOr("count", 0);
                    c.getSource().sendSuccess(() -> Component.literal("Unity marks saved: " + n), false);
                    return n;
                }))
                .then(Commands.literal("place").then(
                    Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                        if (pending.size() >= 256) {
                            c.getSource().sendFailure(Component.literal("Marker queue full"));
                            return 0;
                        }
                        pending.add(new Request(c.getSource(), pos.immutable()));
                        return 1;
                    }))));
    }
    @Override
    public void serverTick(MinecraftServer server) {
        for (Request req; (req = pending.poll()) != null;) {
            ServerLevel level = req.source().getLevel();
            BlockPos pos = req.position();
            if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir()
                || !level.setBlockAndUpdate(pos, Blocks.AMETHYST_BLOCK.defaultBlockState())) {
                req.source().sendFailure(Component.literal("Marker needs a loaded, empty position"));
                continue;
            }
            var storage = server.getCommandStorage();
            var value = storage.get(COUNT).copy();
            int count = value.getIntOr("count", 0) + 1;
            value.putInt("count", count);
            storage.set(COUNT, value);
            var msg = Component.literal(
                "Unity marker placed on game tick at " + pos.toShortString() + "; total " + count);
            req.source().sendSuccess(() -> msg, false);
        }
    }
    @Override
    public void serverStopping(MinecraftServer server) {
        pending.clear();
    }
}
