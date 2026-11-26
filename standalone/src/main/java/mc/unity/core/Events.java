package mc.unity.core;
import com.mojang.brigadier.CommandDispatcher;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.commands.*;
import net.minecraft.world.item.CreativeModeTab;

public final class Events {
    public static void shutdownFailure(String owner, Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) throw fatal;
        if (failure instanceof ThreadDeath fatal) throw fatal;
        System.getLogger("mc.unity").log(System.Logger.Level.ERROR, "Stopping callback " + owner, failure);
    }
    public static final class TickSignal {
        private final CopyOnWriteArrayList<java.util.function.BiConsumer<
            net.minecraft.server.MinecraftServer, java.util.function.BooleanSupplier>> callbacks =
            new CopyOnWriteArrayList<>();
        public void listen(java.util.function.BiConsumer<net.minecraft.server.MinecraftServer,
                                                         java.util.function.BooleanSupplier> listener) {
            callbacks.add(listener);
        }
        public Runnable
        listenScoped(java.util.function.BiConsumer<net.minecraft.server.MinecraftServer,
                                                   java.util.function.BooleanSupplier> listener) {
            callbacks.add(listener);
            return () -> callbacks.remove(listener);
        }
        public void post(net.minecraft.server.MinecraftServer s, java.util.function.BooleanSupplier t) {
            for (var cb : callbacks)
                cb.accept(s, t);
        }
        void clear() {
            callbacks.clear();
        }
    }
    public static final Signal<net.minecraft.server.MinecraftServer> SERVER_ABOUT_TO_START = new Signal<>(),
                                                                     SERVER_STARTING = new Signal<>(),
                                                                     SERVER_STARTED = new Signal<>(),
                                                                     SERVER_STOPPING = new Signal<>(),
                                                                     SERVER_STOPPED = new Signal<>();
    public static final Signal<net.minecraft.server.level.ServerLevel> LEVEL_LOAD = new Signal<>(),
                                                                       LEVEL_UNLOAD = new Signal<>(),
                                                                       LEVEL_TICK_PRE = new Signal<>(),
                                                                       LEVEL_TICK_POST = new Signal<>();
    public static void loadLevel(Object value) {
        LEVEL_LOAD.post((net.minecraft.server.level.ServerLevel)value);
    }
    public static void unloadLevel(net.minecraft.server.level.ServerLevel value) {
        LEVEL_UNLOAD.postDuringShutdown(value);
    }
    public static void beforeLevelTick(net.minecraft.server.level.ServerLevel value) {
        LEVEL_TICK_PRE.post(value);
    }
    public static void afterLevelTick(net.minecraft.server.level.ServerLevel value) {
        LEVEL_TICK_POST.post(value);
    }
    public static final TickSignal SERVER_TICK_PRE = new TickSignal(), SERVER_TICK_POST = new TickSignal();
    public static final Signal<Void> CLIENT_TICK_PRE = new Signal<>(), CLIENT_TICK_POST = new Signal<>();
    public static final Signal<net.minecraft.server.level.ServerPlayer> PLAYER_LOGIN = new Signal<>(),
                                                                        PLAYER_LOGOUT = new Signal<>();
    public static void about(net.minecraft.server.MinecraftServer s) {
        SERVER_ABOUT_TO_START.post(s);
    }
    public static void starting(net.minecraft.server.MinecraftServer s) {
        SERVER_STARTING.post(s);
    }
    public static void started(net.minecraft.server.MinecraftServer s) {
        SERVER_STARTED.post(s);
    }
    public static void stopping(net.minecraft.server.MinecraftServer s) {
        SERVER_STOPPING.postDuringShutdown(s);
    }
    public static void stopped(net.minecraft.server.MinecraftServer s) {
        SERVER_STOPPED.postDuringShutdown(s);
    }
    public static void tickPre(net.minecraft.server.MinecraftServer s,
                               java.util.function.BooleanSupplier time) {
        SERVER_TICK_PRE.post(s, time);
    }
    public static void tickPost(net.minecraft.server.MinecraftServer s,
                                java.util.function.BooleanSupplier time) {
        SERVER_TICK_POST.post(s, time);
    }
    public static void clientPre() {
        CLIENT_TICK_PRE.post(null);
    }
    public static void clientPost() {
        CLIENT_TICK_POST.post(null);
    }
    public static void joined(net.minecraft.server.level.ServerPlayer p) {
        PLAYER_LOGIN.post(p);
    }
    public static void left(net.minecraft.server.level.ServerPlayer p) {
        PLAYER_LOGOUT.post(p);
    }
    public record BlockEntityChange(net.minecraft.world.level.block.entity.BlockEntity entity,
                                    net.minecraft.server.level.ServerLevel level) {}
    public static final Signal<BlockEntityChange> BLOCK_ENTITY_LOAD = new Signal<>(), BLOCK_ENTITY_UNLOAD =
                                                                                          new Signal<>();
    public static void loadBlockEntity(Object old, net.minecraft.world.level.block.entity.BlockEntity next) {
        if (next != old && next.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            BLOCK_ENTITY_LOAD.post(new BlockEntityChange(next, level));
    }
    public static void unloadBlockEntity(Object old) {
        if (old instanceof net.minecraft.world.level.block.entity.BlockEntity be &&
            be.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            BLOCK_ENTITY_UNLOAD.post(new BlockEntityChange(be, level));
    }
    public record Death(net.minecraft.world.entity.LivingEntity entity,
                        net.minecraft.world.damagesource.DamageSource source) {}
    public static final Signal<Death> AFTER_DEATH = new Signal<>();
    public static void died(net.minecraft.world.entity.LivingEntity entity,
                            net.minecraft.world.damagesource.DamageSource source) {
        if (!entity.level().isClientSide() && !AFTER_DEATH.empty())
            AFTER_DEATH.post(new Death(entity, source));
    }
    public static final Signal<Void> CLIENT_BEFORE_OPTIONS = new Signal<>();
    public static void clientBeforeOptions() {
        CLIENT_BEFORE_OPTIONS.post(null);
    }
    public static final Signal<Void> PLAY_REGISTRATION = new Signal<>();
    public record CommandsReady(CommandDispatcher<CommandSourceStack> dispatcher,
                                Commands.CommandSelection selection, CommandBuildContext context) {}
    public record Tooltip(net.minecraft.world.item.ItemStack stack,
                          net.minecraft.world.item.Item.TooltipContext context,
                          net.minecraft.world.item.TooltipFlag flag,
                          java.util.List<net.minecraft.network.chat.Component> lines) {}
    public static final Signal<Tooltip> TOOLTIP = new Signal<>();
    public static java.util.List<net.minecraft.network.chat.Component>
    tooltip(java.util.List<net.minecraft.network.chat.Component> original,
            net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
            net.minecraft.world.item.TooltipFlag flag) {
        if (TOOLTIP.empty())
            return original;
        var result = new java.util.ArrayList<>(original);
        TOOLTIP.post(new Tooltip(stack, context, flag, result));
        return result;
    }
    public record Tab(CreativeModeTab tab, Object output) {}
    public static final Signal<Void> INIT = new Signal<>(), CLIENT_SETUP = new Signal<>();
    public static final Signal<CommandsReady> COMMANDS = new Signal<>();
    public static final Signal<Tab> CREATIVE = new Signal<>();
    public static final class Signal<T> {
        private record Slot<T>(byte priority, Consumer<T> consumer) {}
        private final CopyOnWriteArrayList<Slot<T>> listeners = new CopyOnWriteArrayList<>();
        public synchronized Runnable listen(byte priority, Consumer<T> cb) {
            var slot = new Slot<>(priority, cb);
            int i = 0;
            while (i < listeners.size() && listeners.get(i).priority >= priority)
                i++;
            listeners.add(i, slot);
            return () -> remove(slot);
        }
        private synchronized void remove(Slot<T> slot) {
            listeners.remove(slot);
        }

        public boolean empty() {
            return listeners.isEmpty();
        }
        public Runnable listen(Consumer<T> listener) {
            return listen((byte)0, listener);
        }
        public void postDuringShutdown(T event) {
            for (var slot : listeners) {
                try { slot.consumer.accept(event); }
                catch (Throwable failure) { shutdownFailure(slot.consumer.getClass().getName(), failure); }
            }
        }
        public void post(T event) {
            for (var listener : listeners)
                listener.consumer.accept(event);
        }
        public synchronized void clear() {
            listeners.clear();
        }
    }
    static void clear() {
        for (var s : java.util.List.of(SERVER_ABOUT_TO_START, SERVER_STARTING, SERVER_STARTED,
                                       SERVER_STOPPING, SERVER_STOPPED, CLIENT_TICK_PRE, CLIENT_TICK_POST,
                                       PLAYER_LOGIN, PLAYER_LOGOUT))
            s.clear();
        LEVEL_LOAD.clear();
        LEVEL_UNLOAD.clear();
        LEVEL_TICK_PRE.clear();
        LEVEL_TICK_POST.clear();
        SERVER_TICK_PRE.clear();
        SERVER_TICK_POST.clear();
        BLOCK_ENTITY_LOAD.clear();
        BLOCK_ENTITY_UNLOAD.clear();
        Reloads.COLLECT.clear();
        TOOLTIP.clear();
        AFTER_DEATH.clear();
        Actions.clear();
        PLAY_REGISTRATION.clear();
        INIT.clear();
        CLIENT_SETUP.clear();
        CLIENT_BEFORE_OPTIONS.clear();
        COMMANDS.clear();
        CREATIVE.clear();
    }
}
