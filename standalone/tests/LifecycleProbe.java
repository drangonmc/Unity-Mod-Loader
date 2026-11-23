package fixture;
import java.io.*;
import java.nio.file.*;
import mc.unity.api.TickingMod;
import net.minecraft.server.MinecraftServer;
public final class LifecycleProbe implements TickingMod {
    private Path events;
    public void initialize(Path data) {
        events = data.resolve("events");
        append("open");
    }
    public void serverStarted(MinecraftServer server) {
        append("start");
    }
    public void serverTick(MinecraftServer server) {
        append("tick");
    }
    public void serverStopping(MinecraftServer server) {
        append("stop");
    }
    private void append(String event) {
        try {
            Files.writeString(events, event + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
