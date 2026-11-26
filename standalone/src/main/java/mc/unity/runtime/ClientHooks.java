package mc.unity.runtime;

import java.nio.file.Path;
import java.util.List;
import mc.unity.api.ClientTickMod;
import mc.unity.loader.NativeLoader;

public final class ClientHooks {
    private static List<ClientTickMod> callbacks = List.of();
    public static void initialize(Path game) throws Exception {
        if (java.nio.file.Files.exists(game.resolve(".unity-client.sha256")))
            throw new IllegalStateException(
                "Choose a standalone client directory, separate from the compatibility client.");
        if (cleanup != null)
            throw new IllegalStateException("Client session already open");
        setup = false;
        var mods = NativeLoader.open(game, "client");
        Platforms forge;
        try {
            forge = Platforms.open(game, mods.mods().stream().map(NativeLoader.Loaded::id).toList(), "CLIENT");
        } catch (Exception | LinkageError failure) {
            try {
                mods.close();
            } catch (Exception e) {
                failure.addSuppressed(e);
            }
            throw failure;
        }
        ServerHooks.attachClient(mods,forge);
        mc.unity.client.ClientEvents.wire();
        mc.unity.core.PlayNetwork.bindPlayer(() -> {
            try {
                var mc = Class.forName("net.minecraft.client.Minecraft");
                return (net.minecraft.world.entity.player.Player) mc.getField("player").get(
                    mc.getMethod("getInstance").invoke(null));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        mc.unity.core.PlayNetwork.bindClient(() -> {
            try {
                var minecraft = Class.forName("net.minecraft.client.Minecraft");
                var instance = minecraft.getMethod("getInstance").invoke(null);
                var listener = minecraft.getMethod("getConnection").invoke(instance);
                if (listener == null)
                    throw new IllegalStateException("Client is disconnected");
                return (net.minecraft.network.Connection) listener.getClass()
                    .getMethod("getConnection")
                    .invoke(listener);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        callbacks = mods.mods()
                        .stream()
                        .map(NativeLoader.Loaded::mod)
                        .filter(ClientTickMod.class ::isInstance)
                        .map(ClientTickMod.class ::cast)
                        .toList();
        System.out.println(
            "[Unity] Client native mods " + mods.mods().stream().map(NativeLoader.Loaded::id).toList());
        cleanup = () -> {
            ServerHooks.detachClient();
            mc.unity.client.Renderers.close();
            try {
                mods.close();
            } finally {
                forge.close();
            }
        };
    }
    private static AutoCloseable cleanup;
    public static synchronized void finish() {
        if (cleanup == null) return;
        try {
            cleanup.close();
        } catch (Exception e) {
            throw new IllegalStateException("Closing client mods", e);
        } finally {
            cleanup = null;
            setup = false;
            callbacks = List.of();
        }
    }
    private static boolean setup;
    public static void setup() {
        if (!setup) {
            setup = true;
            mc.unity.core.Events.CLIENT_SETUP.post(null);
            mc.unity.client.Renderers.setup();
        }
    }
    public static void tick() {
        for (int i = 0; i < callbacks.size(); i++) callbacks.get(i).clientTick();
    }
}
