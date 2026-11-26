package mc.unity.core;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

/** Keys live in the mod session. Values live in the holder and travel with its vanilla save record. */
public final class Attachments {
    public interface Owner {
        Attachments unity$attachments(boolean create);
    }
    public interface Key<T> {
        Identifier id();
        Codec<T> codec();
        default boolean copyOnDeath() { return false; }
        default T copy(T value, Owner target, net.minecraft.core.HolderLookup.Provider lookup) {
            if (codec() == null)
                return value;
            var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
            return codec().parse(ops, codec().encodeStart(ops, value).getOrThrow()).getOrThrow();
        }
    }
    @SuppressWarnings("unchecked")
    private <T> void copyKey(Key<T> key, Owner target, net.minecraft.core.HolderLookup.Provider lookup) {
        T copy = key.copy((T)values.get(key), target, lookup);
        if (copy != null)
            set(target, key, copy);
    }
    public static void respawn(net.minecraft.server.level.ServerPlayer next,
                               net.minecraft.server.level.ServerPlayer old, boolean alive) {
        var data = ((Owner)old).unity$attachments(false);
        if (data == null)
            return;
        var target = (Owner)next;
        for (var key : data.values.keySet())
            if (alive || key.copyOnDeath())
                data.copyKey(key, target, next.registryAccess());
        if (alive && !data.unknown.isEmpty())
            target.unity$attachments(true).unknown = data.unknown.copy();
    }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String FIELD = "unity:attachments";
    private static final Map<Identifier, Key<?>> keys = new LinkedHashMap<>();
    private final Map<Key<?>, Object> values = new IdentityHashMap<>();
    private CompoundTag lastGood = new CompoundTag();
    private CompoundTag unknown = new CompoundTag();
    public static synchronized void register(Key<?> key) {
        if (keys.putIfAbsent(key.id(), key) != null)
            throw new IllegalArgumentException("Duplicate attachment " + key.id());
    }
    static synchronized void clear() { keys.clear(); }
    public boolean any() { return !values.isEmpty() || !unknown.isEmpty(); }
    @SuppressWarnings("unchecked")
    public static <T> T find(Owner owner, Key<T> key) {
        var data = owner.unity$attachments(false);
        return data == null ? null : (T)data.values.get(key);
    }
    @SuppressWarnings("unchecked")
    public static <T> T set(Owner owner, Key<T> key, T value) {
        var data = owner.unity$attachments(value != null);
        if (data == null)
            return null;
        String name = key.id().toString();
        if (value == null) data.lastGood.remove(name);
        else if (data.unknown.contains(name)) data.lastGood.put(name, data.unknown.get(name).copy());
        data.unknown.remove(name);
        T old = (T)(value == null ? data.values.remove(key) : data.values.put(key, value));
        if (owner instanceof BlockEntity tile)
            tile.setChanged();
        if (owner instanceof net.minecraft.world.level.chunk.ChunkAccess chunk)
            chunk.markUnsaved();
        return old;
    }
    /** A default created by a read must not overwrite a quarantined value on the next save. */
    public static <T> void initialize(Owner owner, Key<T> key, T value) {
        var state = owner.unity$attachments(true);
        if (state.unknown.contains(key.id().toString()))
            state.values.put(key, value);
        else
            set(owner, key, value);
    }
    private <O, T> void encodeEntry(RecordBuilder<O> result, DynamicOps<O> ops, Key<T> key) {
        String name = key.id().toString();
        if (unknown.contains(name)) return;
        try {
            var codec = key.codec();
            if (codec == null) return;
            @SuppressWarnings("unchecked") T value = (T)values.get(key);
            O encoded = codec.encodeStart(ops, value).getOrThrow();
            var raw = ops.convertTo(NbtOps.INSTANCE, encoded);
            lastGood.put(name, raw.copy());
            result.add(name, encoded);
            return;
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not encode attachment {}; previous serialized value available: {}",
                        name, lastGood.contains(name), failure);
        }
        var saved = lastGood.get(name);
        if (saved != null) result.add(name, NbtOps.INSTANCE.convertTo(ops, saved));
    }
    private static final Codec<Attachments> CODEC = new Codec<>() {
        public <O> DataResult<O> encode(Attachments data, DynamicOps<O> ops, O prefix) {
            var result = ops.mapBuilder();
            for (var e : data.unknown.entrySet())
                result.add(e.getKey(), NbtOps.INSTANCE.convertTo(ops, e.getValue()));
            for (var key : data.values.keySet())
                data.encodeEntry(result, ops, key);
            return result.build(prefix);
        }
        public <O> DataResult<Pair<Attachments, O>> decode(DynamicOps<O> ops, O input) {
            return ops.getMap(input).flatMap(map -> {
                var data = new Attachments();
                for (var entry : map.entries().toList()) {
                    var nameResult = ops.getStringValue(entry.getFirst());
                    if (nameResult.result().isEmpty()) {
                        LOGGER.warn("Ignoring attachment with a non-string key");
                        continue;
                    }
                    String name = nameResult.result().orElseThrow();
                    var id = Identifier.tryParse(name);
                    var key = id == null ? null : keys.get(id);
                    if (key != null && key.codec() != null) {
                        try {
                            var decoded = key.codec().parse(ops, entry.getSecond());
                            if (decoded.result().isPresent()) {
                                data.lastGood.put(name, ops.convertTo(NbtOps.INSTANCE, entry.getSecond()).copy());
                                data.values.put(key, decoded.result().orElseThrow());
                                continue;
                            }
                            LOGGER.warn("Preserving unreadable attachment {}: {}", name,
                                        decoded.error().orElseThrow().message());
                        } catch (RuntimeException invalid) {
                            LOGGER.warn("Preserving unreadable attachment {}: {}", name, invalid.toString());
                        }
                    }
                    try {
                        data.unknown.put(name, ops.convertTo(NbtOps.INSTANCE, entry.getSecond()).copy());
                    } catch (RuntimeException invalid) {
                        LOGGER.warn("Cannot represent attachment {} as NBT: {}", name, invalid.toString());
                    }
                }
                return DataResult.success(Pair.of(data, ops.empty()));
            });
        }
    };
    public interface Serialized {
        CompoundTag unity$raw();
        void unity$raw(CompoundTag value);
    }
    public static void parsed(Serialized record, CompoundTag source) {
        if (record != null)
            source.getCompound(FIELD).ifPresent(tag -> record.unity$raw(tag.copy()));
    }
    public static void written(CompoundTag output, Serialized record) {
        var raw = record.unity$raw();
        if (raw != null)
            output.put(FIELD, raw.copy());
    }
    private static Owner realChunk(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        if (chunk instanceof net.minecraft.world.level.chunk.ImposterProtoChunk wrapper)
            chunk = wrapper.getWrapped();
        return (Owner)chunk;
    }
    public static void pack(Serialized record, net.minecraft.server.level.ServerLevel level,
                            net.minecraft.world.level.chunk.ChunkAccess chunk) {
        var data = realChunk(chunk).unity$attachments(false);
        if (data != null && data.any())
            record.unity$raw(
                (CompoundTag)CODEC
                    .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), data)
                    .getOrThrow())
        ;
    }
    public static void unpack(net.minecraft.world.level.chunk.ProtoChunk proto, Serialized record,
                              net.minecraft.server.level.ServerLevel level) {
        var raw = record.unity$raw();
        if (raw != null) {
            var decoded =
                CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), raw);
            decoded.resultOrPartial(reason -> LOGGER.warn("Cannot load chunk attachments: {}", reason))
                .ifPresent(value -> copy(value, realChunk(proto).unity$attachments(true)));
        }
    }
    public static void promote(Owner target, net.minecraft.world.level.chunk.ProtoChunk proto) {
        var data = realChunk(proto).unity$attachments(false);
        if (data != null)
            copy(data, target.unity$attachments(true));
    }
    private static void copy(Attachments from, Attachments to) {
        to.values.putAll(from.values);
        to.unknown = from.unknown.copy();
        to.lastGood = from.lastGood.copy();
    }
    public static void save(Owner owner, ValueOutput output) {
        var data = owner.unity$attachments(false);
        if (data != null && data.any())
            output.store(FIELD, CODEC, data);
    }
    public static void load(Owner owner, ValueInput input) {
        var saved = input.read(FIELD, CODEC);
        var data = owner.unity$attachments(saved.isPresent());
        if (data == null)
            return;
        data.values.clear();
        data.unknown = new CompoundTag();
        data.lastGood = new CompoundTag();
        saved.ifPresent(decoded -> copy(decoded, data));
    }
}
