package mc.unity.adapter.neoforge;

import com.google.gson.*;
import com.mojang.serialization.*;
import java.io.*;
import java.util.*;
import mc.unity.core.*;
import net.minecraft.core.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.registries.datamaps.*;

public final class DataMaps {
    private record Id(Identifier registry, Identifier map) {}
    private static final Map<Id, DataMapType<?, ?>> types = new LinkedHashMap<>();
    private static volatile Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> serverValues = Map.of(), clientValues = Map.of();
    private static volatile MinecraftServer currentServer;
    private static ResourceManager resources;
    private static Packet snapshot;
    private record Packet(String json) implements CustomPacketPayload {
        static final Type<Packet> ID = new Type<>(Identifier.parse("unity:registry_data_maps"));
        public Type<Packet> type() { return ID; }
    }
    public static void register(DataMapType<?, ?> type) {
        Id id = new Id(type.registryKey().identifier(), type.id());
        if (types.putIfAbsent(id, type) != null)
            throw new IllegalArgumentException("Duplicate data map " + id);
    }
    public static void start() {
        if (types.isEmpty())
            return;
        Events.SERVER_STARTED.listen(server -> {currentServer=server; reload(server);});
        Events.SERVER_STOPPED.listen(server -> {if (currentServer==server) clearWorld();});
        Events.SERVER_TICK_PRE.listen((server, timer) -> {
            if (resources != server.getResourceManager())
                reload(server);
        });
        if (types.values().stream().noneMatch(t -> t.networkCodec() != null))
            return;
        String version = types.values()
                             .stream()
                             .filter(DataMapType::mandatorySync)
                             .map(t -> t.registryKey().identifier() + "/" + t.id())
                             .sorted()
                             .toList()
                             .toString();
        var codec = new StreamCodec<RegistryFriendlyByteBuf, Packet>() {
            public void encode(RegistryFriendlyByteBuf buf, Packet p) { buf.writeUtf(p.json, 262144); }
            public Packet decode(RegistryFriendlyByteBuf buf) { return new Packet(buf.readUtf(262144)); }
        };
        PlayNetwork.register(Packet.ID.id(), version, PacketFlow.CLIENTBOUND, Packet.ID, codec,
                             (p, context) -> receive(p, PlayNetwork.localPlayer().registryAccess()), true);
        Events.PLAYER_LOGIN.listen(p -> {
            if (snapshot != null)
                p.connection.send(new ClientboundCustomPayloadPacket(snapshot));
        });
    }
    private static void reload(MinecraftServer server) {
        var manager = server.getResourceManager();
        Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> result = new HashMap<>();
        JsonArray wire = new JsonArray();
        for (var type : types.values())
            load(type, server.registryAccess(), manager, result, wire);
        serverValues = Map.copyOf(result);
        resources = manager;
        snapshot = new Packet(wire.toString());
        if (!wire.isEmpty())
            for (var p : server.getPlayerList().getPlayers())
                p.connection.send(new ClientboundCustomPayloadPacket(snapshot));
    }
    private static <R, T> void load(DataMapType<R, T> type, RegistryAccess access, ResourceManager manager,
                                    Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> result,
                                    JsonArray wire) {
        var registry = access.lookupOrThrow(type.registryKey());
        var data = new LinkedHashMap<ResourceKey<?>, Object>();
        var registryId = type.registryKey().identifier();
        String sub = registryId.getNamespace().equals("minecraft")
                         ? registryId.getPath()
                         : registryId.getNamespace() + "/" + registryId.getPath();
        var file = type.id().withPath("data_maps/" + sub + "/" + type.id().getPath() + ".json");
        var ops = access.createSerializationContext(JsonOps.INSTANCE);
        for (var resource : manager.getResourceStack(file))
            try (var reader = resource.openAsReader()) {
                var doc = JsonParser.parseReader(reader).getAsJsonObject();
                if (doc.has("neoforge:conditions") || doc.has("conditions"))
                    throw new IOException("Unsupported conditional data map " + file);
                if (doc.has("replace") && doc.get("replace").getAsBoolean())
                    data.clear();
                for (var e : doc.getAsJsonObject("values").entrySet()) {
                    var v = e.getValue();
                    if (v.isJsonObject() && v.getAsJsonObject().has("value"))
                        v = v.getAsJsonObject().get("value");
                    T parsed = type.codec().parse(ops, v).getOrThrow();
                    for (var key : keys(registry, e.getKey()))
                        data.put(key, parsed);
                }
                if (doc.has("remove"))
                    for (var e : doc.getAsJsonArray("remove"))
                        for (var key : keys(registry, e.getAsString()))
                            data.remove(key);
            } catch (IOException | RuntimeException e) {
                throw new IllegalArgumentException("Data map " + file, e);
            }
        result.put(type, Map.copyOf(data));
        if (type.networkCodec() != null) {
            var row = new JsonObject();
            row.addProperty("registry", registryId.toString());
            row.addProperty("id", type.id().toString());
            var members = new JsonObject();
            for (var e : data.entrySet()) {
                @SuppressWarnings("unchecked") T value = (T)e.getValue();
                members.add(e.getKey().identifier().toString(),
                            type.networkCodec().encodeStart(ops, value).getOrThrow());
            }
            row.add("values", members);
            wire.add(row);
        }
    }
    private static <R> List<ResourceKey<R>> keys(Registry<R> registry, String name) {
        if (name.startsWith("#"))
            return registry.get(TagKey.create(registry.key(), Identifier.parse(name.substring(1))))
                .stream()
                .flatMap(HolderSet::stream)
                .flatMap(h -> h.unwrapKey().stream())
                .toList();
        var key = ResourceKey.create(registry.key(), Identifier.parse(name));
        return registry.get(key).isPresent() ? List.of(key) : List.of();
    }
    private static void receive(Packet packet, RegistryAccess access) {
        Map<DataMapType<?, ?>, Map<ResourceKey<?>, Object>> result = new HashMap<>();
        var ops = access.createSerializationContext(JsonOps.INSTANCE);
        for (var el : JsonParser.parseString(packet.json).getAsJsonArray()) {
            var row = el.getAsJsonObject();
            var id = new Id(Identifier.parse(row.get("registry").getAsString()),
                            Identifier.parse(row.get("id").getAsString()));
            var type = types.get(id);
            if (type == null)
                continue;
            if (type.networkCodec() == null || result.containsKey(type))
                throw new IllegalArgumentException("Invalid synced data map " + id);
            Map<ResourceKey<?>, Object> entries = new HashMap<>();
            for (var e : row.getAsJsonObject("values").entrySet())
                entries.put(ResourceKey.create(type.registryKey(), Identifier.parse(e.getKey())),
                            type.networkCodec().parse(ops, e.getValue()).getOrThrow());
            result.put(type, Map.copyOf(entries));
        }
        if (types.values().stream().anyMatch(t -> t.mandatorySync() && !result.containsKey(t)))
            throw new IllegalArgumentException("Missing mandatory registry data map");
        clientValues = Map.copyOf(result);
    }
    public static Object holder(Holder<?> holder, DataMapType<?, ?> type) {
        return holder.unwrapKey().map(k -> lookup(type, k)).orElse(null);
    }
    public static Object lookup(DataMapType<?, ?> type, ResourceKey<?> key) { return all(type).get(key); }
    public static Map<ResourceKey<?>, Object> all(DataMapType<?, ?> type) {
        var server=currentServer;
        var values=server!=null && (server.isDedicatedServer() || server.isSameThread()) ? serverValues : clientValues;
        return values.getOrDefault(type, Map.of());
    }
    public static void close() {
        types.clear();
        clearWorld();clearClient();
    }
    public static void clearClient(){clientValues=Map.of();}
    private static void clearWorld() {
        currentServer=null;
        serverValues = Map.of();
        resources = null;
        snapshot = null;
    }
}
