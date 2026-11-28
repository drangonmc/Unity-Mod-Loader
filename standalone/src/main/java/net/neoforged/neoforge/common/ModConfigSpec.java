package net.neoforged.neoforge.common;

import com.electronwill.nightconfig.core.*;
import java.util.*;
import java.util.function.*;
import net.neoforged.fml.config.*;

/** Typed TOML specifications and immutable path definitions owned by one mod config. */
public final class ModConfigSpec implements IConfigSpec {
    public enum RestartType { NONE, WORLD, GAME }
    public static class ConfigValue<T> implements Supplier<T> {
        private List<String> path;
        private String comment;
        private RestartType restart;
        private final T initial;
        private final Function<Object, T> decode;
        private ModConfigSpec spec;
        ConfigValue(T initial, Function<Object, T> decode) {
            this.initial = Objects.requireNonNull(initial);
            this.decode = decode;
        }
        public T get() {
            var config = spec.loaded;
            return config == null ? initial : decode.apply(config.config().get(path));
        }
        public List<String> getPath() { return path; }
        boolean accepts(Object value) {
            try {
                return value != null && decode.apply(value) != null;
            } catch (RuntimeException invalid) {
                return false;
            }
        }
    }
    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(boolean v) { super(v, Boolean.class ::cast); }
    }
    public static final class DoubleValue extends ConfigValue<Double> {
        DoubleValue(double v, double min, double max) {
            super(v, raw -> {
                double n = ((Number)raw).doubleValue();
                return n >= min && n <= max ? n : null;
            });
        }
    }
    public static final class LongValue extends ConfigValue<Long> {
        LongValue(long v, long min, long max) {
            super(v, raw -> {
                if (!(raw instanceof Integer || raw instanceof Long))
                    return null;
                long n = ((Number)raw).longValue();
                return n >= min && n <= max ? n : null;
            });
        }
    }
    public static final class IntValue extends ConfigValue<Integer> {
        IntValue(int v, int min, int max) {
            super(v, raw -> {
                if (!(raw instanceof Integer || raw instanceof Long))
                    return null;
                long n = ((Number)raw).longValue();
                return n >= min && n <= max ? (int)n : null;
            });
        }
    }
    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {
        EnumValue(E v, Predicate<Object> valid) {
            super(v, raw -> {
                E value = raw instanceof String s ? Enum.valueOf(v.getDeclaringClass(), s)
                                                  : v.getDeclaringClass().cast(raw);
                return valid.test(value) ? value : null;
            });
        }
    }
    public static final class Builder {
        private final List<String> path = new ArrayList<>();
        private final Map<List<String>, ConfigValue<?>> values = new LinkedHashMap<>();
        private boolean built;
        private String comment;
        private RestartType restart = RestartType.NONE;
        public Builder push(String section) {
            path.addAll(parts(section));
            return this;
        }
        public Builder pop() { return pop(1); }
        public Builder pop(int count) {
            path.subList(path.size() - count, path.size()).clear();
            return this;
        }
        public Builder comment(String text) {
            comment = text;
            return this;
        }
        public Builder comment(String... text) {
            comment = String.join("\n", text);
            return this;
        }
        public Builder worldRestart() {
            restart = RestartType.WORLD;
            return this;
        }
        public Builder gameRestart() {
            restart = RestartType.GAME;
            return this;
        }
        private static List<String> parts(String key) {
            var parts = List.of(key.split("\\.", -1));
            if (parts.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException(key);
            return parts;
        }
        private <V extends ConfigValue<?>> V put(String key, V value) {
            if (built)
                throw new IllegalStateException("Config builder closed");
            ConfigValue<?> entry = value;
            var full = new ArrayList<>(path);
            full.addAll(parts(key));
            entry.path = List.copyOf(full);
            if (!entry.accepts(entry.initial) || values.putIfAbsent(entry.path, entry) != null)
                throw new IllegalArgumentException("Invalid default or duplicate config path " + full);
            entry.comment = comment;
            entry.restart = restart;
            comment = null;
            restart = RestartType.NONE;
            return value;
        }
        public BooleanValue define(String k, boolean v) { return put(k, new BooleanValue(v)); }
        @SuppressWarnings("unchecked")
        public <T> ConfigValue<T> define(String k,T value) {
            Objects.requireNonNull(value);
            return put(k,new ConfigValue<>(value,raw -> {
                if(value instanceof List<?>)return raw instanceof List<?> ? (T)raw : null;
                if(value instanceof Enum<?> e) {
                    if(raw instanceof String name)return (T)Enum.valueOf((Class)e.getDeclaringClass(),name);
                    return e.getDeclaringClass().isInstance(raw)?(T)raw:null;
                }
                if(value instanceof Number && raw instanceof Number n) {
                    if(value instanceof Double)return Double.isFinite(n.doubleValue())?(T)Double.valueOf(n.doubleValue()):null;
                    if(value instanceof Float)return Float.isFinite(n.floatValue())?(T)Float.valueOf(n.floatValue()):null;
                    var exact=new java.math.BigDecimal(n.toString());
                    if(value instanceof Integer)return (T)Integer.valueOf(exact.intValueExact());
                    if(value instanceof Long)return (T)Long.valueOf(exact.longValueExact());
                    if(value instanceof Short)return (T)Short.valueOf(exact.shortValueExact());
                    if(value instanceof Byte)return (T)Byte.valueOf(exact.byteValueExact());
                }
                return value.getClass().isInstance(raw)?(T)raw:null;
            }));
        }
        public <T> ConfigValue<T> define(String k, T v, Predicate<Object> valid) {
            return put(k, new ConfigValue<>(v, raw -> valid.test(raw) ? (T)raw : null));
        }
        public IntValue defineInRange(String k, int v, int lo, int hi) {
            return put(k, new IntValue(v, lo, hi));
        }
        public LongValue defineInRange(String k, long v, long lo, long hi) {
            return put(k, new LongValue(v, lo, hi));
        }
        public DoubleValue defineInRange(String k, double v, double lo, double hi) {
            return put(k, new DoubleValue(v, lo, hi));
        }
        public <E extends Enum<E>> EnumValue<E> defineEnum(String k, E v, Predicate<Object> valid) {
            return put(k, new EnumValue<>(v, valid));
        }
        public <T> ConfigValue<List<? extends T>>
        defineListAllowEmpty(String k, List<? extends T> v, Supplier<T> newElement, Predicate<Object> valid) {
            return define(k, List.copyOf(v),
                          raw -> raw instanceof List<?> list && list.stream().allMatch(valid));
        }
        public ModConfigSpec build() {
            if (built)
                throw new IllegalStateException("Already built");
            built = true;
            return new ModConfigSpec(values);
        }
    }
    private final List<ConfigValue<?>> values;
    private volatile ILoadedConfig loaded;
    private ModConfigSpec(Map<List<String>, ConfigValue<?>> values) {
        this.values = List.copyOf(values.values());
        this.values.forEach(v -> v.spec = this);
    }
    public boolean isEmpty() { return values.isEmpty(); }
    public boolean isLoaded() { return loaded != null; }
    public void validateSpec(ModConfig config) {
        if (config.getSpec() != this)
            throw new IllegalArgumentException("Config spec owner");
    }
    public boolean isCorrect(UnmodifiableCommentedConfig config) {
        return values.stream().allMatch(v -> v.accepts(config.get(v.path)));
    }
    public void correct(CommentedConfig config) {
        for (var v : values) {
            if (!v.accepts(config.get(v.path)))
                config.set(v.path, v.initial instanceof Enum<?> e ? e.name() : v.initial);
            if (v.comment != null && config.getComment(v.path) == null)
                config.setComment(v.path, v.comment);
        }
    }
    public void acceptConfig(ILoadedConfig loaded) { this.loaded = loaded; }
}
