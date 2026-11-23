package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.*;
import com.electronwill.nightconfig.toml.*;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import net.neoforged.fml.ModContainer;
public final class ModConfig {
    public enum Type {
        COMMON,
        CLIENT,
        SERVER,
        STARTUP;
        public String extension() { return name().toLowerCase(Locale.ROOT); }
    }
    private final String owner;
    private final java.util.function.Consumer<net.neoforged.fml.event.config.ModConfigEvent> events;
    private final Type type;
    private final IConfigSpec spec;
    private final String name;
    private Path path;
    private IConfigSpec.ILoadedConfig loaded;
    ModConfig(String owner, Type type, IConfigSpec spec, String name, java.util.function.Consumer<net.neoforged.fml.event.config.ModConfigEvent> events) {
        this.events=events;
        this.owner = owner;
        this.type = type;
        this.spec = spec;
        this.name = name;
        spec.validateSpec(this);
    }
    public IConfigSpec getSpec() { return spec; }
    public String getModId() { return owner; }
    public String getFileName() { return name; }
    public Path getFullPath() { return path; }
    public Type getType() { return type; }
    public IConfigSpec.ILoadedConfig getLoadedConfig() { return loaded; }
    private String previousText;
    CommentedConfig parse(String text) {
        var values = new TomlParser().parse(new StringReader(text));
        if (!spec.isCorrect(values)) throw new IllegalArgumentException("Invalid values in " + name);
        return values;
    }
    String snapshot() {
        var out = new StringWriter();
        new TomlWriter().write(loaded.config(),out);
        return out.toString();
    }
    void accept(CommentedConfig values, boolean remote) {
        var handle = new IConfigSpec.ILoadedConfig() {
            public CommentedConfig config() { return values; }
            public void save() {
                synchronized (ModConfig.this) {
                    if (remote || loaded != this) throw new IllegalStateException("Config is not locally writable: " + name);
                    write(values);
                }
            }
        };
        spec.acceptConfig(handle);
        loaded=handle;
    }
    void notifyLoaded(boolean initial) {
        if (initial) events.accept(new net.neoforged.fml.event.config.ModConfigEvent.Loading(this));
        else events.accept(new net.neoforged.fml.event.config.ModConfigEvent.Reloading(this));
    }
    private String read() throws IOException {
        if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path))
            throw new IOException("Invalid config path " + name);
        try (var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=in.readNBytes(524289);
            if (bytes.length>524288) throw new IOException("Oversized config " + name);
            return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    boolean reload() {
        CommentedConfig next;
        try {
            String text=read();
            if (text.equals(previousText)) return false;
            next=parse(text);
            ConfigSync.checkReload(this,next);
            previousText=text;
        } catch (IOException | RuntimeException invalid) {
            System.err.println("[Unity] 配置重载失败，继续使用上次有效值 " + name + " (" + invalid.getMessage() + ")");
            return false;
        }
        accept(next,false);
        notifyLoaded(false);
        return true;
    }
    synchronized void load(Path directory) {
        try {
            Path root = Files.createDirectories(directory).toRealPath();
            path = root.resolve(name).normalize();
            if (!path.startsWith(root) || Files.isSymbolicLink(path))
                throw new IOException("Invalid config path " + name);
            if (!Files.createDirectories(path.getParent()).toRealPath().startsWith(root))
                throw new IOException("Config leaves game directory");
            boolean exists = Files.exists(path);
            previousText = exists ? read() : null;
            var values = exists ? new TomlParser().parse(new StringReader(previousText)) : TomlFormat.newConfig();
            if (!exists || !spec.isCorrect(values)) {
                spec.correct(values);
                write(values);
                previousText=read();
            }
            accept(values,false);
            notifyLoaded(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    private synchronized void write(CommentedConfig values) {
        try {
            Path stage = Files.createTempFile(path.getParent(), ".unity-config-", ".tmp");
            try {
                try (var writer = Files.newBufferedWriter(stage)) {
                    new TomlWriter().write(values, writer);
                }
                Files.move(stage, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(stage);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    void unload() {
        if (loaded == null)
            return;
        try {
            events.accept(new net.neoforged.fml.event.config.ModConfigEvent.Unloading(this));
        } finally {
            spec.acceptConfig(null);
            loaded = null;
            path = null;
            previousText=null;
        }
    }
}
