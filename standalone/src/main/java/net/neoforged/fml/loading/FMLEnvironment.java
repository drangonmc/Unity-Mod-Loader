package net.neoforged.fml.loading;
import net.neoforged.api.distmarker.Dist;
public final class FMLEnvironment {
    public static Dist getDist() {
        return FMLEnvironment.class.getClassLoader().getResource("net/minecraft/client/Minecraft.class") == null
            ? Dist.DEDICATED_SERVER
            : Dist.CLIENT;
    }
    public static boolean isProduction() { return true; }
}
