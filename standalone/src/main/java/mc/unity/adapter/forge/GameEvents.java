package mc.unity.adapter.forge;
import java.util.function.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent.*;
import net.minecraftforge.event.entity.player.PlayerEvent.*;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.bus.EventBus;
public final class GameEvents {
    private static volatile MinecraftServer server;
    public static MinecraftServer server() {
        if (server == null)
            throw new IllegalStateException("No running server");
        return server;
    }
    private static <T> void emit(EventBus<T> bus, Supplier<T> factory) {
        var b = (CommandBus<T>) bus;
        if (!b.empty())
            b.dispatch(factory.get());
    }
    static <T extends net.minecraftforge.event.level.LevelEvent> void level(T event,EventBus<T> bus){
        emit(net.minecraftforge.event.level.LevelEvent.BUS,()->event);
        emit(bus,()->event);
    }
    public static void about(MinecraftServer s) {
        server = s;
        var e = new ServerAboutToStartEvent(s);
        emit(ServerLifecycleEvent.BUS, () -> e);
        emit(ServerAboutToStartEvent.BUS, () -> e);
    }
    public static void starting(MinecraftServer s) {
        var e = new ServerStartingEvent(s);
        emit(ServerLifecycleEvent.BUS, () -> e);
        emit(ServerStartingEvent.BUS, () -> e);
    }
    public static void started(MinecraftServer s) {
        server = s;
        var e = new ServerStartedEvent(s);
        emit(ServerLifecycleEvent.BUS, () -> e);
        emit(ServerStartedEvent.BUS, () -> e);
    }
    public static void stopping(MinecraftServer s) {
        var e = new ServerStoppingEvent(s);
        emit(ServerLifecycleEvent.BUS, () -> e);
        emit(ServerStoppingEvent.BUS, () -> e);
    }
    public static void stopped(MinecraftServer s) {
        var e = new ServerStoppedEvent(s);
        try {
            emit(ServerLifecycleEvent.BUS, () -> e);
            emit(ServerStoppedEvent.BUS, () -> e);
        } finally {
            server = null;
        }
    }
    public static void tickPre(MinecraftServer s, BooleanSupplier time) {
        var b = (CommandBus<ServerTickEvent.Pre>) ServerTickEvent.Pre.BUS;
        if (!b.empty())
            b.dispatch(new ServerTickEvent.Pre(time, s));
    }
    public static void tickPost(MinecraftServer s, BooleanSupplier time) {
        var b = (CommandBus<ServerTickEvent.Post>) ServerTickEvent.Post.BUS;
        if (!b.empty())
            b.dispatch(new ServerTickEvent.Post(time, s));
    }
    public static void clientPre() {
        ((CommandBus<ClientTickEvent.Pre>) ClientTickEvent.Pre.BUS).dispatch(ClientTickEvent.Pre.INSTANCE);
    }
    public static void clientPost() {
        ((CommandBus<ClientTickEvent.Post>) ClientTickEvent.Post.BUS).dispatch(ClientTickEvent.Post.INSTANCE);
    }
    public static void joined(ServerPlayer p) {
        emit(PlayerLoggedInEvent.BUS, () -> new PlayerLoggedInEvent(p));
    }
    public static void left(ServerPlayer p) {
        emit(PlayerLoggedOutEvent.BUS, () -> new PlayerLoggedOutEvent(p));
    }
    public static void clear() {
        server = null;
        for (var bus :
            java.util.List.of(net.minecraftforge.event.level.LevelEvent.BUS,net.minecraftforge.event.level.LevelEvent.Load.BUS,net.minecraftforge.event.level.LevelEvent.Unload.BUS,net.minecraftforge.event.level.LevelEvent.Save.BUS,ServerTickEvent.Pre.BUS, ServerTickEvent.Post.BUS, ClientTickEvent.Pre.BUS,
                ClientTickEvent.Post.BUS, ServerAboutToStartEvent.BUS, ServerStartingEvent.BUS,
                ServerStartedEvent.BUS, ServerStoppingEvent.BUS, ServerStoppedEvent.BUS,
                net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.BUS, net.minecraftforge.event.AddReloadListenerEvent.BUS, net.minecraftforge.event.entity.EntityAttributeCreationEvent.BUS, ServerLifecycleEvent.BUS, PlayerLoggedInEvent.BUS, PlayerLoggedOutEvent.BUS))
            ((CommandBus<?>) bus).clear();
    }
}
