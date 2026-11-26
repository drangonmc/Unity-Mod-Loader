package net.minecraftforge.fml.loading;

import net.minecraftforge.api.distmarker.Dist;

/** Physical side of the process, including a client hosting an integrated server. */
public class FMLEnvironment {
    public static final Dist dist =
        FMLEnvironment.class.getClassLoader().getResource("net/minecraft/client/Minecraft.class") == null
            ? Dist.DEDICATED_SERVER
            : Dist.CLIENT;
    public static final boolean production = FMLLoader.isProduction();
}
