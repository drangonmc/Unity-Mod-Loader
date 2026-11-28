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
            if (raw.containsKey("bootstrapper") || raw.containsKey("loader"))
                throw new IOException(
                    desc.getName() +
                    " requires a custom bootstrap or loader outside the supported API");
            if(raw.containsKey("libraries") && !(raw.get("libraries") instanceof List<?>))
                throw new IOException("Expected a list of Maven coordinates in "+path);
            for (String coordinate:desc.getLibraries()) {
                if (!coordinate.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_-]+)*:[A-Za-z0-9][A-Za-z0-9_.-]*:[A-Za-z0-9][A-Za-z0-9_.+-]*") ||
                        coordinate.contains(".."))
                    throw new IOException("Invalid Bukkit library coordinate "+coordinate+" in "+path);
            }
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
        private URLClassLoader libraries;
        private final Set<java.util.Timer> migrationTimers=Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean timersClosed;
        synchronized void ownTimer(java.util.Timer timer){if(timersClosed)timer.cancel();else migrationTimers.add(timer);}
        private synchronized void closeTimers(){
            timersClosed=true;
            for(var timer:migrationTimers)timer.cancel();
            if(!migrationTimers.isEmpty())Host.LOG.info("Released WorldGuard UUID migration timers: "+migrationTimers.size());
            migrationTimers.clear();
        }
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
            closeTimers();
            try {
                // 7.4.6 creates a private non-daemon timer for command feedback and exposes no shutdown method.
                // Only inspect the feedback class if this loader has already defined it.
                Class<?> progress=platformClasses.containsKey(WE_ADAPTER) ?
                    findLoadedClass("com.sk89q.worldedit.command.util.FutureProgressListener") : null;
                // EssentialsX 2.22 uses a private non-daemon pool for locale bundles with no public close method.
                // The pool belongs exclusively to this plugin class loader and is drained after onDisable.
                var i18n = file.description().getMain().equals("com.earth2me.essentials.Essentials") ?
                    findLoadedClass("com.earth2me.essentials.I18n") : null;
                if(i18n!=null)try{
                    var accessor=java.lang.invoke.MethodHandles.privateLookupIn(i18n,java.lang.invoke.MethodHandles.lookup())
                            .findStaticVarHandle(i18n,"BUNDLE_LOADER_EXECUTOR",java.util.concurrent.ExecutorService.class);
                    var pool=(java.util.concurrent.ExecutorService)accessor.get();pool.shutdown();
                    try {if(!pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS))pool.shutdownNow();}
                    catch(InterruptedException interrupted){pool.shutdownNow();Thread.currentThread().interrupt();}
                } catch (ReflectiveOperationException | RuntimeException ex){Host.LOG.log(java.util.logging.Level.WARNING,"Closing EssentialsX locale executor",ex);}
                if(progress!=null) try {
                    var timer=java.lang.invoke.MethodHandles.privateLookupIn(progress,java.lang.invoke.MethodHandles.lookup())
                        .findStaticVarHandle(progress,"timer",java.util.Timer.class);
                    ((java.util.Timer)timer.get()).cancel();
                } catch(ReflectiveOperationException | RuntimeException ex){Host.LOG.log(java.util.logging.Level.WARNING,"Closing WorldEdit command feedback",ex);}
            }
            finally {
                try { if(libraries!=null)libraries.close(); }
                finally {super.close();}
            }
        }
        private URL ownResource(String name) {return super.findResource(name);}
        @Override public URL findResource(String name) {
            var local=ownResource(name);
            return local!=null || libraries==null?local:libraries.findResource(name);
        }
        @Override public Enumeration<URL> findResources(String name)throws IOException {
            var urls=new LinkedHashSet<>(Collections.list(super.findResources(name)));
            if(libraries!=null)urls.addAll(Collections.list(libraries.findResources(name)));
            return Collections.enumeration(urls);
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if(name.equals("com.sk89q.worldguard.protection.managers.migration.UUIDMigration") &&
                    file.description().getMain().equals("com.sk89q.worldguard.bukkit.WorldGuardPlugin")){
                try(var jar=new java.util.jar.JarFile(file.jar().toFile(),true,java.util.zip.ZipFile.OPEN_READ,Runtime.version())){
                    var item=jar.getJarEntry(name.replace('.','/')+".class");
                    if(item==null)throw new ClassNotFoundException(name);
                    byte[] bytes;try(var in=jar.getInputStream(item)){bytes=in.readAllBytes();}
                    if(item.getCodeSigners()!=null)return super.findClass(name);
                    var origin=file.jar().toUri().toURL();String pkg=name.substring(0,name.lastIndexOf('.'));
                    var manifest=jar.getManifest();
                    if(getDefinedPackage(pkg)==null)try{
                        if(manifest!=null)definePackage(pkg,manifest,origin);else definePackage(pkg,null,null,null,null,null,null,null);
                    }catch(IllegalArgumentException race){if(getDefinedPackage(pkg)==null)throw race;}
                    var defined=getDefinedPackage(pkg);
                    if(defined.isSealed() && !defined.isSealed(origin))throw new SecurityException("Sealed package "+pkg);
                    bytes=PluginTimers.migration(bytes,this);
                    return defineClass(name,bytes,0,bytes.length,new java.security.CodeSource(origin,(java.security.CodeSigner[])null));
                }catch(IOException failure){throw new ClassNotFoundException(name,failure);}
            }
            byte[] data=platformClasses.get(name);
            return data==null ? super.findClass(name) : defineClass(name,data,0,data.length);
        }
        Loader(PluginFile file, Host host) throws Exception {
            super(file.name(), new URL[] {file.jar.toUri().toURL()}, Host.class.getClassLoader());
            this.file = file;
            this.host = host;
            if(file.description().getMain().equals("com.sk89q.worldedit.bukkit.WorldEditPlugin")) {
                try (var candidate = new java.util.jar.JarFile(file.jar().toFile(),true,java.util.zip.ZipFile.OPEN_READ,Runtime.version())) {
                    try(var in=new java.util.jar.JarInputStream(Objects.requireNonNull(Host.class.getResourceAsStream("/META-INF/unity/worldedit-7.4.6.jar")))) {
                        for(var entry=in.getNextJarEntry();entry!=null;entry=in.getNextJarEntry()){
                            String n=entry.getName();
                            if(n.endsWith(".class"))platformClasses.put(n.substring(0,n.length()-6).replace('/','.'),in.readAllBytes());
                        }
                    }
                    mc.unity.bootstrap.JarContract.check(candidate, platformClasses.values(),"com/sk89q/worldedit/","org/enginehub/linbus/");

                }
            }
        }
        void link(Map<String, Loader> available) {
            if (!file.description().getLibraries().isEmpty()) {
                var resolver=new org.bukkit.plugin.java.LibraryLoader(Host.LOG);
                try {
                    var field=java.lang.invoke.MethodHandles.privateLookupIn(resolver.getClass(),java.lang.invoke.MethodHandles.lookup())
                        .findVarHandle(resolver.getClass(),"repository",org.eclipse.aether.RepositorySystem.class);
                    var system=(org.eclipse.aether.RepositorySystem)field.get(resolver);
                    try { libraries=(URLClassLoader)resolver.createLoader(file.description()); }
                    finally {system.shutdown();}
                } catch (ReflectiveOperationException e){throw new IllegalStateException("Paper library resolver contract",e);}
            }
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
            return loadVisible(name, resolve, true, true);
        }
        private Class<?> loadVisible(String name, boolean resolve, boolean imports, boolean useLibraries)
            throws ClassNotFoundException {
            try {
                Class<?> result = super.loadClass(name, resolve);
                if (!imports && result.getClassLoader() instanceof Loader owner && owner != this)
                    throw new ClassNotFoundException(name);
                return result;
            } catch (ClassNotFoundException missing) {
                if (useLibraries && libraries!=null)
                    try {return libraries.loadClass(name);} catch (ClassNotFoundException absent) { /* Try exported plugin types next. */ }
                if (imports) {
                    String path = name.replace('.', '/') + ".class";
                    var candidates=new LinkedHashSet<>(dependencies);
                    // Classic Bukkit exports plugin API types to legacy plugins with undeclared soft dependencies.
                    if(!file.paper()) candidates.addAll(host.loaders.values());
                    candidates.remove(this);
                    for (var owner : candidates) {
                        if (owner.ownResource(path) != null)
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
            return loadVisible(name, resolve, global, libraries);
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
