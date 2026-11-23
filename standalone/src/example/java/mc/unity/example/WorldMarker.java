package mc.unity.example;

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
        if (Boolean.getBoolean("mc.unity.dualQA"))
            mc.unity.qa.QaPackets.install();
    }
    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        if(Boolean.getBoolean("mc.unity.contentQA")) dispatcher.register(Commands.literal("unityqa_channel")
            .requires(src->src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).then(Commands.argument("channel",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->{
                var player=c.getSource().getPlayerOrException();
                String name=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"channel");
                boolean available=((mc.unity.core.PlayNetwork.Peer)(Object)player.connection).hasChannel(
                    new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.parse(name)));
                c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("QA_CHANNEL "+player.getGameProfile().name()+" "+name+" "+available),false);
                return available?1:0;
            })));

        if (Boolean.getBoolean("mc.unity.dualQA")) {
            dispatcher.register(Commands.literal("unityqa_viewers")
                    .requires(s
                        -> s.permissions().hasPermission(
                            net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .executes(ctx -> {
                        var source = ctx.getSource();
                        var pos = net.minecraft.core.BlockPos.containing(source.getPosition());
                        var be = source.getLevel().getBlockEntity(pos);
                        if (be == null)
                            return 0;
                        var players =
                            source.getServer()
                                .getPlayerList()
                                .getPlayers()
                                .stream()
                                .filter(p
                                    -> p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu m
                                        && m.getContainer() == be)
                                .map(p -> p.getGameProfile().name())
                                .sorted()
                                .toList();
                        source.sendSuccess(
                            ()
                                -> Component.literal("UNITY_SHARED " + pos.toShortString() + " " + players),
                            false);
                        return players.size();
                    }));
            dispatcher.register(Commands.literal("unityqa_colors")
                    .requires(s
                        -> s.permissions().hasPermission(
                            net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .executes(ctx -> {
                        var menu = ctx.getSource().getPlayerOrException().containerMenu;
                        if (!menu.getClass().getName().equals(
                                "com.github.iunius118.laserbladetools.menu.ColorizerMenu"))
                            return 0;
                        try {
                            var read = menu.getClass().getMethod("getColorIndex", int.class);
                            var colors = java.util.List.of(
                                read.invoke(menu, 0), read.invoke(menu, 1), read.invoke(menu, 2));
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("UNITY_COLORS " + colors), false);
                            return 1;
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    }));
        }
        if (Boolean.getBoolean("mc.unity.contentQA")) {
            dispatcher.register(Commands.literal("unityqa_setup_food")
                    .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(c -> {
                        var food = c.getSource().getPlayerOrException().getFoodData();
                        food.setFoodLevel(8);
                        food.setSaturation(0);
                        return 1;
                    }));
            dispatcher.register(Commands.literal("unityregistry")
                    .then(Commands.argument("id", net.minecraft.commands.arguments.IdentifierArgument.id())
                            .executes(c -> {
                                var id = net.minecraft.commands.arguments.IdentifierArgument.getId(c, "id");
                                var items = net.minecraft.core.registries.BuiltInRegistries.ITEM;
                                var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
                                c.getSource().sendSuccess(
                                    ()
                                        -> Component.literal("UNITY_REGISTRY " + id + " item="
                                            + items.getOptional(id).map(items::getId).orElse(-1) + " block="
                                            + blocks.getOptional(id).map(blocks::getId).orElse(-1)),
                                    false);
                                return 1;
                            })));
        }
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
