package qa.worlds.nativecheck;
import java.nio.file.Path;
import mc.unity.api.TickingMod;
import net.minecraft.server.MinecraftServer;
public final class NativeWorld implements TickingMod {
    private MinecraftServer current;
    private int sessions, ticks;
    public void initialize(Path dir) { System.out.println("QA14 native-init"); }
    public void serverStarted(MinecraftServer s) {
        if (current != null)
            throw new IllegalStateException("Overlapping world");
        current = s;
        ticks = 0;
        sessions++;
    }
    public void serverTick(MinecraftServer s) {
        if (current != s || !s.isSameThread())
            throw new IllegalStateException("Wrong native tick owner");
        ticks++;
    }
    public void serverStopping(MinecraftServer s) {
        if (current != s || ticks == 0)
            throw new IllegalStateException("Native session lost");
        System.out.println("QA14 native-stop " + sessions + " " + s.getWorldData().getLevelName() +
                           " ticks=" + ticks);
        current = null;
    }
}
