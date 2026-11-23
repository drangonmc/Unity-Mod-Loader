package mc.unity.adapter.common;

import com.google.gson.*;
import com.mojang.serialization.*;
import java.io.*;
import java.util.*;
import mc.unity.core.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public final class LootModifiers {
    public static final Registry<MapCodec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>>
        FORGE = ExtraRegistries.create(
            ResourceKey.createRegistryKey(Identifier.parse("forge:global_loot_modifier_serializers")));
    public static final Registry<MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>>
        NEO = ExtraRegistries.create(
            ResourceKey.createRegistryKey(Identifier.parse("neoforge:global_loot_modifier_serializers")));
    private record Rule(int priority, Drops.Modifier modifier) {}
    private record TableId(Identifier id, MapCodec<TableId> type) implements LootItemCondition {
        public boolean test(LootContext c) { return id.equals(Drops.queriedTable()); }
        public MapCodec<TableId> codec() { return type; }
    }
    private static final class TableCondition extends MapCodec<TableId> {
        private final MapCodec<Identifier> id = Identifier.CODEC.fieldOf("loot_table_id");
        public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops) { return id.keys(ops); }
        public <T> DataResult<TableId> decode(DynamicOps<T> ops, MapLike<T> map) {
            return id.decode(ops, map).map(k -> new TableId(k, this));
        }
        public <T> RecordBuilder<T> encode(TableId value, DynamicOps<T> ops, RecordBuilder<T> builder) {
            return id.encode(value.id, ops, builder);
        }
    }
    public static void install() {
        for (String platform : List.of("forge", "neoforge"))
            Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
                              Identifier.fromNamespaceAndPath(platform, "loot_table_id"),
                              new TableCondition());
        Registry.register(NEO, Identifier.parse("neoforge:add_table"),
                          net.neoforged.neoforge.common.loot.AddTableLootModifier.CODEC);
        Reloads.COLLECT.listen(
            reload
            -> reload.listeners().add((ResourceManagerReloadListener)manager -> load(reload, manager)));
    }
    private static void load(Reloads reload, ResourceManager manager) {
        var ops = reload.resources().fullRegistries().lookup().createSerializationContext(JsonOps.INSTANCE);
        var all = new ArrayList<Drops.Modifier>();
        for (String platform : List.of("forge", "neoforge")) {
            var ordered = new LinkedHashSet<Identifier>();
            var index =
                Identifier.fromNamespaceAndPath(platform, "loot_modifiers/global_loot_modifiers.json");
            for (var r : manager.getResourceStack(index))
                try (var in = r.openAsReader()) {
                    var json = JsonParser.parseReader(in).getAsJsonObject();
                    if (json.get("replace").getAsBoolean())
                        ordered.clear();
                    for (var e : json.getAsJsonArray("entries")) {
                        var id = Identifier.parse(e.getAsString());
                        ordered.remove(id);
                        ordered.add(id);
                    }
                } catch (RuntimeException | IOException e) {
                    throw new IllegalStateException("Invalid loot list " + index + " in " + r.sourcePackId(),
                                                    e);
                }
            var rules = new ArrayList<Rule>();
            for (var id : ordered) {
                var path = id.withPath("loot_modifiers/" + id.getPath() + ".json");
                try (var in = manager.getResourceOrThrow(path).openAsReader()) {
                    var json = JsonParser.parseReader(in).getAsJsonObject();
                    if (json.has("neoforge:conditions") || json.has("forge:conditions"))
                        throw new IllegalArgumentException("Unsupported conditional resource " + id);
                    if (platform.equals("forge")) {
                        var rule =
                            net.minecraftforge.common.loot.IGlobalLootModifier.DIRECT_CODEC.parse(ops, json)
                                .getOrThrow();
                        rules.add(new Rule(0, rule::apply));
                    } else {
                        var rule = net.neoforged.neoforge.common.loot.IGlobalLootModifier.DIRECT_CODEC
                                       .parse(ops, json)
                                       .getOrThrow();
                        rules.add(new Rule(rule.priority(), (t, items, c) -> rule.apply(items, c)));
                    }
                } catch (IOException | RuntimeException e) {
                    throw new IllegalArgumentException(
                        "Invalid " + platform + " global loot modifier " + path, e);
                }
            }
            rules.sort(Comparator.comparingInt(Rule::priority).reversed());
            rules.forEach(r -> all.add(r.modifier));
        }
        var holder = reload.resources().fullRegistries();
        var names = new IdentityHashMap<LootTable, Identifier>();
        if (!all.isEmpty())
            holder.lookup()
                .lookupOrThrow(Registries.LOOT_TABLE)
                .listElements()
                .forEach(h -> names.put(h.value(), h.key().identifier()));
        Drops.publish(holder, new Drops.Rules(List.copyOf(all), Collections.unmodifiableMap(names)));
    }
}
