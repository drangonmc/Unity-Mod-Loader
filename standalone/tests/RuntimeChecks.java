import static java.lang.constant.ConstantDescs.*;

import java.io.*;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
class RuntimeChecks {
    private static URLClassLoader testLoader(URL[] urls, byte[] stub) throws Exception {
        Path game = Path.of(urls[1].toURI());
        Class<?> type = Class.forName("mc.unity.bootstrap.GameLoader");
        var ctor = type.getDeclaredConstructor(URL[].class, Path.class);
        ctor.setAccessible(true);
        var loader = (URLClassLoader) ctor.newInstance(urls, game);
        if (stub == null) {
            var install =
                Class.forName("mc.unity.bootstrap.GamePatches").getDeclaredMethod("install", type, Path.class);
            install.setAccessible(true);
            install.invoke(null, loader, game);
        } else
            try (var jar = new JarFile(game.toFile(), true)) {
                String tab = "net/minecraft/world/item/CreativeModeTab";
                byte[] bytes;
                try (var input = jar.getInputStream(jar.getJarEntry(tab + ".class"))) {
                    bytes = input.readAllBytes();
                }
                var install = type.getDeclaredMethod("install", Map.class);
                install.setAccessible(true);
                install.invoke(loader, Map.of("net/minecraft/server/MinecraftServer", stub, tab, bytes));
            }
        return loader;
    }
    private static void dependencyChecks(Path temp, String cp, URL[] urls) throws Exception {
        var src = Files.createDirectory(temp.resolve("deps-src"));
        var mods = Files.createDirectories(temp.resolve("deps/mods"));
        var library = src.resolve("Library.java");
        var consumer = src.resolve("Consumer.java");
        Files.writeString(library,
            "package fixture; @net.minecraftforge.fml.common.Mod(\"alpha_lib\") public class Library { public "
                + "static int value; public Library(){value=7;} }");
        Files.writeString(consumer,
            "package fixture; @net.minecraftforge.fml.common.Mod(\"use_lib\") public class Consumer { public "
                + "Consumer(){if(Library.value!=7)throw new AssertionError(\"Library initialization order\");} "
                + "}");
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-cp", cp, "-d",
                src.toString(), library.toString(), consumer.toString())
            != 0)
            throw new AssertionError("Dependency fixture compile");
        for (String id : List.of("alpha_lib", "use_lib")) {
            boolean lib = id.equals("alpha_lib");
            String name = lib ? "Library" : "Consumer";
            try (var out = new JarOutputStream(
                     Files.newOutputStream(mods.resolve(lib ? "z-library.jar" : "a-consumer.jar")))) {
                out.putNextEntry(new JarEntry("fixture/" + name + ".class"));
                Files.copy(src.resolve("fixture/" + name + ".class"), out);
                out.closeEntry();
                out.putNextEntry(new JarEntry("META-INF/mods.toml"));
                String meta = "modLoader=\"javafml\"\nloaderVersion=\"[65,)\"\n[[mods]]\nmodId=\"" + id
                    + "\"\nversion=\"1.0\"\n";
                if (!lib)
                    meta += "[[dependencies.use_lib]]\nmodId=\"alpha_lib\"\nmandatory=true\nversionRange=\"[1,"
                        + "2)\"\nside=\"BOTH\"\n";
                out.write(meta.getBytes());
                out.closeEntry();
            }
        }
        try (var game = testLoader(urls, null)) {
            var adapter = game.loadClass("mc.unity.runtime.Platforms");
            var open = adapter.getMethod("open", Path.class, Collection.class);
            try (var loaded = (AutoCloseable) open.invoke(null, mods.getParent(), List.of())) {
                adapter.getMethod("initialize").invoke(loaded);
            }
            Files.move(mods.resolve("z-library.jar"), temp.resolve("away.jar"));
            try {
                open.invoke(null, mods.getParent(), List.of());
                throw new AssertionError("Missing declared library accepted");
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IOException)
                    || !e.getCause().getMessage().contains("Missing FORGE dependency"))
                    throw e;
            }
        }
        System.out.println(
            "PASS two Forge JARs share classes, constructors respect dependencies, absent library rejected");
    }
    private static void reviewChecks(URL[] urls, Path dir, Path probe) throws Exception {
        try (var game = testLoader(urls, null)) {
            var cf = ClassFile.of();
            var api = game.loadClass("mc.unity.adapter.common.PlatformAbi")
                          .getDeclaredMethod("check", ClassModel.class);
            api.setAccessible(true);
            var unknown =
                Annotation.of(ClassDesc.of("net.minecraftforge.eventbus.api.SubscribeEvent"), List.of());
            for (var bytes :
                List.of(cf.build(ClassDesc.of("fixture.Visible"),
                            b
                            -> b.with(
                                java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute.of(unknown))),
                    cf.build(ClassDesc.of("fixture.Invisible"),
                        b
                        -> b.withMethod("method", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC,
                            mb -> {
                                mb.with(java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute.of(
                                    unknown));
                                mb.withCode(cb -> cb.return_());
                            })),
                    cf.build(ClassDesc.of("fixture.Generic"),
                        b -> b.withField("types", ClassDesc.of("java.util.List"), fb -> {
                            fb.with(java.lang.classfile.attribute.SignatureAttribute.of(Signature.parseFrom(
                                "Ljava/util/List<Lnet/minecraftforge/notimplemented/Type;>;")));
                        })))) {
                try {
                    api.invoke(null, cf.parse(bytes));
                    throw new AssertionError("Annotation or generic type escaped preflight");
                } catch (InvocationTargetException fail) {
                    if (!(fail.getCause() instanceof IOException))
                        throw fail;
                }
            }
            var cmp = game.loadClass("mc.unity.adapter.common.FmlDescriptor")
                          .getMethod("compare", String.class, String.class);
            for (String[] test : List.of(new String[] {"1.2.0-beta.1", "1.2.0", "-1"},
                     new String[] {"26.2.0+forge", "26.2", "0"}, new String[] {"1.0.0-SNAPSHOT", "1.0", "-1"},
                     new String[] {"1.2.0-beta.10", "1.2.0-beta.2", "1"})) {
                int actual = (int) cmp.invoke(null, test[0], test[1]);
                if (Integer.signum(actual) != Integer.parseInt(test[2]))
                    throw new AssertionError(Arrays.toString(test));
            }
            var content = game.loadClass("mc.unity.core.Content");
            var forge = game.loadClass("mc.unity.runtime.Platforms");
            var build = game.loadClass("net.minecraftforge.event.BuildCreativeModeTabContentsEvent");
            var bus = build.getField("BUS").get(null);
            try (var opened = (AutoCloseable) forge.getMethod("open", Path.class, Collection.class)
                     .invoke(null, dir, List.of())) {
                content.getMethod("source", String.class, Path.class, boolean.class)
                    .invoke(null, "test", probe, true);
                bus.getClass()
                    .getMethod("addListener", java.util.function.Consumer.class)
                    .invoke(bus, (java.util.function.Consumer<Object>) v -> {
                        throw new AssertionError("Listener survived close");
                    });
                for (String n : List.of("frozen", "nextColumn", "digest")) {
                    var f = content.getDeclaredField(n);
                    f.setAccessible(true);
                    f.set(null,
                        n.equals("frozen")           ? Boolean.TRUE
                            : n.equals("nextColumn") ? Integer.valueOf(25)
                                                     : "old");
                }
            }
            bus.getClass().getMethod("dispatch", Object.class).invoke(bus, (Object) null);
            content.getMethod("checkOpen").invoke(null);
            if (!content.getMethod("digest").invoke(null).equals(""))
                throw new AssertionError("Stale digest");
            for (String n : List.of("groups", "sources", "packs", "tabOrder", "requestedOrder", "nextColumn")) {
                var f = content.getDeclaredField(n);
                f.setAccessible(true);
                Object value = f.get(null);
                if(value instanceof Map<?,?> m&&!m.isEmpty() || value instanceof Collection<?> c&&!c.isEmpty() || value instanceof Integer i&&i!=7)
                    throw new AssertionError(n);
            }
            Path invalid = dir.resolve("mods/invalid.jar");
            try (var out = new JarOutputStream(Files.newOutputStream(invalid))) {
                out.putNextEntry(new JarEntry("test.txt"));
                out.closeEntry();
            }
            try {
                game.loadClass("mc.unity.runtime.ClientHooks")
                    .getMethod("initialize", Path.class)
                    .invoke(null, dir);
                throw new AssertionError("Invalid client accepted");
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IOException))
                    throw e;
            }
            var moved = Files.move(probe, probe.resolveSibling("released.tmp"));
            Files.move(moved, probe);
        }
        System.out.println("PASS visible and invisible annotations, generic signatures, qualified versions, "
            + "content reset, creative listeners and client rollback");
    }
    private static void v9Checks(URL[] urls, Path root, Path temp) throws Exception {
        var cf = ClassFile.of();
        try (var game = testLoader(urls, null)) {
            var predicate = game.loadClass("mc.unity.adapter.fabric.FabricVersions")
                                .getMethod("check", String.class, String.class);
            for (var ok : List.of(new String[] {"26.2", "26.1 || 26.2"}, new String[] {"26.2", ">=26.1 <26.3"},
                     new String[] {"1.2.0", "~1"}, new String[] {"0.19.99", "^0.19.3"},
                     new String[] {"0.0.3", "^0.0.3"}, new String[] {"1.2.8", "~1.2.0"},
                     new String[] {"0.19.3", "^0.19.0"}))
                predicate.invoke(null, (Object[]) ok);
            for (var bad : List.of(new String[] {"26.3", ">=26.1 <26.3"}, new String[] {"2.0.0", "~1"},
                     new String[] {"0.99.0", "^0.19.3"}, new String[] {"0.20.0", "^0.19.3"},
                     new String[] {"0.0.4", "^0.0.3"}, new String[] {"1.3", "~1.2"},
                     new String[] {"2", "^1.2"})) {
                try {
                    predicate.invoke(null, (Object[]) bad);
                    throw new AssertionError(Arrays.toString(bad));
                } catch (InvocationTargetException e) {
                    if (!(e.getCause() instanceof IOException))
                        throw e;
                }
            }
            var pending = game.loadClass("mc.unity.core.LoginSync$Pending");
            var ctor = pending.getDeclaredConstructor(Runnable.class);
            ctor.setAccessible(true);
            var once = pending.getDeclaredMethod("consume");
            once.setAccessible(true);
            Object state = ctor.newInstance((Runnable) () -> {});
            var win = new java.util.concurrent.atomic.AtomicInteger();
            try (var threads = java.util.concurrent.Executors.newFixedThreadPool(8)) {
                var calls = new ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 64; i++)
                    calls.add(threads.submit(() -> {
                        if ((boolean) once.invoke(state))
                            win.incrementAndGet();
                        return null;
                    }));
                for (var f : calls) f.get();
            }
            if (win.get() != 1)
                throw new AssertionError("Login response consumed " + win.get() + " times");
            var props = game.loadClass("mc.unity.mixin.PropertiesService");
            var service = props.getConstructor().newInstance();
            var keyType = game.loadClass("org.spongepowered.asm.service.IPropertyKey");
            var key = props.getMethod("resolveKey", String.class).invoke(service, "fixture");
            props.getMethod("setProperty", keyType, Object.class).invoke(service, key, "prior");
            props.getMethod("clear").invoke(service);
            if (props.getMethod("getProperty", keyType).invoke(service, key) != null)
                throw new AssertionError("Mixin globals survived close");
            var net = game.loadClass("mc.unity.core.PlayNetwork");
            var view = net.getDeclaredField("entries");
            view.setAccessible(true);
            var check = net.getMethod("manifest");
            var clear = net.getMethod("clear");
            var add = Arrays.stream(net.getMethods())
                          .filter(m -> m.getName().equals("add"))
                          .findFirst()
                          .orElseThrow();
            var identifier = game.loadClass("net.minecraft.resources.Identifier");
            var id = identifier.getMethod("parse", String.class).invoke(null, "test:publication");
            var type = game.loadClass("net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type")
                           .getConstructor(identifier)
                           .newInstance(id);
            var codec = game.loadClass("net.minecraft.network.codec.StreamCodec");
            Object dummy = java.lang.reflect.Proxy.newProxyInstance(game, new Class[] {codec},
                (proxy, m, args) -> { throw new AssertionError("Codec must not execute"); });
            add.invoke(null, id, 1, null, type, dummy,
                (java.util.function.BiConsumer<Object, Object>) (x, y) -> {}, true);
            if(!((Map<?,?>)view.get(null)).isEmpty())
                throw new AssertionError("Unfrozen registrations published");
            check.invoke(null);Map<?,?> snapshot=(Map<?,?>)view.get(null);
            if (snapshot.size() != 1 || !Modifier.isVolatile(view.getModifiers()))
                throw new AssertionError("Missing frozen publication");
            clear.invoke(null);if(snapshot.size()!=1||!((Map<?,?>)view.get(null)).isEmpty())
                throw new AssertionError("Reader snapshot mutated on close");
        }
        Path repeated = Files.createDirectory(temp.resolve("resource-cache"));
        Path folder = Files.createDirectory(repeated.resolve("mods"));
        Path archive = folder.resolve("resource.jar");
        try (var z = new JarOutputStream(Files.newOutputStream(archive))) {
            z.putNextEntry(new JarEntry("qa/tiny.json"));
            z.write("resource".getBytes());
            z.closeEntry();
        }
        try (var game = testLoader(urls, null)) {
            var api = game.getClass().getMethod("archives", Path.class, boolean.class);
            var initial=(Map<?,?>)api.invoke(game,repeated,false);
            Object jar = initial.values().iterator().next();
            for (int i = 0; i < 1000; i++) {
                try (var input = game.getResourceAsStream("qa/tiny.json")) {
                    if (!"resource".equals(new String(input.readAllBytes())))
                        throw new AssertionError("Resource read");
                }
            }
            if(((Map<?,?>)api.invoke(game,repeated,false)).values().iterator().next()!=jar)
                throw new AssertionError("Reopened mod archive");
        }
        Files.move(archive, folder.resolve("released.zip"));
        System.out.println(
            "PASS 1000 cached resource reads in one mod archive session, handle released on close");
        // Each failure occurs in the normal loader before any sample entrypoint is invoked.
        for (String scenario :
            List.of("duplicate", "fabric-abi", "neo-abi", "reserved-fabric", "reserved-neo")) {
            Path dir = Files.createDirectory(temp.resolve(scenario)),
                 mods = Files.createDirectory(dir.resolve("mods"));
            String suffix = scenario.equals("reserved-fabric") ? "net.fabricmc.fixture.Reserved"
                : scenario.equals("reserved-neo")              ? "net.neoforged.fixture.Reserved"
                                                               : "fixture.Shared";
            String bad = scenario.equals("fabric-abi") ? "net.fabricmc.unimplemented.Missing"
                : scenario.equals("neo-abi")           ? "net.neoforged.unimplemented.Missing"
                                                       : "java.lang.String";
            byte[] cl = cf.build(
                ClassDesc.of(suffix), b -> b.withField("typed", ClassDesc.of(bad), ClassFile.ACC_PUBLIC));
            for (String platform : scenario.equals("duplicate")
                    ? List.of("fabric", "neo")
                    : List.of(scenario.startsWith("neo") ? "neo" : "fabric")) {
                Path sample = root.resolve("standalone/tests/platform/"
                    + (platform.equals("neo") ? "unity_neo_sample" : "unity_fabric_sample") + ".jar");
                try (var z = new JarFile(sample.toFile());
                    var out = new JarOutputStream(Files.newOutputStream(mods.resolve(platform + ".jar")))) {
                    for (var entry : Collections.list(z.entries())) {
                        out.putNextEntry(new JarEntry(entry.getName()));
                        try (var in = z.getInputStream(entry)) {
                            in.transferTo(out);
                        }
                        out.closeEntry();
                    }
                    out.putNextEntry(new JarEntry(suffix.replace('.', '/') + ".class"));
                    out.write(cl);
                    out.closeEntry();
                }
            }
            try (var game = testLoader(urls, null)) {
                var open = game.loadClass("mc.unity.runtime.Platforms")
                               .getMethod("open", Path.class, Collection.class);
                try {
                    open.invoke(null, dir, List.of());
                    throw new AssertionError("Accepted " + scenario);
                } catch (InvocationTargetException e) {
                    var cause = e.getCause();
                    if (!(cause instanceof IOException)
                        || !(cause.getMessage().contains(scenario.contains("abi")
                                ? "Unsupported platform type"
                                : "Duplicate or reserved mod class")))
                        throw e;
                }
            }
        }
        System.out.println(
            "PASS shared-domain duplicate and reserved packages, Fabric and NeoForge ABI preflight, Fabric "
            + "constraints, immutable network snapshot, one-shot login consumption and Mixin properties");
    }
    private static void signedLibrary(URL[] urls, Path temp) throws Exception {
        var cf = ClassFile.of();
        Path lib = temp.resolve("signed.jar"), keys = temp.resolve("fixture.p12");
        try (var z = new JarOutputStream(Files.newOutputStream(lib))) {
            for (String type : List.of("Before", "After")) {
                var cl = cf.build(ClassDesc.of("signedfixture." + type),
                    b
                    -> b.withMethodBody("value", MethodTypeDesc.of(CD_int),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, c -> c.iconst_1().ireturn()));
                z.putNextEntry(new JarEntry("signedfixture/" + type + ".class"));
                z.write(cl);
                z.closeEntry();
            }
        }
        for (var args : List.of(List.of("keytool", "-genkeypair", "-alias", "fixture", "-keyalg", "RSA",
                                    "-keystore", keys.toString(), "-storepass", "fixture-only", "-keypass",
                                    "fixture-only", "-dname", "CN=Unity temporary test", "-validity", "1"),
                 List.of("jarsigner", "-keystore", keys.toString(), "-storepass", "fixture-only",
                     lib.toString(), "fixture"))) {
            var proc = new ProcessBuilder(args).redirectErrorStream(true).start();
            var message = proc.getInputStream().readAllBytes();
            if (proc.waitFor() != 0)
                throw new IOException(new String(message));
        }
        try (var z = new JarFile(lib.toFile(), true)) {
            for (String name : List.of("Before", "After")) {
                var e = z.getJarEntry("signedfixture/" + name + ".class");
                try (var in = z.getInputStream(e)) {
                    in.readAllBytes();
                }
                if (e.getCodeSigners() == null)
                    throw new AssertionError("Fixture unsigned");
            }
        }
        URL[] cp = Arrays.copyOf(urls, urls.length + 1);
        cp[urls.length] = lib.toUri().toURL();
        Path mods = Files.createDirectories(temp.resolve("signed-session/mods"));
        Path source = temp.resolve("SignedMixin.java");
        Files.writeString(source, """
            package fixture.mix;
            import org.spongepowered.asm.mixin.Mixin;
            import org.spongepowered.asm.mixin.injection.*;
            import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
            @Mixin(targets="signedfixture.After",remap=false)
            public abstract class SignedMixin {
                @Inject(method="value",at=@At("HEAD"),cancellable=true,remap=false)
                private static void replace(CallbackInfoReturnable<Integer> result){result.setReturnValue(2);}
            }
            """);
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-classpath",
                Path.of(urls[0].toURI()).toString(), "-d", temp.toString(), source.toString())
            != 0)
            throw new AssertionError("Mixin fixture compilation");
        var manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("MixinConfigs", "signed.mixins.json");
        try (var out = new JarOutputStream(Files.newOutputStream(mods.resolve("testmod.jar")), manifest)) {
            out.putNextEntry(new JarEntry("signed.mixins.json"));
            out.write(("{\"required\":true,\"minVersion\":\"0.8\",\"compatibilityLevel\":\"JAVA_21\","
                       + "\"package\":\"fixture.mix\",\"mixins\":[\"SignedMixin\"]}").getBytes(
                          java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("fixture/mix/SignedMixin.class"));
            Files.copy(temp.resolve("fixture/mix/SignedMixin.class"), out);
            out.closeEntry();
        }
        var previous = Thread.currentThread().getContextClassLoader();
        try (var loader = testLoader(cp, null)) {
            Thread.currentThread().setContextClassLoader(loader);
            var before = Class.forName("signedfixture.Before", true, loader);
            var start = loader.getClass().getDeclaredMethod("mixins", Path.class, boolean.class);
            start.setAccessible(true);
            start.invoke(loader, mods.getParent(), false);
            var after = Class.forName("signedfixture.After", true, loader);
            if ((int) before.getMethod("value").invoke(null) != 1
                || (int) after.getMethod("value").invoke(null) != 2
                || before.getProtectionDomain().getCodeSource().getCodeSigners() != null
                || after.getProtectionDomain().getCodeSource().getCodeSigners() != null)
                throw new AssertionError("Signed input and derived runtime attribution");
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        System.out.println(
            "PASS signed vendor JAR across Sponge Mixin activation and exact early-version ranges");
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath(), temp = Path.of(args[1]).toRealPath();
        var core = (args.length>2 ? Path.of(args[2]) : root.resolve("versions/0.2.5-Beta/unity-loader-0.2.5-Beta.jar"));
        Path runtime = (args.length>3 ? Path.of(args[3]) : root.resolve("cache/0.2.5-Beta"));
        var files = new ArrayList<Path>(List.of(core, runtime.resolve("versions/26.2/server-26.2.jar")));
        try (var paths = Files.walk(runtime.resolve("libraries"))) {
            files.addAll(paths.filter(p -> p.toString().endsWith(".jar")).toList());
        }
        var urls = files.stream()
                       .map(p -> {
                           try {
                               return p.toUri().toURL();
                           } catch (Exception e) {
                               throw new IllegalArgumentException(e);
                           }
                       })
                       .toArray(URL[] ::new);
        var cf = ClassFile.of();
        String name = "net.minecraft.server.MinecraftServer";
        var server = ClassDesc.of(name);
        var empty = MethodTypeDesc.of(CD_void);
        var eventLoop=ClassDesc.of("net.minecraft.util.thread.ReentrantBlockableEventLoop");
        var stub = cf.build(server, b -> {
            // Match the verified 26.2 server hierarchy even for code reached during shutdown.
            b.withSuperclass(eventLoop);
            b.withInterfaceSymbols(ClassDesc.of("net.minecraft.commands.CommandSource"),ClassDesc.of("net.minecraft.server.ServerInfo"),
                                   ClassDesc.of("net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter"));
            b.withField("status", CD_int, 0);
            b.withMethodBody("<init>", empty, ClassFile.ACC_PUBLIC,
                c -> c.aload(0).ldc("Unity fixture").iconst_0().invokespecial(eventLoop, "<init>", MethodTypeDesc.of(CD_void,CD_String,CD_boolean)).return_());
            b.withMethodBody("runServer", empty, ClassFile.ACC_PUBLIC,
                c -> c.aload(0).iconst_1().putfield(server, "status", CD_int).return_());
            b.withMethodBody("tickServer",
                MethodTypeDesc.of(CD_void, ClassDesc.of("java.util.function.BooleanSupplier")),
                ClassFile.ACC_PUBLIC, c -> c.return_());
            var failure = ClassDesc.of("java.lang.IllegalStateException");
            b.withMethodBody("stopServer", empty, ClassFile.ACC_PUBLIC,
                c -> c.new_(failure).dup().invokespecial(failure, "<init>", empty).athrow());
        });
        byte[] patched;
        try (var toolkit = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var transform = toolkit.loadClass("mc.unity.bootstrap.GamePatches")
                                .getDeclaredMethod("server", ClassFile.class, ClassModel.class);
            transform.setAccessible(true);
            patched = (byte[]) transform.invoke(null, cf, cf.parse(stub));
            var check = toolkit.loadClass("mc.unity.adapter.common.PlatformAbi")
                            .getDeclaredMethod("check", ClassModel.class);
            check.setAccessible(true);
            var text = cf.build(ClassDesc.of("fixture.Text"),
                b
                -> b.withMethodBody("log", MethodTypeDesc.of(CD_String), ClassFile.ACC_STATIC,
                    c -> c.ldc("net/minecraftforge/unavailable/Feature").areturn()));
            check.invoke(null, cf.parse(text));
            var invalid = cf.build(ClassDesc.of("fixture.Bad"),
                b
                -> b.withField("dependency", ClassDesc.of("net.minecraftforge.unavailable.Feature"),
                    ClassFile.ACC_PUBLIC));
            try {
                check.invoke(null, cf.parse(invalid));
                throw new AssertionError("Unknown typed reference accepted");
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IOException))
                    throw e;
            }
        }
        String cp = String.join(File.pathSeparator, files.stream().map(Path::toString).toList());
        Path handlers = Files.createDirectory(temp.resolve("priority-fixture"));
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-classpath", cp, "-d",
                handlers.toString(), root.resolve("standalone/tests/BusPriorityChecks.java").toString())
            != 0)
            throw new AssertionError("Priority fixture compilation");
        try (var game = testLoader(urls, null);
            var cases = new URLClassLoader(new URL[] {handlers.toUri().toURL()}, game)) {
            cases.loadClass("fixture.BusPriorityChecks").getMethod("verify").invoke(null);
        }
        signedLibrary(urls, temp);
        v9Checks(urls, root, temp);
        dependencyChecks(temp, cp, urls);
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-cp", cp, "-d",
                temp.toString(), root.resolve("standalone/tests/LifecycleProbe.java").toString())
            != 0)
            throw new AssertionError("Probe compilation");
        var mods = Files.createDirectories(temp.resolve("unity-mods"));
        var jar = mods.resolve("probe.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("fixture/LifecycleProbe.class"));
            Files.copy(temp.resolve("fixture/LifecycleProbe.class"), out);
            out.closeEntry();
            out.putNextEntry(new JarEntry("META-INF/unity.properties"));
            out.write("api=1\nid=fixture\nentrypoint=fixture.LifecycleProbe\n".getBytes());
            out.closeEntry();
        }
        try (var game = testLoader(urls, patched)) {
            var discover =
                game.loadClass("mc.unity.runtime.ServerHooks").getDeclaredMethod("discover", Path.class);
            discover.setAccessible(true);
            var klass = game.loadClass(name);
            for (int i = 0; i < 2; i++) {
                discover.invoke(null, temp);
                var instance = klass.getConstructor().newInstance();
                klass.getMethod("runServer").invoke(instance);
                klass.getMethod("tickServer", java.util.function.BooleanSupplier.class)
                    .invoke(instance, (Object) null);
                try {
                    klass.getMethod("stopServer").invoke(instance);
                    throw new AssertionError("Original stop failure was swallowed");
                } catch (InvocationTargetException e) {
                    if (!(e.getCause() instanceof IllegalStateException))
                        throw e;
                }
                var moved = Files.move(jar, mods.resolve("closed.tmp"));
                Files.move(moved, jar);
            }
        }
        if (!Files.readString(temp.resolve("unity-data/fixture/events"))
                .equals("open\nstart\ntick\nstop\n".repeat(2)))
            throw new AssertionError("Second lifecycle failed");
        reviewChecks(urls, temp, jar);
        System.out.println("PASS two server lifecycles in one JVM, exceptional stop closes the mod JAR, text "
            + "literal accepted and missing typed reference rejected");
    }
}
