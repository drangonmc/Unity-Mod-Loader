package mc.unity.plugins;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.Logger;
import mc.unity.api.PluginHost;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class Host implements PluginHost {
    static Host current;
    static Host active() {
        return Objects.requireNonNull(current);
    }
    Path directory;
    MinecraftServer game;
    Server server;
    BukkitViews views;
    final Map<String, PluginFile.Loader> loaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final Map<String, org.bukkit.command.Command> commands = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final List<JavaPlugin> enabled = new ArrayList<>();
    final List<PaperHandler> paper = new ArrayList<>();
    private List<PluginFile> files;
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private final Map<UUID, Integer> levels = new HashMap<>();
    final Map<String, PluginFile.Loader> providers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private boolean worldsReady;
    private Runnable detachGameplay;
    private boolean closed;
    private record PaperHandler(Plugin plugin, LifecycleEventHandler handler) {}
    public static final Logger LOG = Logger.getLogger("mc.unity.plugins");

    @Override
    public void initialize(Path path) throws Exception {
        current = this;
        directory = path;
        files = PluginFile.ordered(path);
    }
    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> next) {
        dispatcher = next;
        if (!enabled.isEmpty())
            bind();
    }
    @Override
    public void aboutToStart(MinecraftServer game) {
        this.game = game;
        if (files.isEmpty())
            return;
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            views = new BukkitViews(this);
            server = views.server();
            detachGameplay = mc.unity.core.Actions.attachScoped(new GameplayEvents(this));
            Bukkit.setServer(server);
            views.bindCommands(server);
            for (var f : files)
                loaders.put(f.name(), new PluginFile.Loader(f, this));
            PluginFile.providers(files).forEach((id, name) -> providers.put(id, loaders.get(name)));
            for (var loader : loaders.values())
                loader.link(providers);
            for (var f : files) {
                var cl = loaders.get(f.name());
                Thread.currentThread().setContextClassLoader(cl);
                var plugin = Class.forName(f.description().getMain(), true, cl)
                                 .asSubclass(JavaPlugin.class)
                                 .getConstructor()
                                 .newInstance();
                views.permissions(plugin);
                if (!f.paper())
                    declare(plugin);
                plugin.onLoad();
            }
            enable(org.bukkit.plugin.PluginLoadOrder.STARTUP);
        } catch (Exception | Error ex) {
            try {
                close();
            } catch (RuntimeException | Error cleanup) {
                ex.addSuppressed(cleanup);
            }
            if (ex instanceof Error error)
                throw error;
            throw new IllegalStateException("Unity plug-in startup failed", ex);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
    private void enable(org.bukkit.plugin.PluginLoadOrder order) {
        for (var f : files) {
            var cl = loaders.get(f.name());
            if (cl.plugin.getDescription().getLoad() != order || cl.plugin.isEnabled())
                continue;
            Thread thread = Thread.currentThread();
            var previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(cl);
                enabled.add(cl.plugin);
                cl.enable();
                fire(new org.bukkit.event.server.PluginEnableEvent(cl.plugin));
                LOG.info("Enabled " + f.name() + " " + f.description().getVersion());
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }
    @Override
    public void serverStarted(MinecraftServer server) {
        if (views == null)
            return;
        for (var level : server.getAllLevels())
            fire(new WorldLoadEvent(views.world(level)));
        worldsReady = true;
        enable(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);
        if (dispatcher != null)
            bind();
        fire(new org.bukkit.event.server.ServerLoadEvent(
            org.bukkit.event.server.ServerLoadEvent.LoadType.STARTUP));
    }
    private void declare(JavaPlugin plugin) throws ReflectiveOperationException {
        var constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        for (var entry : plugin.getDescription().getCommands().entrySet()) {
            var cmd = constructor.newInstance(entry.getKey(), plugin);
            var spec = entry.getValue();
            if (spec.get("permission") instanceof String p)
                cmd.setPermission(p);
            if (spec.get("usage") instanceof String u)
                cmd.setUsage(u);
            if (spec.get("description") instanceof String d)
                cmd.setDescription(d);
            Object aliases = spec.get("aliases");
            if (aliases instanceof List<?> a)
                cmd.setAliases(a.stream().map(Object::toString).toList());
            else if (aliases instanceof String a)
                cmd.setAliases(List.of(a));
            var labels = new ArrayList<>(cmd.getAliases());
            labels.add(entry.getKey());
            for (var label : labels) {
                commands.putIfAbsent(label, cmd);
                commands.put(plugin.getName().toLowerCase(Locale.ROOT) + ":" + label, cmd);
            }
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    LifecycleEventManager<Plugin> lifecycle(Plugin plugin) {
        return Views.of(LifecycleEventManager.class, "Paper lifecycle", (m, a) -> {
            if (m.equals("registerEventHandler") && a.length == 2 && a[0] == LifecycleEvents.COMMANDS) {
                paper.add(new PaperHandler(plugin, (LifecycleEventHandler)a[1]));
                return null;
            }
            throw Views.unsupported("Paper lifecycle", m);
        });
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void bind() {
        for (var entry : commands.entrySet()) {
            var cmd = entry.getValue();
            var name = entry.getKey();
            register(name, cmd::testPermissionSilent,
                     (source, args) -> cmd.execute(views.sender(source), name, args) ? 1 : 0);
        }
        for (var entry : paper) {
            var registrar =
                Views.of(io.papermc.paper.command.brigadier.Commands.class, "Paper commands", (m, a) -> {
                    if (m.equals("register") && a[0] instanceof String name &&
                        a[a.length - 1] instanceof BasicCommand cmd) {
                        Set<String> labels = new LinkedHashSet<>();
                        labels.add(name);
                        labels.add(entry.plugin.getName().toLowerCase(Locale.ROOT) + ":" + name);
                        for (var value : a)
                            if (value instanceof Collection<?> list)
                                for (var s : list)
                                    labels.add(s.toString());
                        for (var label : labels)
                            register(label, cmd::canUse, (source, args) -> {
                                var paperSource = Views.of(
                                    io.papermc.paper.command.brigadier.CommandSourceStack.class,
                                    "Paper source", (method, params) -> {
                                        if (method.equals("getSender"))
                                            return views.sender(source);
                                        if (method.equals("getExecutor") || method.equals("getPlayerOrThrow"))
                                            return views.player(source.getPlayerOrException());
                                        throw Views.unsupported("Paper source", method);
                                    });
                                cmd.execute(paperSource, args);
                                return 1;
                            });
                        return Set.copyOf(labels);
                    }
                    throw Views.unsupported("Paper commands", m);
                });
            entry.handler.run(Views.of(ReloadableRegistrarEvent.class, "Paper COMMANDS", (m, a) -> {
                if (m.equals("registrar"))
                    return registrar;
                throw Views.unsupported("Paper COMMANDS", m);
            }));
        }
    }
    private void register(String name, Predicate<CommandSender> allowed,
                          ToIntBiFunction<CommandSourceStack, String[]> callback) {
        if (dispatcher.getRoot().getChild(name) != null)
            return;
        var node =
            Commands.literal(name)
                .requires(s -> allowed.test(views.sender(s)))
                .executes(c -> callback.applyAsInt(c.getSource(), new String[0]))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                          .executes(c
                                    -> callback.applyAsInt(
                                        c.getSource(), StringArgumentType.getString(c, "args").split(" +"))));
        dispatcher.register(node);
    }
    void listen(Listener listener, Plugin owner) {
        PluginEvents.listen(listener, owner);
    }
    private static final boolean AUDIT = Boolean.getBoolean("mc.unity.contentQA");
    private final Set<Class<?>> emitted = java.util.concurrent.ConcurrentHashMap.newKeySet();
    void fire(Event event) {
        PluginEvents.fire(event, game.isSameThread());
        if (AUDIT && emitted.add(event.getClass()))
            LOG.info("Unity Bukkit game event " + event.getClass().getName());
    }
    @Override
    public net.minecraft.network.chat.Component playerMessage(net.minecraft.network.chat.Component original,
                                                              ServerPlayer player, boolean join) {
        if (join)
            levels.put(player.getUUID(), player.experienceLevel);
        else
            levels.remove(player.getUUID());
        if (views == null || enabled.isEmpty())
            return original;
        var handlers = join ? org.bukkit.event.player.PlayerJoinEvent.getHandlerList()
                            : org.bukkit.event.player.PlayerQuitEvent.getHandlerList();
        if (handlers.getRegisteredListeners().length == 0)
            return original;
        var ops =
            player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var serializer = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson();
        var component =
            original == null
                ? null
                : serializer.deserializeFromTree(
                      net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(ops, original)
                          .getOrThrow());
        net.kyori.adventure.text.Component changed;
        if (join) {
            var event = new org.bukkit.event.player.PlayerJoinEvent(views.player(player), component);
            fire(event);
            changed = event.joinMessage();
        } else {
            var event = new org.bukkit.event.player.PlayerQuitEvent(
                views.player(player), component,
                org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED);
            fire(event);
            changed = event.quitMessage();
        }
        if (changed == null)
            return null;
        return Objects.equals(component, changed) ? original
                                                  : net.minecraft.network.chat.ComponentSerialization.CODEC
                                                        .parse(ops, serializer.serializeToTree(changed))
                                                        .getOrThrow();
    }
    @Override
    public boolean changeSlot(ServerPlayer player, int slot) {
        if (views == null ||
            org.bukkit.event.player.PlayerItemHeldEvent.getHandlerList().getRegisteredListeners().length == 0)
            return true;
        var prev = player.getInventory().getSelectedSlot();
        var event = new org.bukkit.event.player.PlayerItemHeldEvent(views.player(player), prev, slot);
        fire(event);
        if (event.isCancelled())
            player.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(prev));
        return !event.isCancelled();
    }
    @Override
    public void chunkLoad(net.minecraft.world.level.chunk.LevelChunk chunk, boolean fresh) {
        if (views != null && ChunkLoadEvent.getHandlerList().getRegisteredListeners().length > 0) {
            var pos = chunk.getPos();
            fire(new ChunkLoadEvent(views.chunk((ServerLevel)chunk.getLevel(), pos.x(), pos.z()), fresh));
        }
    }
    @Override
    public boolean allowMode(ServerPlayer player, net.minecraft.world.level.GameType type) {
        if (views == null || org.bukkit.event.player.PlayerGameModeChangeEvent.getHandlerList()
                                     .getRegisteredListeners()
                                     .length == 0)
            return true;
        var e = new org.bukkit.event.player.PlayerGameModeChangeEvent(views.player(player),
                                                                      GameMode.valueOf(type.name()));
        fire(e);
        if (e.isCancelled() && e.cancelMessage() != null)
            views.player(player).sendMessage(e.cancelMessage());
        return !e.isCancelled();
    }
    @Override
    public boolean allowFlight(ServerPlayer player, boolean flying) {
        if (views == null || org.bukkit.event.player.PlayerToggleFlightEvent.getHandlerList()
                                     .getRegisteredListeners()
                                     .length == 0)
            return true;
        var e = new org.bukkit.event.player.PlayerToggleFlightEvent(views.player(player), flying);
        fire(e);
        if (e.isCancelled())
            player.onUpdateAbilities();
        return !e.isCancelled();
    }
    @Override
    public float acceptedDamage(net.minecraft.world.entity.LivingEntity target, net.minecraft.world.damagesource.DamageSource source,
                                float amount,float previous) {
        if(enabled.isEmpty() || org.bukkit.event.entity.EntityDamageEvent.getHandlerList().getRegisteredListeners().length==0)return amount;
        return DamageEvents.fire(this,target,source,amount,previous);
    }
    public net.minecraft.world.item.ItemStack dispense(net.minecraft.core.dispenser.BlockSource source, net.minecraft.core.BlockPos target, net.minecraft.world.item.ItemStack offered) {
        if(enabled.isEmpty() || org.bukkit.event.block.BlockDispenseEvent.getHandlerList().getRegisteredListeners().length==0)return offered;
        var event=new org.bukkit.event.block.BlockDispenseEvent(views.blocks.block(source.level(),source.pos()),
            new BlockViews.Stack(offered),new org.bukkit.util.Vector(target.getX(),target.getY(),target.getZ()));
        fire(event);
        if(event.isCancelled())return null;
        var chosen=BlockViews.Stack.nativeCopy(event.getItem());
        return net.minecraft.world.item.ItemStack.matches(offered,chosen)?offered:chosen;
    }
    public net.minecraft.world.level.block.state.BlockState cauldron(ServerPlayer p,net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState proposed,String change) {
        if(enabled.isEmpty() || org.bukkit.event.block.CauldronLevelChangeEvent.getHandlerList().getRegisteredListeners().length==0)return proposed;
        var next=new java.util.concurrent.atomic.AtomicReference<>(proposed);
        var reason=org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.valueOf(change);
        var event=new org.bukkit.event.block.CauldronLevelChangeEvent(views.blocks.block(p.level(),pos),views.player(p),reason,
            views.blocks.snapshot(p.level(),pos,next));
        fire(event);return event.isCancelled()?null:next.get();
    }
    public boolean observesCauldron() {
        return !enabled.isEmpty() && org.bukkit.event.block.CauldronLevelChangeEvent.getHandlerList().getRegisteredListeners().length!=0;
    }
    public boolean observesBucket(boolean fill) {
        var handlers=fill ? org.bukkit.event.player.PlayerBucketFillEvent.getHandlerList() : org.bukkit.event.player.PlayerBucketEmptyEvent.getHandlerList();
        return !enabled.isEmpty() && handlers.getRegisteredListeners().length!=0;
    }
    public net.minecraft.world.item.ItemStack bucket(ServerPlayer player,net.minecraft.core.BlockPos target,
            net.minecraft.world.phys.BlockHitResult hit,net.minecraft.world.InteractionHand hand,boolean fill,
            net.minecraft.world.item.ItemStack result) {
        var handlers=fill ? org.bukkit.event.player.PlayerBucketFillEvent.getHandlerList() : org.bukkit.event.player.PlayerBucketEmptyEvent.getHandlerList();
        if(enabled.isEmpty() || handlers.getRegisteredListeners().length==0)return result;
        var p=views.player(player);
        var affected=views.blocks.block(player.level(),target);
        var clicked=views.blocks.block(player.level(),hit.getBlockPos());
        var face=org.bukkit.block.BlockFace.valueOf(hit.getDirection().name());
        var slot=hand==net.minecraft.world.InteractionHand.MAIN_HAND ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        var material=new BlockViews.Stack(player.getItemInHand(hand)).getType();
        var offered=new BlockViews.Stack(result);
        org.bukkit.event.player.PlayerBucketEvent event=fill ?
            new org.bukkit.event.player.PlayerBucketFillEvent(p,affected,clicked,face,material,offered,slot) :
            new org.bukkit.event.player.PlayerBucketEmptyEvent(p,affected,clicked,face,material,offered,slot);
        fire(event);
        if(event.isCancelled())return null;
        var chosen=BlockViews.Stack.nativeCopy(event.getItemStack());
        return net.minecraft.world.item.ItemStack.matches(result,chosen) ? result : chosen;
    }
    public boolean shoot(net.minecraft.world.entity.LivingEntity shooter,net.minecraft.world.item.ItemStack bow,
                         net.minecraft.world.item.ItemStack ammo,net.minecraft.world.entity.projectile.Projectile projectile,
                         net.minecraft.world.InteractionHand hand,float force) {
        if(enabled.isEmpty() || org.bukkit.event.entity.EntityShootBowEvent.getHandlerList().getRegisteredListeners().length==0)return true;
        var entity=views.entity(projectile);
        var event=new org.bukkit.event.entity.EntityShootBowEvent((org.bukkit.entity.LivingEntity)views.entity(shooter),
            new BlockViews.Stack(bow),new BlockViews.Stack(ammo),entity,
            hand==net.minecraft.world.InteractionHand.MAIN_HAND ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND,force,true);
        fire(event);
        return !event.isCancelled() && event.getProjectile()==entity;
    }
    public boolean launch(net.minecraft.world.entity.projectile.Projectile projectile) {
        if(enabled.isEmpty() || org.bukkit.event.entity.ProjectileLaunchEvent.getHandlerList().getRegisteredListeners().length==0)return true;
        var event=new org.bukkit.event.entity.ProjectileLaunchEvent((org.bukkit.entity.Projectile)views.entity(projectile));
        fire(event);return !event.isCancelled();
    }
    public boolean hit(net.minecraft.world.entity.projectile.Projectile projectile,net.minecraft.world.phys.HitResult hit) {
        if(enabled.isEmpty() || org.bukkit.event.entity.ProjectileHitEvent.getHandlerList().getRegisteredListeners().length==0)return true;
        var block=hit instanceof net.minecraft.world.phys.BlockHitResult b ? views.blocks.block((net.minecraft.server.level.ServerLevel)projectile.level(),b.getBlockPos()) : null;
        var face=hit instanceof net.minecraft.world.phys.BlockHitResult b ? org.bukkit.block.BlockFace.valueOf(b.getDirection().name()) : null;
        var target=hit instanceof net.minecraft.world.phys.EntityHitResult e ? views.entity(e.getEntity()) : null;
        var event=new org.bukkit.event.entity.ProjectileHitEvent((org.bukkit.entity.Projectile)views.entity(projectile),target,block,face);
        fire(event);return !event.isCancelled();
    }
    @Override
    public boolean containerInput(ServerPlayer player, net.minecraft.network.protocol.game.ServerboundContainerClickPacket packet) {
        return !enabled.isEmpty() && InventoryEvents.input(this, player, packet);
    }
    @Override
    public void levelTick(ServerPlayer player) {
        if (views == null || !levels.containsKey(player.getUUID()))
            return;
        int old = levels.get(player.getUUID());
        if (old == player.experienceLevel)
            return;
        levels.put(player.getUUID(), player.experienceLevel);
        if (org.bukkit.event.player.PlayerLevelChangeEvent.getHandlerList().getRegisteredListeners().length >
            0)
            fire(new org.bukkit.event.player.PlayerLevelChangeEvent(views.player(player), old,
                                                                    player.experienceLevel));
    }
    @Override
    public void worldSave(ServerLevel level) {
        if (views != null && WorldSaveEvent.getHandlerList().getRegisteredListeners().length > 0)
            fire(new WorldSaveEvent(views.world(level)));
    }
    @Override
    public boolean saveUnloadingChunk(net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (views == null || ChunkUnloadEvent.getHandlerList().getRegisteredListeners().length == 0)
            return true;
        var pos = chunk.getPos();
        var event = new ChunkUnloadEvent(views.chunk((ServerLevel)chunk.getLevel(), pos.x(), pos.z()), true);
        fire(event);
        return event.isSaveChunk();
    }
    @Override
    public void tick(MinecraftServer game) {
        if (views != null)
            views.tick();
    }
    @Override
    public void worldEvent(ServerLevel level, boolean load) {
        if (views == null || enabled.isEmpty())
            return;
        var w = views.world(level);
        if (load) {
            fire(new WorldInitEvent(w));
            if (worldsReady)
                fire(new WorldLoadEvent(w));
        } else
            fire(new WorldUnloadEvent(w));
    }
    @Override
    public List<String> names() {
        return enabled.stream().map(JavaPlugin::getName).toList();
    }
    @Override
    public void serverStopping(MinecraftServer game) {
        for (var p : enabled.reversed())
            try {
                fire(new org.bukkit.event.server.PluginDisableEvent(p));
                try {
                    p.setEnabled(false);
                } finally {
                    HandlerList.unregisterAll(p);
                    views.tasks.call("cancelTasks", new Object[] {p});
                }
            } catch (Throwable e) {
                if (e instanceof VirtualMachineError fatal)
                    throw fatal;
                if (e instanceof ThreadDeath death)
                    throw death;
                LOG.log(java.util.logging.Level.SEVERE, "Disabling " + p.getName(), e);
            }
        levels.clear();
        worldsReady = false;
        enabled.clear();
        paper.clear();
        commands.clear();
    }
    @Override
    public void close() {
        if (closed)
            return;
        closed = true;
        if (detachGameplay != null) {
            detachGameplay.run();
            detachGameplay = null;
        }
        try {
            if (!enabled.isEmpty())
                serverStopping(game);
        } finally {
            try {
                if (views != null)
                    views.tasks.close();
            } finally {
                for (var cl : loaders.values())
                    try {
                        cl.close();
                    } catch (java.io.IOException e) {
                        LOG.warning(e.toString());
                    }
                providers.clear();
                loaders.clear();
                if (current == this)
                    current = null;
            }
        }
    }
}
