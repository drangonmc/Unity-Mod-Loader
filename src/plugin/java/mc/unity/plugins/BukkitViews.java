package mc.unity.plugins;

import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;

final class BukkitViews {
    private static final java.lang.invoke.VarHandle SOURCE, VISIBLE_CHUNKS;
    static {
        try {
            var map=net.minecraft.server.level.ChunkMap.class;
            VISIBLE_CHUNKS=java.lang.invoke.MethodHandles.privateLookupIn(map,java.lang.invoke.MethodHandles.lookup())
                .findVarHandle(map,"visibleChunkMap",it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap.class);
            SOURCE = java.lang.invoke.MethodHandles
                         .privateLookupIn(CommandSourceStack.class, java.lang.invoke.MethodHandles.lookup())
                         .findVarHandle(CommandSourceStack.class, "source",
                                        net.minecraft.commands.CommandSource.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    final BlockViews blocks = new BlockViews(this);
    private final Host host;
    private static final class EntityMetadata extends org.bukkit.metadata.MetadataStoreBase<UUID> {
        final boolean player;
        final java.lang.ref.WeakReference<net.minecraft.world.entity.Entity> owner;
        EntityMetadata(net.minecraft.world.entity.Entity entity) { player = entity instanceof ServerPlayer; owner = new java.lang.ref.WeakReference<>(entity); }
        protected String disambiguate(UUID id, String key) { return key; }
    }
    private final Map<UUID, EntityMetadata> metadata = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ServerLevel, World> worlds = new java.util.concurrent.ConcurrentHashMap<>();
    private int metadataTicks;
    private Object metadata(net.minecraft.world.entity.Entity entity, String method, Object[] args) {
        UUID id = entity.getUUID();
        String key = (String)args[0];
        if (method.equals("setMetadata")) {
            metadata.computeIfAbsent(id, ignored -> new EntityMetadata(entity)).setMetadata(id,key,(org.bukkit.metadata.MetadataValue)args[1]);
            return null;
        }
        var store = metadata.get(id);
        return switch (method) {
            case "hasMetadata" -> store != null && store.hasMetadata(id,key);
            case "getMetadata" -> store == null ? List.of() : store.getMetadata(id,key);
            case "removeMetadata" -> { if (store != null) store.removeMetadata(id,key,(Plugin)args[1]); yield null; }
            default -> throw new AssertionError(method);
        };
    }
    void close() { try { tasks.close(); } finally { worlds.clear();metadata.clear(); } }
    void forget(net.minecraft.world.entity.Entity entity) {
        var store=metadata.get(entity.getUUID());
        if(store!=null && store.owner.get()==entity) metadata.remove(entity.getUUID(),store);
    }
    void forget(UUID player) { metadata.remove(player); }
    void forget(ServerLevel world) {
        worlds.remove(world);
        metadata.entrySet().removeIf(entry -> {
            var entity = entry.getValue().owner.get();
            return !entry.getValue().player && (entity == null || entity.level()==world);
        });
    }
    final PluginHelp help;
    final PluginTasks tasks = new PluginTasks();
    private final Map<String, Permission> permissions = new HashMap<>();
    private PluginManager manager;
    private SimpleCommandMap commandMap;
    private final SimpleServicesManager services = new SimpleServicesManager();
    private final org.bukkit.plugin.messaging.StandardMessenger messenger = new org.bukkit.plugin.messaging.StandardMessenger();
    void bindCommands(Server server) {
        commandMap=new SimpleCommandMap(server,host.commands);
        manager=new SessionPluginManager(commandMap,manager);
    }
    private final BukkitScheduler scheduler;
    BukkitViews(Host host) {
        this.host = host;
        help = new PluginHelp(host.commands);
        manager = Views.of(PluginManager.class, "plugin manager", this::manage);
        scheduler = Views.of(BukkitScheduler.class, "scheduler", tasks::call);
    }
    @SuppressWarnings("unchecked")
    private org.bukkit.block.data.BlockData createBlockData(Object[] arguments) {
        String input;
        if (arguments[0] instanceof Material material) {
            if (!material.isBlock()) throw new IllegalArgumentException("Not a block: " + material);
            input = material.getKey().toString();
            if (arguments.length == 2 && arguments[1] instanceof String states) input += states;
        } else {
            Object raw = arguments.length == 2 ? arguments[1] : arguments[0];
            if (!(raw instanceof String value)) throw new IllegalArgumentException("Missing block data");
            input = value;
        }
        try {
            var nativeState = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                host.game.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), input, false);
            var data = BlockViews.data(nativeState.blockState());
            if (arguments.length == 2 && arguments[1] instanceof java.util.function.Consumer<?> callback)
                ((java.util.function.Consumer<org.bukkit.block.data.BlockData>)callback).accept(data);
            return data;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException(input, e);
        }
    }
    Server server() {
        return Views.of(Server.class, "Unity", (m, a) -> switch (m) {
            case "getName" -> "Unity";
            case "createBlockData" -> createBlockData(a);
            case "getVersion" -> "Unity 0.2.8-Beta (MC: 26.2)";
            case "getBukkitVersion" -> "26.2-R0.1-SNAPSHOT";
            case "getMinecraftVersion" -> "26.2";
            case "getLogger" -> Host.LOG;
            case "getPluginManager" -> manager;
            case "getHelpMap" -> help;
            case "getCommandMap" -> commandMap;
            case "getTags" -> tags(a[0].toString(),(Class<?>)a[1],null);
            case "getTag" -> tags(a[0].toString(),(Class<?>)a[2],(NamespacedKey)a[1]).stream().findFirst().orElse(null);
            case "getServicesManager" -> services;
            case "getMessenger" -> messenger;
            case "getScheduler" -> scheduler;
            case "getConsoleSender" -> sender(host.game.createCommandSourceStack());
            case "getPluginCommand" -> host.commands.get(a[0].toString()) instanceof PluginCommand pc ? pc : null;
            case "getWorlds" -> levels().stream().map(this::world).toList();
            case "getWorld" ->
                levels()
                    .stream()
                    .map(this::world)
                    .filter(
                        w -> w.getName().equals(a[0]) || w.getUID().equals(a[0]) || w.getKey().equals(a[0]))
                    .findFirst()
                    .orElse(null);
            case "getEntity" -> levels().stream().map(level->level.getEntity((UUID)a[0])).filter(Objects::nonNull).findFirst().map(this::entity).orElse(null);
            case "getOnlinePlayers" ->
                host.game.getPlayerList().getPlayers().stream().map(this::player).toList();
            case "getPlayer", "getPlayerExact" -> {
                var p = a[0] instanceof UUID id ? host.game.getPlayerList().getPlayer(id)
                                                : host.game.getPlayerList().getPlayerByName(a[0].toString());
                yield p == null ? null : player(p);
            }
            case "getSpawnRadius" -> host.game instanceof net.minecraft.server.dedicated.DedicatedServer server ? server.getProperties().spawnProtection.get() : 0;
            case "getMaxPlayers" -> host.game.getMaxPlayers();
            case "getPort" -> host.game.getPort();
            case "getOnlineMode" -> host.game.usesAuthentication();
            case "isPrimaryThread" -> host.game.isSameThread();
            case "getCurrentTick" -> host.game.getTickCount();
            case "getWorldContainer" -> host.directory.getParent().toFile();
            case "getPluginsFolder" -> host.directory.toFile();
            default -> throw Views.unsupported("Server", m);
        });
    }
    private static List<Tag<Keyed>> tags(String kind,Class<?> type,NamespacedKey wanted) {
        net.minecraft.core.Registry<?> registry=switch(kind){
            case "blocks" -> net.minecraft.core.registries.BuiltInRegistries.BLOCK;
            case "items" -> net.minecraft.core.registries.BuiltInRegistries.ITEM;
            case "entity_types" -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
            case "fluids" -> net.minecraft.core.registries.BuiltInRegistries.FLUID;
            case "game_events" -> net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT;
            default -> throw Views.unsupported("Server","tags "+kind);
        };
        List<Tag<Keyed>> result=new ArrayList<>();
        var groups=registry.getTags();
        if(wanted!=null)groups=groups.filter(group->group.key().location().toString().equals(wanted.toString()));
        groups.forEach(holders->result.add(new Tag<Keyed>(){
            public NamespacedKey getKey(){return NamespacedKey.fromString(holders.key().location().toString());}
            public boolean isTagged(Keyed item){return type.isInstance(item) && holders.stream().anyMatch(h->h.unwrapKey().orElseThrow().identifier().toString().equals(item.getKey().toString()));}
            public Set<Keyed> getValues(){
                Set<Keyed> values=new LinkedHashSet<>();
                holders.stream().map(h->h.unwrapKey().orElseThrow().identifier()).forEach(id->{
                    var k=NamespacedKey.fromString(id.toString());
                    Keyed v=type==Material.class ? BlockViews.material(id)
                        : switch(kind){
                            case "fluids" -> org.bukkit.Registry.FLUID.get(k);
                            case "entity_types" -> org.bukkit.Registry.ENTITY_TYPE.get(k);
                            case "game_events" -> org.bukkit.Registry.GAME_EVENT.get(k);
                            default -> throw Views.unsupported("Server","tags "+type);
                        };
                    if(v!=null)values.add(v);
                });
                return Collections.unmodifiableSet(values);
            }
        }));
        return result;
    }
    private List<ServerLevel> levels() {
        var list = new ArrayList<ServerLevel>();
        host.game.getAllLevels().forEach(list::add);
        return list;
    }
    private Object manage(String m, Object[] a) {
        return switch (m) {
            case "getPlugin" -> {
                var l = host.providers.get(a[0].toString());
                yield l == null ? null : l.plugin;
            }
            case "getPlugins" ->
                host.loaders.values()
                    .stream()
                    .map(l -> l.plugin)
                    .filter(Objects::nonNull)
                    .toArray(Plugin[] ::new);
            case "isPluginEnabled" -> {
                var p = a[0] instanceof Plugin v ? v : (Plugin)manage("getPlugin", a);
                yield p != null &&p.isEnabled();
            }
            case "clearPermissions" -> { permissions.clear(); yield null; }
            case "getPermission" -> permissions.get(a[0].toString().toLowerCase(Locale.ROOT));
            case "addPermission" -> {
                var p = (Permission)a[0];
                permissions.put(p.getName().toLowerCase(Locale.ROOT), p);
                yield null;
            }
            case "registerEvents" -> {
                host.listen((Listener)a[0], (Plugin)a[1]);
                yield null;
            }
            case "registerEvent" -> {
                PluginEvents.register(((Class<?>)a[0]).asSubclass(Event.class), (Listener)a[1],
                                      (org.bukkit.event.EventPriority)a[2], (EventExecutor)a[3], (Plugin)a[4],
                                      a.length > 5 && (boolean)a[5]);
                yield null;
            }
            case "getRegisteredListeners" ->
                org.bukkit.event.HandlerList.getRegisteredListeners((Plugin)a[0]);
            case "callEvent" -> {
                PluginEvents.fire((Event)a[0], host.game.isSameThread());
                yield null;
            }
            case "getPermissionSubscriptions", "getDefaultPermSubscriptions" -> Set.of();
            default -> throw Views.unsupported("PluginManager", m);
        };
    }
    void permissions(Plugin plugin) {
        for (var p : plugin.getDescription().getPermissions())
            permissions.putIfAbsent(p.getName().toLowerCase(Locale.ROOT), p);
    }
    CommandSender sender(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer p)
            return player(p);
        if (SOURCE.get(src) == host.game)
            return Views.of(ConsoleCommandSender.class, "Console", (m, a) -> senderCall(src, null, m, a));
        return Views.of(CommandSender.class, src.getTextName(), (m, a) -> senderCall(src, null, m, a));
    }
    org.bukkit.entity.Entity entity(net.minecraft.world.entity.Entity raw) {
        if (raw instanceof ServerPlayer player)
            return player(player);
        var id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(raw.getType());
        org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.UNKNOWN;
        if (id.getNamespace().equals("minecraft")) {
            try {
                type = org.bukkit.entity.EntityType.valueOf(id.getPath().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException noEnum) {}
        }
        final var kind = type;
        var iface = kind.getEntityClass();
        if (iface == null)
            iface = raw instanceof net.minecraft.world.entity.LivingEntity
                        ? org.bukkit.entity.LivingEntity.class
                        : org.bukkit.entity.Entity.class;
        if(raw instanceof net.minecraft.world.entity.projectile.Projectile && !org.bukkit.entity.Projectile.class.isAssignableFrom(iface))
            iface=org.bukkit.entity.Projectile.class;
        return Views.of(iface, raw.getName().getString(), raw.getUUID(), (m, a) -> switch (m) {
            case "getBasePotionType" -> {
                var contents=((net.minecraft.world.entity.projectile.arrow.AbstractArrow)raw).getPickupItemStackOrigin()
                    .getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,net.minecraft.world.item.alchemy.PotionContents.EMPTY);
                yield contents.potion().map(holder->org.bukkit.potion.PotionType.valueOf(holder.unwrapKey().orElseThrow().identifier().getPath().toUpperCase(Locale.ROOT))).orElse(null);
            }
            case "getCustomEffects" -> {
                var contents=((net.minecraft.world.entity.projectile.arrow.AbstractArrow)raw).getPickupItemStackOrigin()
                    .getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,net.minecraft.world.item.alchemy.PotionContents.EMPTY);
                yield contents.customEffects().stream().map(effect->new org.bukkit.potion.PotionEffect(
                    org.bukkit.Registry.MOB_EFFECT.getOrThrow(NamespacedKey.fromString(effect.getEffect().unwrapKey().orElseThrow().identifier().toString())),
                    effect.getDuration(),effect.getAmplifier(),effect.isAmbient(),effect.isVisible(),effect.showIcon())).toList();
            }
            case "getPierceLevel" -> (int)((net.minecraft.world.entity.projectile.arrow.AbstractArrow)raw).getPierceLevel();
            case "getShooter" -> {
                if (!(raw instanceof net.minecraft.world.entity.projectile.Projectile projectile)) throw Views.unsupported("Entity",m);
                var owner=projectile.getOwner();
                yield owner==null ? null : entity(owner) instanceof org.bukkit.projectiles.ProjectileSource source ? source : null;
            }
            case "getVelocity" -> { var v=raw.getDeltaMovement(); yield new org.bukkit.util.Vector(v.x,v.y,v.z); }
            case "setVelocity" -> { var v=(org.bukkit.util.Vector)a[0];v.checkFinite();raw.setDeltaMovement(v.getX(),v.getY(),v.getZ());raw.hurtMarked=true;yield null; }
            case "hasMetadata", "getMetadata", "setMetadata", "removeMetadata" -> metadata(raw,m,a);
            case "getEntityId" -> raw.getId();
            case "getUniqueId" -> raw.getUUID();
            case "getName" -> raw.getName().getString();
            case "getType" -> kind;
            case "getServer" -> host.server;
            case "getWorld" -> world((ServerLevel)raw.level());
            case "getLocation" ->
                new Location(world((ServerLevel)raw.level()), raw.getX(), raw.getY(), raw.getZ(),
                             raw.getYRot(), raw.getXRot());
            case "isDead" -> !raw.isAlive();
            case "isValid" -> !raw.isRemoved();
            case "getHealth" -> (double)((net.minecraft.world.entity.LivingEntity)raw).getHealth();
            default -> throw Views.unsupported("Entity", m);
        });
    }
    private final Map<net.minecraft.server.network.ServerGamePacketListenerImpl,java.lang.ref.WeakReference<Player>> playerViews=new WeakHashMap<>();
    synchronized Player player(ServerPlayer p) {
        // Respawn replaces the ServerPlayer on the existing play connection. A later login has its own connection.
        var connection = p.connection;
        if(connection!=null){
            var ref=playerViews.get(connection);
            var cached=ref==null ? null : ref.get();
            if(cached!=null)return cached;
        }
        java.util.function.Supplier<ServerPlayer> handle=connection==null ? ()->p : connection::getPlayer;
        var view=Views.of(Player.class, p.getGameProfile().name(), p.getUUID(), (method, args) -> {
            var current=handle.get();
            return senderCall(current.createCommandSourceStack(), current, method, args);
        });
        if(connection!=null)playerViews.put(connection,new java.lang.ref.WeakReference<>(view));
        return view;
    }
    private Object senderCall(CommandSourceStack src, ServerPlayer p, String m, Object[] a) {
        boolean op = src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return switch (m) {
            case "getName", "getDisplayName" -> p == null ? "CONSOLE" : p.getGameProfile().name();
            case "getServer" -> host.server;
            case "isOp" -> op;
            case "getLocale" -> Objects.requireNonNull(p).clientInformation().language();
            case "locale" -> Locale.forLanguageTag(Objects.requireNonNull(p).clientInformation().language().replace('_', '-'));
            case "hasPermission", "isPermissionSet" -> {
                var perm = a[0] instanceof Permission v
                               ? v
                               : permissions.get(a[0].toString().toLowerCase(Locale.ROOT));
                yield m.equals("isPermissionSet") ? perm != null
                : perm == null                    ? op
                                                  : perm.getDefault().getValue(op);
            }
            case "sendMessage", "sendPlainMessage", "sendRichMessage" -> {
                Object message = a[a.length - 1];
                if (message instanceof String[] lines)
                    for (var s : lines)
                        send(src, p, s);
                else if (message instanceof net.kyori.adventure.text.Component c)
                    send(src, p,
                         net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                             .serialize(c));
                else
                    send(src, p, message.toString());
                yield null;
            }
            case "getInventory" -> new MenuViews(host,Objects.requireNonNull(p)).bottom;
            case "getOpenInventory" -> new MenuViews(host,Objects.requireNonNull(p)).view;
            case "getType" -> org.bukkit.entity.EntityType.PLAYER;
            case "isOnline" -> p!=null && host.game.getPlayerList().getPlayer(p.getUUID())==p;
            case "getAddress" -> p.connection.getRemoteAddress() instanceof java.net.InetSocketAddress address ? address : null;
            case "isDead" -> p.isDeadOrDying();
            case "isValid" -> !p.isRemoved();
            case "isSneaking" -> p.isShiftKeyDown();
            case "getEntityId" -> p.getId();
            case "hasMetadata", "getMetadata", "setMetadata", "removeMetadata" -> metadata(p,m,a);
            case "isPlayerTimeRelative" -> true;
            case "getPlayerTimeOffset" -> 0L;
            case "getPlayerWeather" -> null;
            case "getAllowFlight" -> p.getAbilities().mayfly;
            case "getFoodLevel" -> p.getFoodData().getFoodLevel();
            case "getSaturation" -> p.getFoodData().getSaturationLevel();
            case "getMaxHealth" -> (double)p.getMaxHealth();
            case "playEffect" -> {
                Location at=(Location)a[0];
                if(at.getWorld()!=null && at.getWorld().equals(world(p.level()))) {
                    var effect=(Effect)a[1];
                    int data=effectData(effect,a[2]);
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(effect.getId(),
                        new net.minecraft.core.BlockPos(at.getBlockX(),at.getBlockY(),at.getBlockZ()),data,false));
                }
                yield null;
            }
            case "getPing" -> Objects.requireNonNull(p).connection.latency();
            case "getLevel" -> Objects.requireNonNull(p).experienceLevel;
            case "getExp" -> Objects.requireNonNull(p).experienceProgress;
            case "getGameMode" ->
                GameMode.valueOf(Objects.requireNonNull(p).gameMode.getGameModeForPlayer().name());
            case "getHealth" -> (double)Objects.requireNonNull(p).getHealth();
            case "isFlying" -> Objects.requireNonNull(p).getAbilities().flying;
            case "getUniqueId" -> Objects.requireNonNull(p).getUUID();
            case "getWorld" -> world((ServerLevel)Objects.requireNonNull(p).level());
            case "getLocation/into" -> {
                var where = (Location)a[0];
                if (where != null) {
                    where.setWorld(world((ServerLevel)p.level()));
                    where.setX(p.getX());
                    where.setY(p.getY());
                    where.setZ(p.getZ());
                    where.setYaw(p.getYRot());
                    where.setPitch(p.getXRot());
                }
                yield where;
            }
            case "getLocation" ->
                new Location(world((ServerLevel)Objects.requireNonNull(p).level()), p.getX(), p.getY(),
                             p.getZ(), p.getYRot(), p.getXRot());
            default -> throw Views.unsupported("CommandSender", m);
        };
    }
    private static int effectData(Effect effect,Object value) {
        if(value==null)return 0;
        if(value instanceof Integer i)return i;
        if(effect==Effect.SMOKE && value instanceof org.bukkit.block.BlockFace face && face.isCartesian())
            return net.minecraft.core.Direction.valueOf(face.name()).get3DDataValue();
        throw Views.unsupported("Effect",effect.name()+" with "+value.getClass().getSimpleName());
    }
    private void send(CommandSourceStack src, ServerPlayer p, String value) {
        var text = net.minecraft.network.chat.Component.literal(value);
        if (p != null)
            p.sendSystemMessage(text);
        else
            src.sendSystemMessage(text);
    }
    World world(ServerLevel level) {
        return worlds.computeIfAbsent(level, l -> {
            String id = l.dimension().identifier().toString();
            String base = host.game.getWorldData().getLevelName();
            String name = switch (id) {
                case "minecraft:overworld" -> base;
                case "minecraft:the_nether" -> base + "_nether";
                case "minecraft:the_end" -> base + "_the_end";
                default -> id;
            };
            return Views.of(NativeWorldView.class, name, (m, a) -> switch (m) {
                case "unityLevel" -> l;
                case "getName" -> name;
                case "getUID" ->
                    UUID.nameUUIDFromBytes((base + "/" + l.getSeed() + "/" + id)
                                               .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                case "getKey" -> NamespacedKey.fromString(id);
                case "getEnvironment" -> l.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) ? World.Environment.NORMAL :
                    l.dimension().equals(net.minecraft.world.level.Level.NETHER) ? World.Environment.NETHER :
                    l.dimension().equals(net.minecraft.world.level.Level.END) ? World.Environment.THE_END : World.Environment.CUSTOM;
                case "playEffect" -> {
                    var at=(Location)a[0];var effect=(Effect)a[1];
                    var pos=new net.minecraft.core.BlockPos(at.getBlockX(),at.getBlockY(),at.getBlockZ());
                    int range=a.length>3 ? (int)a[3] : 64;
                    var packet=new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(effect.getId(),pos,effectData(effect,a[2]),false);
                    for(var p:l.players())if(p.distanceToSqr(at.getX(),at.getY(),at.getZ())<=(double)range*range)p.connection.send(packet);
                    yield null;
                }
                case "getMinHeight" -> l.getMinY();
                case "getMaxHeight" -> l.getMaxY()+1;
                case "getSeed" -> l.getSeed();
                case "getBlockAt" -> {
                    if (a[0] instanceof Location at)
                        yield blocks.block(l, new net.minecraft.core.BlockPos(at.getBlockX(), at.getBlockY(),
                                                                              at.getBlockZ()));
                    yield blocks.block(l, new net.minecraft.core.BlockPos((int)a[0], (int)a[1], (int)a[2]));
                }
                case "getLoadedChunks" -> {
                    var visible=(it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<?>)VISIBLE_CHUNKS.getVolatile(l.getChunkSource().chunkMap);
                    var loaded=new ArrayList<Chunk>();
                    for(long packed:visible.keySet()){
                        var pos=net.minecraft.world.level.ChunkPos.unpack(packed);
                        if(l.getChunkSource().getChunkNow(pos.x(),pos.z())!=null)loaded.add(chunk(l,pos.x(),pos.z()));
                    }
                    yield loaded.toArray(Chunk[]::new);
                }
                case "getEntities" -> {
                    var results=new ArrayList<org.bukkit.entity.Entity>();
                    l.getAllEntities().forEach(raw->results.add(entity(raw)));
                    yield results;
                }
                case "getEntitiesByClass" -> {
                    var matches=new ArrayList<org.bukkit.entity.Entity>();
                    var types=a[0] instanceof Class<?> single ? List.of(single) : Arrays.asList((Class<?>[])a[0]);
                    for(var raw:l.getAllEntities()) {
                        var value=entity(raw);
                        if(types.stream().anyMatch(type->type.isInstance(value)))matches.add(value);
                    }
                    yield matches;
                }
                case "getWorldFolder" ->
                    host.directory.getParent()
                        .resolve(base)
                        .resolve("dimensions")
                        .resolve(id.replace(':', '/'))
                        .toFile();
                case "loadChunk" -> {
                    if (a.length == 3 && Boolean.FALSE.equals(a[2]))
                        yield loadExisting(l, (int)a[0], (int)a[1]);
                    l.getChunk((int)a[0], (int)a[1]);
                    yield a.length == 2 ? null : true;
                }
                case "getChunkAt" -> {
                    int x = (int)a[0], z = (int)a[1];
                    if (a.length == 2 || (boolean)a[2])
                        l.getChunk(x, z);
                    yield chunk(l, x, z);
                }
                case "getChunkAt/block" -> {
                    var block = (org.bukkit.block.Block)a[0];
                    int x = block.getX() >> 4, z = block.getZ() >> 4;
                    l.getChunk(x, z);
                    yield chunk(l, x, z);
                }
                case "loadChunk/chunk" -> {
                    var c = (Chunk)a[0];
                    l.getChunk(c.getX(), c.getZ());
                    yield null;
                }
                case "isChunkLoaded/chunk" -> {
                    var c = (Chunk)a[0];
                    yield l.hasChunk(c.getX(), c.getZ());
                }
                case "isChunkLoaded" -> l.hasChunk((int)a[0], (int)a[1]);
                case "setChunkForceLoaded" -> {
                    l.setChunkForced((int)a[0], (int)a[1], (boolean)a[2]);
                    yield null;
                }
                case "isChunkForceLoaded" ->
                    l.getForceLoadedChunks().contains(
                        net.minecraft.world.level.ChunkPos.pack((int)a[0], (int)a[1]));
                default -> throw Views.unsupported("World", m);
            });
        });
    }
    private boolean loadExisting(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        if (source.getChunkNow(x, z) != null)
            return true;
        var saved = source.chunkMap.read(new net.minecraft.world.level.ChunkPos(x, z));
        level.getServer().managedBlock(saved::isDone);
        if (saved.join().map(net.minecraft.world.level.chunk.storage.SerializableChunkData::getChunkStatusFromTag)
                .orElse(null) != net.minecraft.world.level.chunk.status.ChunkStatus.FULL)
            return false;
        return level.getChunk(x, z) != null;
    }
    Chunk chunk(ServerLevel level, int x, int z) {
        return Views.of(Chunk.class, "Chunk " + x + "," + z, (m, a) -> switch (m) {
            case "getX" -> x;
            case "getZ" -> z;
            case "getWorld" -> world(level);
            case "isLoaded" -> level.hasChunk(x, z);
            default -> throw Views.unsupported("Chunk", m);
        });
    }
    void tick() {
        tasks.tick();
        if (++metadataTicks == 20) {
            metadataTicks = 0;
            metadata.entrySet().removeIf(entry -> {
                var entity = entry.getValue().owner.get();
                if (host.game.getPlayerList().getPlayer(entry.getKey()) != null) return false;
                return entity == null || entity.isRemoved();
            });
        }
    }
}
