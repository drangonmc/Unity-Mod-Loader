package mc.unity.bootstrap;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.*;
import java.util.function.BiFunction;
import java.util.jar.*;

/** Owns game class definition. Transformations apply only to pinned vanilla classes in this loader. */
public final class GameLoader extends URLClassLoader {
    static {
        registerAsParallelCapable();
    }
    private final JarFile vanilla;
    private final URL origin;
    private Map<String, byte[]> patches = Map.of();
    private boolean mixinStarted;
    private boolean fullFabricApi;
    private BiFunction<String,byte[],byte[]> optionalGuard;
    public boolean alreadyDefined(String name) {return findLoadedClass(name)!=null;}
    private Map<Path, Path> containers = Map.of();
    public Path vanillaPath() {
        return Path.of(vanilla.getName());
    }
    public Map<Path, Path> containers() {
        return containers;
    }
    public static Set<String> clientEntries(JarFile jar) throws IOException {
        var mf = jar.getManifest();
        if (mf == null)
            return Set.of();
        var value = mf.getMainAttributes().getValue("Fabric-Loom-Client-Only-Entries");
        return value == null ? Set.of() : new HashSet<>(Arrays.asList(value.split(";")));
    }
    public boolean fullFabricApi() {
        return fullFabricApi;
    }
    private final Set<Path> neoMods = new HashSet<>();
    private Path kffService;
    private Path puzzles;
    private java.util.function.UnaryOperator<byte[]> puzzlesGuard;
    private boolean puzzlesContract(JarFile jar) throws IOException {
        var consumers = new ArrayList<byte[]>();
        for (var name:List.of("CommonPlatform","ClientPlatform","PuzzlesLifecycle","PuzzlesClient"))
            try (var in=super.getResourceAsStream("mc/unity/adapter/puzzles/"+name+".class")) {
                if (in==null) throw new IOException("Missing Unity Puzzles Lib adapter " +name);
                consumers.add(in.readAllBytes());
            }
        JarContract.check(jar,consumers,"fuzs/puzzleslib/");
        return true;
    }
    public boolean usesPuzzlesSpi(Path file) {
        return file.equals(puzzles);
    }

    GameLoader(URL[] urls, Path game) throws IOException {
        super("Unity Minecraft 26.2", urls, ClassLoader.getPlatformClassLoader());
        vanilla = new JarFile(game.toFile(), true, JarFile.OPEN_READ, Runtime.version());
        origin = game.toUri().toURL();
        gameLibraries.addAll(Arrays.asList(urls));
        gameLibraries.remove(urls[0]);
        gameLibraries.remove(origin);
    }
    void install(Map<String, byte[]> classes) throws ClassNotFoundException {
        if (!patches.isEmpty())
            throw new IllegalStateException("Game hooks already installed");
        patches = Map.copyOf(classes);
    }
    private final Map<Path, JarFile> mods = new LinkedHashMap<>();
    private volatile Map<String, List<JarFile>> resources = Map.of();
    private final Map<URL, JarFile> libraries = new HashMap<>();
    private final Set<URL> gameLibraries = new HashSet<>();
    private volatile BiFunction<String, byte[], byte[]> mixin;
    private final Map<String, String> mixinOwners = new LinkedHashMap<>();
    private Path mounted;
    private AutoCloseable nested;
    private Map<Path,String[]> generatedLibraries=Map.of();
    public String[] generatedLibrary(Path path){return generatedLibraries.get(path);}
    private Set<Path> plainLibraries = Set.of();
    private Map<Path, String> dependencyStamps = Map.of();
    public boolean plainLibrary(Path path) {
        return plainLibraries.contains(path);
    }
    public Map<Path, String> dependencyStamps() {
        return dependencyStamps;
    }
    private static final Set<String> trustedFabricModules = trustedModules();
    private static Set<String> trustedModules() {
        try (var in = GameLoader.class.getResourceAsStream("/META-INF/unity/platform-libraries.sha256")) {
            if (in == null)
                throw new IOException("Missing platform library lock");
            return Set.copyOf(
                new BufferedReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.US_ASCII))
                    .lines()
                    .toList());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    public static boolean reserved(String n) {
        if(n.startsWith("net/fabricmc/language/"))return false; // Language providers are distributed as mods. The Loader APIs live under loader/ and api/.
        return List
            .of("java/", "javax/", "jdk/", "mc/unity/", "net/minecraft/", "net/minecraftforge/",
                "net/neoforged/", "net/fabricmc/", "org/spongepowered/asm/", "org/objectweb/asm/")
            .stream()
            .anyMatch(n::startsWith);
    }
    void mixins(Path game, boolean client) throws Exception {
        archives(game, client);
    }
    public synchronized Map<Path, JarFile> archives(Path game, boolean client) throws Exception {
        game = game.toAbsolutePath().normalize();
        if (mounted != null) {
            if (!mounted.equals(game))
                throw new IOException("Loader already assigned to " + mounted);
            return Map.copyOf(mods);
        }
        mounted = game;
        var configs = new LinkedHashSet<String>();
        var index = new HashMap<String, List<JarFile>>();
        try {
            var reader = Class.forName("mc.unity.adapter.common.MixinDeclarations", true, this)
                             .getMethod("read", JarFile.class, boolean.class);
            var resolver = Class.forName("mc.unity.adapter.common.NestedArchives", true, this);
            nested = (AutoCloseable)resolver.getMethod("resolve", Path.class, boolean.class)
                         .invoke(null, game.resolve("mods"), client);
            containers = (Map<Path, Path>)resolver.getMethod("containers").invoke(nested);
            generatedLibraries=(Map<Path,String[]>)resolver.getMethod("generatedLibraries").invoke(nested);
            plainLibraries = (Set<Path>)resolver.getMethod("plainLibraries").invoke(nested);
            dependencyStamps = (Map<Path, String>)resolver.getMethod("fingerprints").invoke(nested);
            {
                var selected = (List<Path>)resolver.getMethod("files").invoke(nested);
                var upstreamModules = (Set<Path>)resolver.getMethod("fabricProviders").invoke(nested);
                fullFabricApi = !upstreamModules.isEmpty();
                for (var file : selected) {
                    var jar = new JarFile(file.toFile(), true, JarFile.OPEN_READ, Runtime.version());
                    mods.put(file, jar);
                    boolean kff=KffLanguage.provider(jar);
                    if(kff){
                        if(kffService!=null)throw new IOException("Multiple KFF language services");
                        kffService=file;
                    }
                    if (jar.getJarEntry("fabric.mod.json") == null &&
                        jar.getJarEntry("META-INF/neoforge.mods.toml") != null)
                        neoMods.add(file);
                    if (jar.getEntry("META-INF/services/fuzs.puzzleslib.common.impl.core.proxy.ProxyImpl") !=
                            null &&
                        (boolean)resolver.getMethod("puzzles",Path.class).invoke(nested,file) &&
                        puzzlesContract(jar))
                        puzzles = file;
                    Boolean fabricModule = null;
                    @SuppressWarnings("unchecked") var list = (List<String>)reader.invoke(null, jar, client);
                    if (list == null) {
                        mods.remove(file);
                        jar.close();
                        continue;
                    }
                    var omitted = client ? Set.<String>of() : clientEntries(jar);
                    for (var name : list) {
                        if (omitted.contains(name))
                            continue;
                        if (jar.getJarEntry(name) == null || !configs.add(name))
                            throw new IOException("Missing or duplicate Mixin configuration " + name +
                                                  " in " + file.getFileName() + ", previous " +
                                                  mixinOwners.get(name));
                        mixinOwners.put(name, file.getFileName().toString());
                    }
                    for (var entries = jar.versionedStream().iterator(); entries.hasNext();) {
                        var entry = entries.next();
                        String n = entry.getName();
                        if (entry.isDirectory() || kff && KffLanguage.hostInternal(n) || omitted.contains(n) ||
                            n.startsWith("META-INF/versions/") || n.equals("module-info.class") ||
                            n.equals("META-INF/MANIFEST.MF"))
                            continue;
                        boolean fabricClass = n.endsWith(".class") && n.startsWith("net/fabricmc/fabric/");
                        if (fabricClass && fabricModule == null)
                            fabricModule =
                                jar.getEntry("fabric.mod.json") != null &&
                                (upstreamModules.contains(file) || trustedFabricModules.contains(VanillaBundle.hash(file)));
                        if (n.endsWith(".class") &&
                            ((reserved(n) && !(fabricClass && Boolean.TRUE.equals(fabricModule))) ||
                             index.containsKey(n) ||
                             super.findResource(n) != null &&
                                 !(fullFabricApi && fabricClass && Boolean.TRUE.equals(fabricModule) &&
                                   findLoadedClass(n.substring(0, n.length() - 6).replace('/', '.')) ==
                                       null)))
                            throw new IOException("Duplicate or reserved mod class " + n + " in " + file);
                        index.computeIfAbsent(n, k -> new ArrayList<>()).add(jar);
                    }
                }
            }
            for (String config : configs)
                if (index.getOrDefault(config, List.of()).size() != 1 || super.findResource(config) != null)
                    throw new IOException("Ambiguous config " + config);
            index.replaceAll((n, owners) -> List.copyOf(owners));
            resources = Map.copyOf(index);
            var transforms = Class.forName("mc.unity.adapter.common.AccessTransforms", true, this);
            var overlay = (Map<String, byte[]>)transforms
                              .getMethod("prepare", Collection.class, ClassLoader.class, boolean.class)
                              .invoke(null, mods.values(), this, client);
            if (!overlay.isEmpty()) {
                var updated = new HashMap<>(patches);
                for (var patch : overlay.entrySet()) {
                    if (findLoadedClass(patch.getKey().replace('/', '.')) != null)
                        throw new IOException("Access target already defined: " + patch.getKey());
                    updated.put(patch.getKey(), patch.getValue());
                }
                patches = Map.copyOf(updated);
                System.out.println("[Unity] Access transforms " + overlay.keySet());
            }
            optionalGuard=(BiFunction<String,byte[],byte[]>)Class.forName("mc.unity.adapter.common.OptionalLinkage",true,this)
                .getMethod("transformer").invoke(null);
            if (puzzles != null) {
                @SuppressWarnings("unchecked")
                var guard = (java.util.function.UnaryOperator<byte[]>)Class
                                .forName("mc.unity.adapter.neoforge.PuzzlesModules", true, this)
                                .getMethod("transformer")
                                .invoke(null);
                puzzlesGuard = guard;
            }
            if (!configs.isEmpty()) {
                var fabric = Class.forName("mc.unity.adapter.fabric.LoaderView", true, this);
                fabric.getMethod("bootstrap", Path.class, boolean.class, Collection.class)
                    .invoke(null, game, client, resolver.getMethod("metadata").invoke(nested));
                mixinStarted = true;
                var impl = Class.forName("mc.unity.mixin.UnityMixinService", true, this);
                @SuppressWarnings("unchecked")
                var transformer =
                    (BiFunction<String, byte[], byte[]>)impl.getMethod("start", List.class, boolean.class)
                        .invoke(null, List.copyOf(configs), client);
                mixin = transformer;
                System.out.println("[Unity] Mixin configurations " + configs);
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(mods));
        } catch (Exception | LinkageError e) {
            ModDiagnostics.failure(game, mixinStarted ? "Mixin / 初始化" : "发现模组", e, mixinOwners);
            Throwable primary = e instanceof java.lang.reflect.InvocationTargetException invocation &&
                                        invocation.getCause() instanceof Exception
                                    ? invocation.getCause()
                                    : e;
            try {
                releaseMods();
            } catch (IOException close) {
                primary.addSuppressed(close);
            }
            if (primary instanceof Exception exception)
                throw exception;
            throw (LinkageError)primary;
        }
    }
    public synchronized void releaseMods() throws IOException {
        var handles = List.copyOf(mods.values());
        // A Mixin plugin can open Fabric root paths before Platforms owns the session.
        IOException metadataFailure = null;
        if (findLoadedClass("mc.unity.adapter.fabric.LoaderView") != null) {
            try {
                var view = findLoadedClass("mc.unity.adapter.fabric.LoaderView");
                ((AutoCloseable)view.getField("INSTANCE").get(null)).close();
            } catch (Exception e) {
                metadataFailure = new IOException("Closing early mod metadata", e);
            }
        }
        mods.clear();
        neoMods.clear();
        resources = Map.of();
        mixinOwners.clear();
        mounted = null;
        fullFabricApi = false;
        containers = Map.of();
        kffService=null;
        puzzles = null;
        puzzlesGuard = null;
        optionalGuard = null;
        generatedLibraries=Map.of();plainLibraries = Set.of();
        dependencyStamps = Map.of();
        try (Closeable nestedArchives = this::closeNested; Closeable modFiles = () -> closeAll(handles)) {
            if (metadataFailure != null)
                throw metadataFailure;
        }
    }
    private void closeNested() throws IOException {
        if (nested == null)
            return;
        try {
            nested.close();
            nested = null;
        } catch (Exception e) {
            throw new IOException("Closing nested archives", e);
        }
    }
    private void closeLibraries() throws IOException {
        try {
            closeAll(libraries.values());
        } finally {
            libraries.clear();
        }
    }
    private static void closeAll(Collection<JarFile> files) throws IOException {
        IOException failure = null;
        for (var f : files)
            try {
                f.close();
            } catch (IOException e) {
                if (failure == null)
                    failure = e;
                else
                    failure.addSuppressed(e);
            }
        if (failure != null)
            throw failure;
    }
    private URL url(JarFile jar, String name) throws IOException {
        java.net.URI uri;
        try {
            var entryPath = new java.net.URI(null, null, "/" + name, null).toASCIIString();
            uri = java.net.URI.create("unityjar:" + Path.of(jar.getName()).toUri().toASCIIString() + "!" +
                                      entryPath);
        } catch (java.net.URISyntaxException | IllegalArgumentException e) {
            throw new IOException("Invalid archive resource " + name, e);
        }
        return URL.of(uri, new URLStreamHandler() {
            protected URLConnection openConnection(URL u) {
                return new URLConnection(u) {
                    public void connect() {}
                    public InputStream getInputStream() throws IOException {
                        try {
                            var entry = jar.getJarEntry(name);
                            if (entry == null)
                                throw new FileNotFoundException(jar.getName() + "!/" + name);
                            return jar.getInputStream(entry);
                        } catch (IllegalStateException closed) {
                            throw new IOException("Archive closed: " + jar.getName() + "!/" + name, closed);
                        }
                    }
                };
            }
        });
    }
    private boolean privateService(String n) {
        return n.startsWith("META-INF/services/") &&
            reserved(n.substring("META-INF/services/".length()).replace('.', '/'));
    }
    @Override
    public URL findResource(String name) {
        if (fullFabricApi && name.startsWith("net/fabricmc/fabric/") && name.endsWith(".class")) {
            var owners = resources.get(name);
            if (owners != null)
                try {
                    return url(owners.getFirst(), name);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
        }
        var base = super.findResource(name);
        if (base != null || privateService(name))
            return base;
        var files = resources.get(name);
        if (files == null)
            return null;
        try {
            return url(files.getFirst(), name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    @Override
    public Enumeration<URL> findResources(String n) throws IOException {
        var list = Collections.list(super.findResources(n));
        if (puzzles != null && n.startsWith("META-INF/services/fuzs.puzzleslib.")) {
            var provider =
                super.findResource("META-INF/unity/puzzles/" + n.substring("META-INF/services/".length()));
            if (provider != null)
                list.addFirst(provider);
        }
        if (!privateService(n))
            for (var file : resources.getOrDefault(n, List.of()))
                list.add(url(file, n));
        return Collections.enumeration(list);
    }
    @Override
    public InputStream getResourceAsStream(String name) {
        if(kffService!=null && name.equals(KffLanguage.CONTAINER+".class"))return new ByteArrayInputStream(KffLanguage.container());
        if (name.endsWith(".class")) {
            var patch = patches.get(name.substring(0, name.length() - 6));
            if (patch != null)
                return new ByteArrayInputStream(patch);
        }
        return super.getResourceAsStream(name);
    }
    private synchronized JarFile library(URL archive) throws Exception {
        var jar = libraries.get(archive);
        if (jar == null) {
            jar = new JarFile(Path.of(archive.toURI()).toFile(), true, JarFile.OPEN_READ, Runtime.version());
            libraries.put(archive, jar);
        }
        return jar;
    }
    private final Set<String> invalidMixinClasses=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public void invalidMixin(String name){invalidMixinClasses.add(name.replace('/','.'));}
    public byte[] beforeMixins(String name,byte[] bytes) {
        if(bytes==null)return null;
        String key=name.replace('.','/');
        var owners=resources.get(key+".class");
        Path file=owners==null?null:Path.of(owners.getFirst().getName());
        bytes = patches.getOrDefault(key, bytes);
        if(key.equals(KffLanguage.CONTAINER) && file!=null && file.equals(kffService))bytes=KffLanguage.container();
        if (owners != null && optionalGuard != null) bytes=optionalGuard.apply(file.getFileName().toString(),bytes);
        if (owners != null && puzzlesGuard != null && file.equals(puzzles))
            bytes = puzzlesGuard.apply(bytes);
        if (owners != null && neoMods.contains(file))
            bytes = TypedListeners.adapt(bytes, this);
        return bytes;
    }
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if(invalidMixinClasses.contains(name)) throw new ClassNotFoundException("Mixin class may not be loaded directly: "+name);
        String key = name.replace('.', '/'), path = key + ".class";
        int dot = name.lastIndexOf('.');
        try {
            var owners = resources.get(path);
            JarFile jar = owners == null ? vanilla : owners.getFirst();
            var entry = jar.getJarEntry(path);
            URL source = owners == null ? origin : Path.of(jar.getName()).toUri().toURL();
            if (entry == null) {
                // Transformable libraries use the same verified, derived signer domain from their first load,
                // even before Mixin startup.
                if (mixin != null && super.findResource(path) == null && name.indexOf('$') >= 0) {
                    var outer = findLoadedClass(name.substring(0, name.indexOf('$')));
                    if (outer != null || name.startsWith("org.spongepowered.asm.synthetic.")) {
                        var generated = mixin.apply(name, null);
                        if (generated != null) {
                            var protection = outer == null ? getClass().getProtectionDomain()
                                                           : outer.getProtectionDomain();
                            return defineClass(name, generated, 0, generated.length, protection);
                        }
                    }
                }
                boolean adapterApi = mixin != null && key.startsWith("net/neoforged/neoforge/");
                if (reserved(key) && !adapterApi)
                    return super.findClass(name);
                var resource = super.findResource(path);
                if (resource == null || !(resource.openConnection() instanceof JarURLConnection conn) ||
                    !(gameLibraries.contains(conn.getJarFileURL()) ||
                      adapterApi && conn.getJarFileURL().equals(getURLs()[0])))
                    return super.findClass(name);
                source = conn.getJarFileURL();
                jar = library(source);
                entry = jar.getJarEntry(path);
            }
            // Read the original in full for signature verification. Runtime definitions use the original URL.
            byte[] bytes;
            try (var in = jar.getInputStream(entry)) {
                bytes = in.readAllBytes();
            }
            bytes = beforeMixins(name,bytes);
            if (mixin != null) {
                try {
                    bytes = mixin.apply(name, bytes);
                    if (fullFabricApi)
                        bytes = ContentPatches.routeLogin(this, key, bytes);
                } catch (RuntimeException | Error e) {
                    if (e instanceof VirtualMachineError || e instanceof ThreadDeath)
                        throw e;
                    ModDiagnostics.failure(mounted, "Mixin / " + name, e, mixinOwners);
                    throw e;
                }
            }
            if (dot > 0) {
                String pkg = name.substring(0, dot);
                synchronized (this) {
                    if (getDefinedPackage(pkg) == null) {
                        var mf = jar.getManifest();
                        if (mf == null)
                            definePackage(pkg, null, null, null, null, null, null, null);
                        else
                            definePackage(pkg, mf, source);
                    } else {
                        Package defined = getDefinedPackage(pkg);
                        if (defined.isSealed() && !defined.isSealed(source))
                            throw new SecurityException("Sealed package " + pkg);
                        var mf = jar.getManifest();
                        if (!defined.isSealed() && mf != null) {
                            var perPackage = mf.getAttributes(pkg.replace('.', '/') + "/");
                            String sealed =
                                perPackage == null ? null : perPackage.getValue(Attributes.Name.SEALED);
                            if (sealed == null)
                                sealed = mf.getMainAttributes().getValue(Attributes.Name.SEALED);
                            if (Boolean.parseBoolean(sealed))
                                throw new SecurityException("Cannot seal an existing package " + pkg);
                        }
                    }
                }
            }
            return defineClass(name, bytes, 0, bytes.length, new CodeSource(source, (CodeSigner[])null));
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ClassNotFoundException(name, e);
        }
    }
    @Override
    public synchronized void close() throws IOException {
        try (Closeable game = vanilla; Closeable urls = super::close; Closeable deps = this::closeLibraries;
             Closeable mods = this::releaseMods) {
            if (mixinStarted) {
                mixinStarted = false;
                mixin = null;
                try {
                    Class.forName("mc.unity.mixin.UnityMixinService", false, this)
                        .getMethod("finish")
                        .invoke(null);
                } catch (ReflectiveOperationException e) {
                    throw new IOException("Closing Mixin service", e);
                }
            }
        }
    }
}
