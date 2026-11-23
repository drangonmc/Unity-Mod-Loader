package mc.unity.api;

/** Physical client callback. A client extension can use Minecraft.getInstance() on this thread. */
public interface ClientTickMod extends UnityMod {
    void clientTick();
}
