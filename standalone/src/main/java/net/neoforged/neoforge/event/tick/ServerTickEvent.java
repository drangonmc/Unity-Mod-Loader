package net.neoforged.neoforge.event.tick;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

public abstract class ServerTickEvent extends Event {
    private final BooleanSupplier time;
    private final MinecraftServer server;
    protected ServerTickEvent(BooleanSupplier time, MinecraftServer server) {
        this.time = time;
        this.server = server;
    }
    public boolean hasTime() { return time.getAsBoolean(); }
    public MinecraftServer getServer() { return server; }
    public static class Pre extends ServerTickEvent {
        public Pre(BooleanSupplier time, MinecraftServer server) { super(time, server); }
    }
    public static class Post extends ServerTickEvent {
        public Post(BooleanSupplier time, MinecraftServer server) { super(time, server); }
    }
}
