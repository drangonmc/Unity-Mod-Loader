package net.neoforged.neoforge.common.data;
import com.google.gson.*;
import com.mojang.serialization.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.*;
import net.minecraft.data.*;
import net.minecraft.resources.*;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
/** Writes simple data maps using each registered value codec and Minecraft's stable pack writer. */
public abstract class DataMapProvider implements DataProvider {
    protected final CompletableFuture<HolderLookup.Provider> lookupProvider;
    private final PackOutput output;
    private final Map<DataMapType<?, ?>, Builder<?, ?>> maps = new LinkedHashMap<>();
    protected DataMapProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        this.output = output;
        lookupProvider = lookup;
    }
    protected abstract void gather(HolderLookup.Provider lookup);
    @SuppressWarnings("unchecked")
    public <T, R> Builder<T, R> builder(DataMapType<R, T> type) {
        return (Builder<T, R>)maps.computeIfAbsent(type, t -> new Builder<>(type));
    }
    public CompletableFuture<?> run(CachedOutput cache) {
        return lookupProvider.thenCompose(lookup -> {
            maps.clear();
            gather(lookup);
            var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
            return CompletableFuture.allOf(maps.values()
                                               .stream()
                                               .map(b -> b.write(cache, output, ops))
                                               .toArray(CompletableFuture[] ::new));
        });
    }
    public String getName() { return "Data Maps"; }
    public static class Builder<T, R> {
        private final DataMapType<R, T> type;
        private record Entry<T>(T value, boolean replace) {}
        private final Map<String, Entry<T>> entries = new LinkedHashMap<>();
        private Builder(DataMapType<R, T> type) { this.type = type; }
        public Builder<T, R> add(TagKey<R> tag, T value, boolean replace, ICondition... conditions) {
            return put("#" + tag.location(), value, replace, conditions);
        }
        public Builder<T, R> add(Holder<R> holder, T value, boolean replace, ICondition... conditions) {
            return put(holder.unwrapKey().orElseThrow().identifier().toString(), value, replace, conditions);
        }
        private Builder<T, R> put(String key, T value, boolean replace, ICondition[] conditions) {
            if (conditions.length != 0)
                throw new UnsupportedOperationException("Conditional data map generation");
            entries.put(key, new Entry<>(value, replace));
            return this;
        }
        private CompletableFuture<?> write(CachedOutput cache, PackOutput out, DynamicOps<JsonElement> ops) {
            var values = new JsonObject();
            entries.forEach((key, entry) -> {
                JsonElement value = type.codec().encodeStart(ops, entry.value()).getOrThrow();
                if (entry.replace()) {
                    var replace = new JsonObject();
                    replace.add("value", value);
                    replace.addProperty("replace", true);
                    value = replace;
                }
                values.add(key, value);
            });
            var data = new JsonObject();
            data.add("values", values);
            var registry = type.registryKey().identifier();
            String sub = (registry.getNamespace().equals("minecraft") ? "" : registry.getNamespace() + "/") +
                         registry.getPath();
            return DataProvider.saveStable(
                cache, data,
                out.createPathProvider(PackOutput.Target.DATA_PACK, "data_maps/" + sub).json(type.id()));
        }
    }
}
