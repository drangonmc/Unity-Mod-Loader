package net.minecraftforge.fml;
import mc.unity.runtime.Platforms;
public final class ModList {
    public static boolean isLoaded(String id) {
        return Platforms.present(id);
    }
}
