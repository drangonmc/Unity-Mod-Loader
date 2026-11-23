import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
/** Exercise production selection and preflight with local fixtures. No game launch is required. */
class NestedChecks {
    static byte[] archive(Map<String, byte[]> entries) throws IOException {
        var buf = new ByteArrayOutputStream();
        try (var out = new JarOutputStream(buf)) {
            for (var row : entries.entrySet()) {
                var e = new JarEntry(row.getKey());
                e.setTime(0);
                out.putNextEntry(e);
                out.write(row.getValue());
                out.closeEntry();
            }
        }
        return buf.toByteArray();
    }
    static byte[] bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    static byte[] lib(String contents) throws IOException {
        return archive(Map.of("qa/value", bytes(contents)));
    }
    static byte[] wrapper(String range, String version, String member, byte[] lib) throws IOException {
        String json =
            "{\"jars\":[{\"identifier\":{\"group\":\"qa\",\"artifact\":\"test\"},\"version\":{\"range\":\""
            + range + "\",\"artifactVersion\":\"" + version + "\"},\"path\":\"" + member + "\"}]}";
        var map = new HashMap<String, byte[]>();
        map.put("META-INF/jarjar/metadata.json", bytes(json));
        map.put(member, lib);
        return archive(map);
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath(),
             temp = Files.createDirectory(Path.of(args[1]).resolve("nested-negative"));
        Path dist = root.resolve("versions/0.2.2-Beta/unity-loader-0.2.2-Beta.jar"),
             cache = root.resolve("cache/0.2.2-Beta");
        var paths = new ArrayList<Path>(List.of(dist, cache.resolve("versions/26.2/server-26.2.jar")));
        try (var walk = Files.walk(cache.resolve("libraries"))) {
            paths.addAll(walk.filter(p -> p.toString().endsWith(".jar")).toList());
        }
        var urls = new ArrayList<URL>();
        for (var p : paths) urls.add(p.toUri().toURL());
        try (var cl = new URLClassLoader(urls.toArray(URL[] ::new), ClassLoader.getPlatformClassLoader())) {
            var api = cl.loadClass("mc.unity.adapter.common.NestedArchives");
            var resolve = api.getMethod("resolve", Path.class, boolean.class);
            for (var reason : List.of("conflict", "divergent", "path")) {
                var d = Files.createDirectory(temp.resolve(reason));
                Files.write(d.resolve("a.jar"),
                    wrapper("[1,2)", "1.2.0",
                        reason.equals("path") ? "../escape.jar" : "META-INF/jarjar/helper.jar", lib("one")));
                if (!reason.equals("path"))
                    Files.write(d.resolve("b.jar"),
                        wrapper(reason.equals("conflict") ? "[2,3)" : "[1,2)",
                            reason.equals("conflict") ? "2.0" : "1.2.0", "META-INF/jarjar/helper.jar",
                            lib("two")));
                try (var unused = (AutoCloseable) resolve.invoke(null, d, false)) {
                    throw new AssertionError("Accepted bad nested case " + reason);
                } catch (InvocationTargetException e) {
                    String msg = e.getCause().getMessage();
                    if (!msg.contains(switch (reason) {
                            case "path" -> "Invalid nested path";
                            case "conflict" -> "Incompatible nested";
                            default -> "Different bytes";
                        }))
                        throw e;
                }
            }
            Path good = Files.createDirectory(temp.resolve("good"));
            Files.write(good.resolve("a.jar"), wrapper("[1,2)", "1.0", "lib.jar", lib("one")));
            Files.write(good.resolve("b.jar"), wrapper("[1.1,2)", "1.4", "lib.jar", lib("two")));
            Path inner;
            try (var resolved = (AutoCloseable) resolve.invoke(null, good, false)) {
                var libs = (Set<?>) api.getMethod("plainLibraries").invoke(resolved);
                if (libs.size() != 1)
                    throw new AssertionError(libs);
                inner = (Path) libs.iterator().next();
                try (var z = new JarFile(inner.toFile())) {
                    try (var in = z.getInputStream(z.getEntry("qa/value"))) {
                        if (!new String(in.readAllBytes()).equals("two"))
                            throw new AssertionError("Wrong artifact chosen");
                    }
                }
            }
            if (Files.exists(inner) || Files.exists(inner.getParent()))
                throw new AssertionError("Staged files remain after close");
            var access = cl.loadClass("mc.unity.adapter.common.AccessTransforms")
                             .getMethod("prepare", Collection.class, ClassLoader.class, boolean.class);
            for (String text : List.of("public net.minecraft.core.BlockPos nonexistent",
                     "public+f net.minecraft.core.BlockPos\npublic-f net.minecraft.core.BlockPos")) {
                var f = Files.createTempFile(temp, "access", ".jar");
                Files.write(f, archive(Map.of("META-INF/accesstransformer.cfg", bytes(text))));
                try (var z = new JarFile(f.toFile())) {
                    try {
                        access.invoke(null, List.of(z), cl, false);
                        throw new AssertionError("Invalid access rule accepted");
                    } catch (InvocationTargetException e) {
                        if (!(e.getCause() instanceof IOException))
                            throw e;
                    }
                }
            }
            var family = cl.loadClass("mc.unity.adapter.forge.CancelBus");
            var rootBus = family.getConstructor(Class.class).newInstance(Object.class);
            var left = family.getMethod("child", Class.class).invoke(rootBus, String.class);
            var right = family.getMethod("child", Class.class).invoke(rootBus, Integer.class);
            var add = family.getMethod("addListener", java.util.function.Consumer.class);
            var has = family.getMethod("hasListeners");
            Object child = add.invoke(left, (java.util.function.Consumer<Object>) v -> {});
            if ((boolean) has.invoke(rootBus) || !(boolean) has.invoke(left) || (boolean) has.invoke(right))
                throw new AssertionError("Sibling listener leak");
            Object parent = add.invoke(rootBus, (java.util.function.Consumer<Object>) v -> {});
            if (!(boolean) has.invoke(right))
                throw new AssertionError("Parent listener invisible");
            Method remove = family.getMethod(
                "remove", cl.loadClass("net.minecraftforge.eventbus.api.listener.EventListener"));
            remove.invoke(null, parent);
            remove.invoke(null, child);
            if ((boolean) has.invoke(left) || (boolean) has.invoke(right))
                throw new AssertionError("Stale cached listeners");
        }
        System.out.println("PASS nested ranges, conflicting bytes, traversal rejection, closed archive "
                           + "cleanup, invalid access rules and type-specific listener snapshots");
    }
}
