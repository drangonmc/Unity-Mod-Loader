package mc.unity.runtime;

import java.nio.file.Path;
import java.util.*;
import mc.unity.api.TickingMod;
import mc.unity.loader.NativeLoader;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class ServerHooks {
    private static NativeLoader nativeMods;
    private static Platforms forge;
    private static List<TickingMod> ticking = List.of();
    private static MinecraftServer running, preparing;
    public static void about(MinecraftServer server) {preparing=server;mc.unity.core.Events.about(server); }
    private static boolean stopping;
    private static boolean sharedClientSession;
    static void attachClient(NativeLoader mods, Platforms platforms) {
        if (nativeMods != null) throw new IllegalStateException("Mod session already attached");
        nativeMods=mods; forge=platforms; sharedClientSession=true;
        ticking=mods.serverCallbacks().stream().map(NativeLoader.Loaded::mod)
            .filter(TickingMod.class::isInstance).map(TickingMod.class::cast).toList();
    }
    static void detachClient() {
        if (!sharedClientSession) return;
        sharedClientSession=false; nativeMods=null; forge=null; ticking=List.of(); running=null; preparing=null; stopping=false;
    }

    static void discover(Path instance) throws Exception {
        nativeMods = NativeLoader.open(instance);
        try {
            forge = Platforms.open(instance, names());
        } catch (Exception | LinkageError e) {
            nativeMods.close();
            throw e;
        }
        ticking = nativeMods.mods()
                      .stream()
                      .map(NativeLoader.Loaded::mod)
                      .filter(TickingMod.class ::isInstance)
                      .map(TickingMod.class ::cast)
                      .toList();
        System.out.println("[Unity] Discovered native mods " + names() + "; platform mods " + forge.ids());
    }
    private static List<String> names() {
        return nativeMods.serverCallbacks().stream().map(NativeLoader.Loaded::id).toList();
    }
    public static void commands(
        Commands commands, Commands.CommandSelection selection, CommandBuildContext context) {
        if (nativeMods == null)
            return;
        var dispatcher = commands.getDispatcher();
        dispatcher.register(Commands.literal("unity").executes(c -> {
            c.getSource().sendSuccess(
                ()
                    -> Component.literal("Unity 0.2.2-Beta standalone | MC 26.2 | native " + names()
                        + " | platform mods " + forge.ids()),
                false);
            return 1;
        }));
        for (var entry : nativeMods.serverCallbacks()) entry.mod().registerCommands(dispatcher);
        mc.unity.core.Events.COMMANDS.post(
            new mc.unity.core.Events.CommandsReady(dispatcher, selection, context));
    }
    public static void started(MinecraftServer server) {
        if (nativeMods == null)
            throw new IllegalStateException("Unity mod discovery did not run");
        stopping = false;
        running = server;
        mc.unity.core.Events.started(server);
        for (var entry : nativeMods.serverCallbacks()) entry.mod().serverStarted(server);
        System.out.println("[Unity] " + (sharedClientSession ? "Integrated" : "Standalone") + " ready; native " + names());
    }
    public static void tick(MinecraftServer server) {
        if (server != running || stopping)
            return;
        for (int i = 0; i < ticking.size(); i++) ticking.get(i).serverTick(server);
    }
    public static void stopping(MinecraftServer server) {
        if (server != running || stopping)
            return;
        stopping = true;
        mc.unity.core.Events.stopping(server);
        for (var entry : nativeMods.serverCallbacks().reversed()) {
            try {
                entry.mod().serverStopping(server);
            } catch (RuntimeException | LinkageError e) {
                System.getLogger("mc.unity").log(System.Logger.Level.ERROR, "Stopping " + entry.id(), e);
            }
        }
    }
    public static void stopped(MinecraftServer server) {
        if (nativeMods == null || (sharedClientSession && server!=running && server!=preparing) || (running != null && server != running))
            return;
        running = null; preparing=null;
        stopping = false;
        if (sharedClientSession) {
            mc.unity.core.Events.stopped(server);
            System.out.println("[Unity] Integrated stopped");
            return;
        }
        ticking = List.of();
        try {
            try {
                try {
                    mc.unity.core.Events.stopped(server);
                } finally {
                    nativeMods.close();
                }
            } finally {
                forge.close();
            }
        } catch (java.io.IOException e) {
            System.getLogger("mc.unity").log(System.Logger.Level.ERROR, "Closing mod jars", e);
        }
        nativeMods = null;
        forge = null;
        System.out.println("[Unity] Standalone stopped");
    }
}
