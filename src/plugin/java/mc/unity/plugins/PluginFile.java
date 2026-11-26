package mc.unity.plugins;

import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.provider.classloader.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

record PluginFile(Path jar, PluginDescriptionFile description, boolean paper, List<String> requires,
                  List<String> after, List<String> before, Set<String> imports) {
    String name() {
        return description.getName();
    }
    static PluginFile read(Path path) throws Exception {
        try (var zip = new JarFile(path.toFile())) {
            boolean paper = zip.getEntry("paper-plugin.yml") != null;
            var entry = zip.getEntry(paper ? "paper-plugin.yml" : "plugin.yml");
            if (entry == null)
                throw new IOException("Missing plugin descriptor in " + path);
            byte[] bytes;
            try (var in = zip.getInputStream(entry)) {
                bytes = in.readAllBytes();
            }
            var desc = new PluginDescriptionFile(new ByteArrayInputStream(bytes));
            if (!desc.getName().matches("[A-Za-z0-9_.-]+") || desc.getName().equals(".") || desc.getName().equals(".."))
                throw new IOException("Invalid plugin name in " + path);
            var yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<?, ?> raw = yaml.load(new ByteArrayInputStream(bytes));
            if (raw.containsKey("bootstrapper") || raw.containsKey("loader") || raw.containsKey("libraries"))
                throw new IOException(
                    desc.getName() +
                    " requires a custom bootstrap, loader or library resolver outside this Alpha");
            var needs = new ArrayList<String>(desc.getDepend());
            var after = new ArrayList<String>(desc.getSoftDepend());
            var before = new ArrayList<String>(desc.getLoadBefore());
            var imports = new LinkedHashSet<String>(needs);
            imports.addAll(after);
            if (paper && raw.get("dependencies") instanceof Map<?,?> scopes) {
                if (scopes.get("bootstrap") instanceof Map<?,?> b && !b.isEmpty())
                    throw new IOException("Unsupported bootstrap dependencies: " + path);
                if (scopes.get("server") instanceof Map<?,?> deps)
                    for (var e : deps.entrySet()) {
                        String id = e.getKey().toString();
                    if (!(e.getValue() instanceof Map<?,?> opt))
                        throw new IOException("Invalid Paper dependency: " + id);
                    if (!Boolean.FALSE.equals(opt.get("required")))
                        needs.add(id);
                    if ("BEFORE".equals(opt.get("load")))
                        after.add(id);
                    if ("AFTER".equals(opt.get("load")))
                        before.add(id);
                    if (!Boolean.FALSE.equals(opt.get("join-classpath")))
                        imports.add(id);
                    }
            }
            return new PluginFile(path, desc, paper, needs, after, before, imports);
        }
    }
    static List<PluginFile> ordered(Path directory) throws Exception {
        var files = new TreeMap<String, PluginFile>(String.CASE_INSENSITIVE_ORDER);
        try (var list = Files.list(Files.createDirectories(directory))) {
            for (var jar : list.filter(p
                                       -> Files.isRegularFile(p) &&
                                              p.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                               .sorted()
                               .toList()) {
                var file = read(jar);
                if (files.putIfAbsent(file.name(), file) != null)
                    throw new IOException("Duplicate plugin " + file.name());
            }
        }
        var names = providers(files.values());
        var edges = new TreeMap<String, Set<String>>(String.CASE_INSENSITIVE_ORDER);
        var preferred = new TreeMap<String, Set<String>>(String.CASE_INSENSITIVE_ORDER);
        files.keySet().forEach(n -> {
            edges.put(n, new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
            preferred.put(n, new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
        });
        for (var f : files.values()) {
            for (var n : f.requires) {
                if (!names.containsKey(n))
                    throw new IOException(f.name() + " requires missing plugin " + n);
                if (!f.paper)
                    edges.get(f.name()).add(names.get(n));
            }
            for (var n : f.after)
                if (names.containsKey(n))
                    (f.paper && f.requires.contains(n) ? edges : preferred).get(f.name()).add(names.get(n));
            for (var n : f.before)
                if (names.containsKey(n))
                    (f.paper && f.requires.contains(n) ? edges : preferred).get(names.get(n)).add(f.name());
        }
        var result = new ArrayList<PluginFile>();
        while (!edges.isEmpty()) {
            var next = edges.entrySet()
                           .stream()
                           .filter(e -> e.getValue().isEmpty())
                           .map(Map.Entry::getKey)
                           .min(Comparator.<String>comparingInt(n -> preferred.get(n).size())
                                    .thenComparing(String.CASE_INSENSITIVE_ORDER))
                           .orElseThrow(() -> new IOException("Plugin dependency cycle: " + edges));
            result.add(files.get(next));
            edges.remove(next);
            edges.values().forEach(d -> d.remove(next));
            preferred.values().forEach(d -> d.remove(next));
        }
        return result;
    }

    static Map<String, String> providers(Collection<PluginFile> files) throws IOException {
        var names = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (var file : files)
            names.put(file.name(), file.name());
        for (var file : files) {
            for (String alias : file.description().getProvides()) {
                String previous = names.putIfAbsent(alias, file.name());
                if (previous != null && !previous.equalsIgnoreCase(file.name()) &&
                    files.stream().noneMatch(f -> f.name().equalsIgnoreCase(alias)))
                    throw new IOException("Ambiguous plugin provider " + alias + ": " + previous + ", " + file.name());
            }
        }
        return names;
    }

    static final class Loader extends URLClassLoader implements ConfiguredPluginClassLoader {
        static {
            if (!registerAsParallelCapable())
                throw new ExceptionInInitializerError("Plugin classloader registration");
        }
        private volatile List<Loader> dependencies = List.of();
        final PluginFile file;
        final Host host;
        JavaPlugin plugin;
        private final Map<String,byte[]> platformClasses=new HashMap<>();
        private static final String WE_ADAPTER="mc.unity.adapter.worldedit.UnityWorldEdit";
        void enable() {
            String key="worldedit.bukkit.adapter";
            boolean useUnity=platformClasses.containsKey(WE_ADAPTER) && System.getProperty(key)==null;
            if(useUnity)System.setProperty(key,WE_ADAPTER);
            try {plugin.setEnabled(true);}
            finally {if(useUnity && WE_ADAPTER.equals(System.getProperty(key)))System.clearProperty(key);}
        }
        @Override public void close() throws IOException {
            try {
                // 7.4.6 creates a private non-daemon timer for command feedback and exposes no shutdown method.
                // Only inspect the feedback class if this loader has already defined it.
                Class<?> progress=platformClasses.containsKey(WE_ADAPTER) ?
                    findLoadedClass("com.sk89q.worldedit.command.util.FutureProgressListener") : null;
                if(progress!=null) {
                    var timer=java.lang.invoke.MethodHandles.privateLookupIn(progress,java.lang.invoke.MethodHandles.lookup())
                        .findStaticVarHandle(progress,"timer",java.util.Timer.class);
                    ((java.util.Timer)timer.get()).cancel();
                }
            } catch(ReflectiveOperationException ex){throw new IOException("Closing WorldEdit command feedback",ex);}
            finally{super.close();}
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] data=platformClasses.get(name);
            return data==null ? super.findClass(name) : defineClass(name,data,0,data.length);
        }
        Loader(PluginFile file, Host host) throws Exception {
            super(file.name(), new URL[] {file.jar.toUri().toURL()}, Host.class.getClassLoader());
            this.file = file;
            this.host = host;
            if(file.description().getMain().equals("com.sk89q.worldedit.bukkit.WorldEditPlugin")) {
                var hash=java.security.MessageDigest.getInstance("SHA-512");
                try(var in=new java.security.DigestInputStream(Files.newInputStream(file.jar()),hash)){in.transferTo(OutputStream.nullOutputStream());}
                if(HexFormat.of().formatHex(hash.digest()).equals("1fe3975132857093f971fed9efc601299bfa76fa7885f52a542fee41e7a0165ca9cb3cbcf5ae244474887d774d650b0db6f8f79aa80f98b245dea44f1c1b4152")) {
                    try(var in=new java.util.jar.JarInputStream(Objects.requireNonNull(Host.class.getResourceAsStream("/META-INF/unity/worldedit-7.4.6.jar")))) {
                        for(var entry=in.getNextJarEntry();entry!=null;entry=in.getNextJarEntry()){
                            String n=entry.getName();
                            if(n.endsWith(".class"))platformClasses.put(n.substring(0,n.length()-6).replace('/','.'),in.readAllBytes());
                        }
                    }
                }
            }
        }
        void link(Map<String, Loader> available) {
            var seen = new LinkedHashSet<Loader>();
            var queue = new ArrayDeque<Loader>();
            seen.add(this);
            queue.add(this);
            while (!queue.isEmpty()) {
                for (String id : queue.remove().file.imports()) {
                    var next = available.get(id);
                    if (next != null && seen.add(next))
                        queue.add(next);
                }
            }
            seen.remove(this);
            dependencies = List.copyOf(seen);
        }
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            return loadVisible(name, resolve, true);
        }
        private Class<?> loadVisible(String name, boolean resolve, boolean imports)
            throws ClassNotFoundException {
            try {
                Class<?> result = super.loadClass(name, resolve);
                if (!imports && result.getClassLoader() instanceof Loader owner && owner != this)
                    throw new ClassNotFoundException(name);
                return result;
            } catch (ClassNotFoundException missing) {
                if (imports) {
                    String path = name.replace('.', '/') + ".class";
                    for (var owner : dependencies) {
                        if (owner.findResource(path) != null)
                            return owner.local(name, resolve);
                    }
                }
                throw missing;
            }
        }
        private Class<?> local(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                var found = findLoadedClass(name);
                if (found == null)
                    found = findClass(name);
                if (resolve)
                    resolveClass(found);
                return found;
            }
        }
        @Override
        public Class<?> loadClass(String name, boolean resolve, boolean global, boolean libraries)
            throws ClassNotFoundException {
            return loadVisible(name, resolve, global);
        }
        @Override
        public PluginMeta getConfiguration() {
            return file.description;
        }
        @Override
        public JavaPlugin getPlugin() {
            return plugin;
        }
        @Override
        public PluginClassLoaderGroup getGroup() {
            return null;
        }
        @Override
        public void init(JavaPlugin instance) {
            if (plugin != null)
                throw new IllegalStateException("Already initialized " + file.name());
            plugin = instance;
            instance.init(host.server, file.description, host.directory.resolve(file.name()).toFile(),
                          file.jar.toFile(), this, file.description, Logger.getLogger(file.name()));
        }
    }
}
