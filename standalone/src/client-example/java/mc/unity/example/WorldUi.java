package mc.unity.example;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

final class WorldUi {
    private final Path io = Path.of(System.getProperty("unity.qa.io"));
    private String opening;
    void tick() {
        var input = io.resolve("world-request.json");
        if (!Files.isRegularFile(input))
            return;
        var answer = new JsonObject();
        try {
            var request = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
            Files.delete(input);
            var mc = Minecraft.getInstance();
            String name = request.has("world") ? request.get("world").getAsString() : "";
            if (!name.isEmpty() && !name.matches("UnityQA[A-Z]"))
                throw new IllegalArgumentException(name);
            switch (request.get("op").getAsString()) {
            case "state" -> {
                var screen = mc.gui.screen();
                if (screen != null)
                    answer.addProperty("title", screen.getTitle().getString());
                if (opening != null &&
                    screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen confirm &&
                    confirm.getTitle().getString().equals(
                        net.minecraft.network.chat.Component
                            .translatable("selectWorld.backupQuestion.experimental")
                            .getString())) {
                    String text =
                        net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinConfirmButton")
                            .getString();
                    for (var child : confirm.children())
                        if (child instanceof net.minecraft.client.gui.components.Button button &&
                            button.getMessage().getString().equals(text)) {
                            System.out.println("[Unity QA] Create backup and reopen " + opening);
                            button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                            break;
                        }
                }
                if (mc.level != null)
                    opening = null;
                answer.addProperty("level", mc.level != null && mc.player != null);
                answer.addProperty("local", mc.hasSingleplayerServer());
                answer.addProperty("screen",
                                   mc.gui.screen() == null ? "" : mc.gui.screen().getClass().getName());
                var server = mc.getSingleplayerServer();
                if (server != null)
                    answer.addProperty("world", server.getWorldData().getLevelName());
                if (mc.player != null)
                    answer.addProperty("uuid", mc.player.getUUID().toString());
            }
            case "create" -> {
                if (mc.level != null ||
                    Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(name).resolve("level.dat")))
                    throw new IllegalStateException("World already exists");
                var settings =
                    new LevelSettings(name, GameType.SURVIVAL, LevelSettings.DifficultySettings.DEFAULT, true,
                                      WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(
                    name, settings, new WorldOptions(261404L, false, false),
                    WorldPresets::createNormalWorldDimensions, new TitleScreen());
            }
            case "open" -> {
                opening = name;
                mc.createWorldOpenFlows().openWorld(name, () -> mc.gui.setScreen(new TitleScreen()));
            }
            case "leave" -> {
                mc.disconnectWithSavingScreen();
                mc.gui.setScreen(new TitleScreen());
            }
            case "reload" -> mc.reloadResourcePacks();
            case "inspect" -> {
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                answer.add(
                    "server",
                    server.submit(() -> WorldInspection.server(server, server.getPlayerList().getPlayer(id)))
                        .get(12, java.util.concurrent.TimeUnit.SECONDS));
                answer.add("client", WorldInspection.client(mc));
            }
            case "command" -> {
                var server = mc.getSingleplayerServer();
                String cmd = request.get("value").getAsString();
                server.execute(
                    () -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
            }
            default -> throw new IllegalArgumentException("Unknown local test action");
            }
        } catch (Exception | LinkageError error) {
            error.printStackTrace();
            answer.addProperty("error", error.toString());
        }
        try {
            Path tmp = io.resolve("world.tmp");
            Files.writeString(tmp, answer.toString());
            Files.move(tmp, io.resolve("world-answer.json"), StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
