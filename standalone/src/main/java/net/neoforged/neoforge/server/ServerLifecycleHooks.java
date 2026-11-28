package net.neoforged.neoforge.server;
public final class ServerLifecycleHooks {
    private ServerLifecycleHooks(){}
    public static net.minecraft.server.MinecraftServer getCurrentServer(){return mc.unity.runtime.ServerHooks.currentServer();}
}
