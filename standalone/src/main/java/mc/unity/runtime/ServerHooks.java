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
    private static PluginSession plugins;
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
            try {
                plugins = PluginSession.open(instance);
            } catch (Exception | Error failure) {
                try {
                    forge.close();
                } catch (Exception suppressed) {
                    failure.addSuppressed(suppressed);
                }
                throw failure;
            }
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
    public static Component playerMessage(Component text, net.minecraft.server.level.ServerPlayer player,
                                          boolean join) {
        return plugins == null ? text : plugins.host().playerMessage(text, player, join);
    }
    public static boolean changeSlot(net.minecraft.server.level.ServerPlayer player, int slot) {
        return plugins == null || player.getInventory().getSelectedSlot() == slot ||
            plugins.host().changeSlot(player, slot);
    }
    public static boolean allowMode(net.minecraft.server.level.ServerPlayer player,
                                    net.minecraft.world.level.GameType type) {
        return plugins == null || player.gameMode.getGameModeForPlayer() == type ||
            plugins.host().allowMode(player, type);
    }
    public static boolean allowFlight(boolean flying, net.minecraft.server.level.ServerPlayer player) {
        return plugins == null || player.getAbilities().flying == flying ||
            plugins.host().allowFlight(player, flying);
    }
    public static boolean containerInput(net.minecraft.server.level.ServerPlayer player,
                                         net.minecraft.network.protocol.game.ServerboundContainerClickPacket packet) {
        if (plugins == null || !plugins.host().containerInput(player, packet))
            return false;
        player.containerMenu.sendAllDataToRemote();
        return true;
    }
    public static mc.unity.api.PluginHost pluginHost() { return plugins==null ? null : plugins.host(); }
    public static boolean deferJoin() { return plugins!=null; }
    private record PearlHit(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.damagesource.DamageSource source,
                           net.minecraft.world.entity.projectile.Projectile pearl) {}
    private static final ThreadLocal<PearlHit> pearlHit=new ThreadLocal<>();
    public static boolean pearlDamage(net.minecraft.server.level.ServerPlayer player,net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.damagesource.DamageSource source,float amount,net.minecraft.world.entity.projectile.Projectile pearl) {
        if(plugins==null)return player.hurtServer(level,source,amount);
        var previous=pearlHit.get();
        pearlHit.set(new PearlHit(player,source,pearl));
        try { return player.hurtServer(level,source,amount); }
        finally { if(previous==null)pearlHit.remove();else pearlHit.set(previous); }
    }
    public static net.minecraft.world.entity.projectile.Projectile pearlDamager(net.minecraft.world.entity.LivingEntity player,
            net.minecraft.world.damagesource.DamageSource source) {
        var ctx=pearlHit.get();
        return ctx!=null && ctx.player==player && ctx.source==source ? ctx.pearl : null;
    }
    public static float acceptedDamage(net.minecraft.world.entity.LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount, float previous) {
        var host=pluginHost();
        return host==null ? amount : host.acceptedDamage(target,source,amount,previous);
    }
    public static void levelTick(net.minecraft.server.level.ServerPlayer player) {
        if (plugins != null)
            plugins.host().levelTick(player);
    }
    public static void chunkLoad(net.minecraft.world.level.chunk.LevelChunk chunk, boolean fresh) {
        if (plugins != null && chunk.getLevel() instanceof net.minecraft.server.level.ServerLevel)
            plugins.host().chunkLoad(chunk, fresh);
    }
    public static void entityUntracked(net.minecraft.world.entity.Entity entity) {
        if (plugins!=null && entity.isRemoved() && !(entity instanceof net.minecraft.server.level.ServerPlayer))
            plugins.host().entityUntracked(entity);
    }
    public static void worldSave(net.minecraft.server.level.ServerLevel level) {
        if (plugins != null)
            plugins.host().worldSave(level);
    }
    public static boolean saveUnloadingChunk(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        return plugins == null || !(chunk instanceof net.minecraft.world.level.chunk.LevelChunk full) ||
            plugins.host().saveUnloadingChunk(full);
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
                    -> Component.literal("Unity 0.3.0-Beta standalone | MC 26.2 | native " + names() +
                                         " | platform mods " + forge.ids() + " | plugins " +
                                         (plugins == null ? List.of() : plugins.host().names())),
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
            } catch (Throwable e) {
                mc.unity.core.Events.shutdownFailure(entry.id(), e);
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
                try {
                    if (plugins != null)
                        plugins.close();
                } finally {
                    plugins = null;
                    forge.close();
                }
            }
        } catch (java.io.IOException e) {
            System.getLogger("mc.unity").log(System.Logger.Level.ERROR, "Closing mod jars", e);
        }
        nativeMods = null;
        forge = null;
        System.out.println("[Unity] Standalone stopped");
    }
}
