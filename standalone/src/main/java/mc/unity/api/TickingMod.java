package mc.unity.api;

import net.minecraft.server.MinecraftServer;

/** Runs on the server thread after a vanilla game tick. */
public interface TickingMod extends UnityMod {
    void serverTick(MinecraftServer server);
}
