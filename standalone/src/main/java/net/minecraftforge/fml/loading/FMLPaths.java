package net.minecraftforge.fml.loading;
import java.nio.file.Path;
public enum FMLPaths {
    GAMEDIR(""),
    MODSDIR("mods"),
    CONFIGDIR("config");
    private final String path;
    private static Path game;
    FMLPaths(String path) {
        this.path = path;
    }
    public static void setup(Path root) {
        game = root.toAbsolutePath().normalize();
    }
    public Path get() {
        return game.resolve(path);
    }
}
