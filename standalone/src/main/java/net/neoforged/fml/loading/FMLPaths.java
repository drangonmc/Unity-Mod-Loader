package net.neoforged.fml.loading;
import java.nio.file.Path;
public enum FMLPaths {
    GAMEDIR(""),
    MODSDIR("mods"),
    CONFIGDIR("config");
    private final String sub;
    private static Path game;
    FMLPaths(String sub) { this.sub = sub; }
    public Path get() {
        return java.util.Objects.requireNonNull(game, "No active game directory").resolve(sub);
    }
    public static void bind(Path root) { game = root == null ? null : root.toAbsolutePath().normalize(); }
}
