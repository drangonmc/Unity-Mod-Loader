package net.minecraftforge.common;

import com.electronwill.nightconfig.core.*;
import com.electronwill.nightconfig.core.utils.UnmodifiableConfigWrapper;
import java.util.*;
import java.util.function.*;
import net.minecraftforge.fml.config.IConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Typed Forge settings backed by the active local or server-supplied TOML document. */
public final class ForgeConfigSpec
    extends UnmodifiableConfigWrapper<UnmodifiableConfig> implements IConfigSpec<ForgeConfigSpec> {
    private final List<ConfigValue<?>> values;
    private volatile CommentedConfig loaded;
    private volatile Runnable save;
    private boolean correcting;
    ForgeConfigSpec(Builder builder) {
        super(builder.schema.unmodifiable());
        values = List.copyOf(builder.values);
        values.forEach(v -> v.spec = this);
    }
    public boolean isLoaded() { return loaded != null; }
    public boolean isCorrecting() { return correcting; }
    public boolean isCorrect(CommentedConfig doc) {
        return values.stream().allMatch(v -> v.valid(doc.get(v.path)));
    }
    public synchronized int correct(CommentedConfig doc) {
        int count = 0;
        correcting = true;
        try {
            for (var v : values) {
                if (!v.valid(doc.get(v.path))) {
                    doc.set(v.path, v.initial);
                    count++;
                }
                if (v.comment != null && doc.getComment(v.path) == null)
                    doc.setComment(v.path, v.comment);
            }
        } finally {
            correcting = false;
        }
        return count;
    }
    public void acceptConfig(CommentedConfig config) { loaded = config; }
    public void afterReload() {}
    public void unitySave(Runnable writer) { save = writer; }
    public void save() { Objects.requireNonNull(save, "No active config").run(); }
    public static class ConfigValue<T> implements Supplier<T> {
        private final T initial;
        private final Function<Object, T> decode;
        private List<String> path;
        private String comment;
        private ForgeConfigSpec spec;
        ConfigValue(T initial, Function<Object, T> decode) {
            this.initial = Objects.requireNonNull(initial);
            this.decode = decode;
        }
        boolean valid(Object raw) {
            try {
                return raw != null && decode.apply(raw) != null;
            } catch (RuntimeException ex) {
                return false;
            }
        }
        public List<String> getPath() { return path; }
        public T getDefault() { return initial; }
        public T get() {
            var doc = spec == null ? null : spec.loaded;
            return doc == null ? initial : decode.apply(doc.get(path));
        }
        public void set(T value) {
            if (!valid(value))
                throw new IllegalArgumentException("Invalid config value for " + path);
            Objects.requireNonNull(spec.loaded, "No loaded config").set(path, value);
        }
        public void save() { spec.save(); }
    }
    public static class IntValue extends ConfigValue<Integer> {
        IntValue(int v, int lo, int hi) {
            super(v, r -> {
                if (!(r instanceof Integer || r instanceof Long))
                    return null;
                long n = ((Number)r).longValue();
                return n >= lo && n <= hi ? (int)n : null;
            });
        }
    }
    public static class LongValue extends ConfigValue<Long> {
        LongValue(long v, long lo, long hi) {
            super(v, r -> {
                if (!(r instanceof Integer || r instanceof Long))
                    return null;
                long n = ((Number)r).longValue();
                return n >= lo && n <= hi ? n : null;
            });
        }
    }
    public static class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(boolean v) { super(v, Boolean.class ::cast); }
    }
    public static class DoubleValue extends ConfigValue<Double> {
        DoubleValue(double v, double lo, double hi) {
            super(v, r -> {
                double n = ((Number)r).doubleValue();
                return n >= lo && n <= hi ? n : null;
            });
        }
    }
    public static class Builder {
        private final Config schema = Config.inMemory();
        private final List<ConfigValue<?>> values = new ArrayList<>();
        private final List<String> sections = new ArrayList<>();
        private String comment;
        private boolean built;
        public Builder comment(String value) {
            comment = value;
            return this;
        }
        public Builder comment(String... values) { return comment(String.join("\n", values)); }
        public Builder push(String path) {
            sections.addAll(parts(path));
            return this;
        }
        public Builder pop() { return pop(1); }
        public Builder pop(int n) {
            sections.subList(sections.size() - n, sections.size()).clear();
            return this;
        }
        private static List<String> parts(String value) {
            var list = List.of(value.split("\\.", -1));
            if (list.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException(value);
            return list;
        }
        private <V extends ConfigValue<?>> V add(String key, V value) {
            var path = new ArrayList<>(sections);
            path.addAll(parts(key));
            ConfigValue<?> v = value;
            if (built || schema.contains(path) || !v.valid(v.initial))
                throw new IllegalArgumentException("Invalid or duplicate config setting " + path);
            v.path = List.copyOf(path);
            v.comment = comment;
            comment = null;
            schema.set(path, v);
            values.add(v);
            return value;
        }
        public BooleanValue define(String key, boolean v) { return add(key, new BooleanValue(v)); }
        public IntValue defineInRange(String key, int v, int lo, int hi) {
            return add(key, new IntValue(v, lo, hi));
        }
        public LongValue defineInRange(String key, long v, long lo, long hi) {
            return add(key, new LongValue(v, lo, hi));
        }
        public DoubleValue defineInRange(String key, double v, double lo, double hi) {
            return add(key, new DoubleValue(v, lo, hi));
        }
        public <T> ConfigValue<T> define(String key, T v) { return define(key, v, v.getClass()::isInstance); }
        @SuppressWarnings("unchecked")
        public <T> ConfigValue<T> define(String key, T v, Predicate<Object> valid) {
            return add(key, new ConfigValue<>(v, r -> valid.test(r) ? (T)r : null));
        }
        public <T> Pair<T, ForgeConfigSpec> configure(Function<Builder, T> visitor) {
            return Pair.of(visitor.apply(this), build());
        }
        public ForgeConfigSpec build() {
            if (built)
                throw new IllegalStateException("Spec already built");
            built = true;
            return new ForgeConfigSpec(this);
        }
    }
}
