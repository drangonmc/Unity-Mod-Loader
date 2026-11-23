import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import mc.unity.adapter.common.AccessTransforms;
import mc.unity.adapter.common.NestedArchives;
import mc.unity.adapter.neoforge.NeoBus;
import net.neoforged.bus.api.*;
class BetaChecks {
    public static class Parent extends Event implements ICancellableEvent {}
    public static class Child extends Parent {}
    static void check(boolean ok, String reason) {
        if (!ok)
            throw new AssertionError(reason);
    }
    static void listeners() {
        var bus = new NeoBus("qa");
        var order = new StringBuilder();
        bus.addListener(EventPriority.HIGH, false, Parent.class, e -> {
            order.append("P");
            e.setCanceled(true);
        });
        bus.addListener(EventPriority.NORMAL, false, Child.class, e -> order.append("X"));
        bus.addListener(EventPriority.LOW, true, Child.class, e -> order.append("C"));
        bus.post(new Child());
        check(order.toString().equals("PC"), "Polymorphic order / cancellation " + order);
        NeoBus.clear();
        bus.post(new Child());
        check(order.toString().equals("PC"), "Session cleanup");
    }
    static byte[] jar(Map<String, byte[]> entries) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var z = new JarOutputStream(out)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new JarEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }
    static byte[] mod(boolean neo, String version) throws IOException {
        String descriptor =
            "modLoader=\"javafml\"\nloaderVersion=\"*\"\n[[mods]]\nmodId=\"samplelib\"\nversion=\"" + version +
            "\"\n";
        return jar(Map.of(neo ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml", descriptor.getBytes()));
    }
    static void nested(Path temp, boolean neo) throws Exception {
        Path d = Files.createDirectory(temp.resolve(neo ? "neo" : "forge"));
        var key = "META-INF/jarjar/library.jar";
        String metadata =
            "{\"jars\":[{\"identifier\":{\"group\":\"vendor\",\"artifact\":\"different-artifact-name\"}," +
            "\"version\":{\"range\":\"[1,2)\",\"artifactVersion\":\"1.1\"},\"path\":\"" +
            key + "\"}]}";
        Files.write(d.resolve("parent.jar"),
                    jar(Map.of(key, mod(neo, "1.1"), "META-INF/jarjar/metadata.json", metadata.getBytes())));
        Files.write(d.resolve("lib.jar"), mod(neo, "1.4"));
        try (var result = NestedArchives.resolve(d, false)) {
            check(result.files().size() == 2 && result.files().contains(d.resolve("lib.jar")),
                  "top-level precedence");
        }
        Files.write(d.resolve("lib.jar"), mod(neo, "3.0"));
        try (var ignored = NestedArchives.resolve(d, false)) {
            throw new AssertionError("incompatible override accepted");
        } catch (IOException expected) {
            check(expected.getMessage().contains("Incompatible"), expected.toString());
        }
    }
    static final class FailingFileSystem extends FileSystem {
        int closes;
        public void close() throws IOException {
            closes++;
            throw new IOException("test close");
        }
        public java.nio.file.spi.FileSystemProvider provider() { throw new UnsupportedOperationException(); }
        public boolean isOpen() { return closes == 0; }
        public boolean isReadOnly() { return true; }
        public String getSeparator() { return "/"; }
        public Iterable<Path> getRootDirectories() { return List.of(); }
        public Iterable<FileStore> getFileStores() { return List.of(); }
        public Set<String> supportedFileAttributeViews() { return Set.of(); }
        public Path getPath(String first, String... rest) { throw new UnsupportedOperationException(); }
        public PathMatcher getPathMatcher(String s) { throw new UnsupportedOperationException(); }
        public java.nio.file.attribute.UserPrincipalLookupService getUserPrincipalLookupService() {
            throw new UnsupportedOperationException();
        }
        public WatchService newWatchService() { throw new UnsupportedOperationException(); }
    }
    static void closeFailures(Path dir) throws Exception {
        var registry = mc.unity.adapter.fabric.LoaderView.INSTANCE;
        registry.bind(dir, false, List.of());
        var mods = registry.getClass().getDeclaredField("mods");
        mods.setAccessible(true);
        var views=((Map<?,?>)mods.get(registry)).values().iterator();
        var a = views.next();
        var b = views.next();
        var fs = a.getClass().getDeclaredField("fs");
        fs.setAccessible(true);
        var one = new FailingFileSystem();
        var two = new FailingFileSystem();
        fs.set(a, one);
        fs.set(b, two);
        try {
            registry.close();
            throw new AssertionError("Expected both close errors");
        } catch (IOException failed) {
            check(failed.getSuppressed().length == 1, "lost second close exception");
        }
        check(one.closes==1 && two.closes==1 && ((Map<?,?>)mods.get(registry)).isEmpty(),"leaked Fabric views");
    }
    static void frameAndAccess(Path dir) throws Exception {
        String source = """
            package synthetic;
            import java.util.function.Consumer;
            import net.neoforged.bus.api.Event;
            class Base { public int field; public int value() {return 7;} }
            class One extends Base {} class Two extends Base {}
            public class Branch {
                public static int run(boolean b) {
                    Base base = b ? new One() : new Two();
                    Consumer<Event> event = e -> {};
                    Consumer<String> simple = s -> {};
                    simple.accept("ok"); return base.value();
                }
            }
            """;
        var file = dir.resolve("Branch.java");
        Files.writeString(file, source);
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-classpath",
                                                       System.getProperty("java.class.path"), "-d",
                                                       dir.toString(), file.toString()) == 0,
              "compile regression input");
        var cls = dir.resolve("synthetic/Branch.class");
        var m = Class.forName("mc.unity.bootstrap.TypedListeners")
                    .getDeclaredMethod("adapt", byte[].class, ClassLoader.class);
        m.setAccessible(true);
        try (var loader =
                 new URLClassLoader(new URL[] {dir.toUri().toURL()}, BetaChecks.class.getClassLoader())) {
            var bytes = (byte[])m.invoke(null, Files.readAllBytes(cls), loader);
            var model = ClassFile.of().parse(bytes);
            long count = model.methods()
                             .stream()
                             .flatMap(x -> x.code().stream())
                             .flatMap(x -> x.elementStream())
                             .filter(x -> x instanceof InvokeInstruction i && i.name().equalsString("capture"))
                             .count();
            check(count == 1, "only event consumer should be instrumented");
            Files.write(cls, bytes);
            var run = loader.loadClass("synthetic.Branch").getMethod("run", boolean.class);
            check(run.invoke(null, true).equals(7) && run.invoke(null, false).equals(7),
                  "mod-only common superclass");
            var cfg = dir.resolve("rules.jar");
            Files.write(
                cfg,
                jar(Map.of("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nFMLAT:  \r\n\r\n".getBytes(),
                           "META-INF/accesstransformer.cfg",
                           "protected synthetic.Base field\ndefault synthetic.Base value()I\n".getBytes())));
            try (var z = new JarFile(cfg.toFile())) {
                var adjusted = ClassFile.of().parse(
                    AccessTransforms.prepare(List.of(z), loader, false).get("synthetic/Base"));
                check(adjusted.fields().getFirst().flags().has(java.lang.reflect.AccessFlag.PUBLIC),
                      "AT demoted field");
                check(adjusted.methods()
                          .stream()
                          .filter(x -> x.methodName().equalsString("value"))
                          .allMatch(x -> x.flags().has(java.lang.reflect.AccessFlag.PUBLIC)),
                      "AT demoted method");
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path d = Path.of(args[0]);
        listeners();
        closeFailures(d);
        nested(d, false);
        nested(d, true);
        frameAndAccess(d);
        System.out.println(
            "PASS Beta: hierarchy, consumer filter, AT visibility and empty FMLAT, Forge and NeoForge " +
            "top-level JarJar precedence and incompatible ranges, inherited event priority");
    }
}
