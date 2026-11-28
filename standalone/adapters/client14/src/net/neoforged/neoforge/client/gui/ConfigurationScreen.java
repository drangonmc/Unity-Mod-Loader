package net.neoforged.neoforge.client.gui;

import java.nio.file.Files;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;

/** The standard two-argument factory opens this mod's local TOML files. */
public final class ConfigurationScreen extends OptionsSubScreen {
    private static Component text(String key, net.minecraft.ChatFormatting... format) {
        return Component.translatable("neoforge.configuration.uitext." + key).withStyle(format);
    }
    public static final Component CRUMB_SEPARATOR = text("breadcrumb.separator",net.minecraft.ChatFormatting.GOLD,net.minecraft.ChatFormatting.BOLD);
    public static final Component TOOLTIP_CANNOT_EDIT_THIS_WHILE_ONLINE = text("notonline",net.minecraft.ChatFormatting.RED);
    public static final Component TOOLTIP_CANNOT_EDIT_THIS_WHILE_OPEN_TO_LAN = text("notlan",net.minecraft.ChatFormatting.RED);
    public static final Component TOOLTIP_CANNOT_EDIT_NOT_LOADED = text("notloaded",net.minecraft.ChatFormatting.RED);
    public static final Component GAME_RESTART_TITLE = text("restart.game.title");
    public static final Component GAME_RESTART_MESSAGE = text("restart.game.text");
    public static final Component SERVER_RESTART_TITLE = text("restart.server.title");
    public static final Component SERVER_RESTART_MESSAGE = text("restart.server.text");
    private final ModContainer mod;
    public ConfigurationScreen(ModContainer mod, Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.literal(mod.getModId()));
        this.mod = mod;
    }
    @Override protected void addOptions() {
        for (var type : ModConfig.Type.values()) {
            ModConfigs.getConfigSet(type).stream()
                .filter(config -> config.getModId().equals(mod.getModId()))
                .sorted(Comparator.comparing(ModConfig::getFileName))
                .forEach(config -> {
                    var path = config.getFullPath();
                    var button = Button.builder(Component.literal(config.getFileName()), b -> Util.getPlatform().openPath(path)).build();
                    button.active = path != null && Files.isRegularFile(path) &&
                        (type != ModConfig.Type.SERVER || Minecraft.getInstance().hasSingleplayerServer());
                    if (!button.active) button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        path == null || !Files.isRegularFile(path) ? TOOLTIP_CANNOT_EDIT_NOT_LOADED : TOOLTIP_CANNOT_EDIT_THIS_WHILE_ONLINE));
                    list.addBig(button);
                });
        }
    }
}
