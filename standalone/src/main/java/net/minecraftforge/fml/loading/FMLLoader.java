package net.minecraftforge.fml.loading;
import net.minecraftforge.api.distmarker.Dist;
public final class FMLLoader {
    private static Dist dist = Dist.DEDICATED_SERVER;
    public static void setSide(String side) {
        dist = side.equals("CLIENT") ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }
    public static Dist getDist() {
        return dist;
    }
    public static boolean isProduction() {
        return true;
    }
}
