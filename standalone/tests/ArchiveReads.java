import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
/** A warm-cache microbenchmark of one fixed resource from one unmodified Mixin mod. */
class ArchiveReads {
    public static void main(String[] args) throws Exception {
        Path loader = Path.of(args[0]).toRealPath(), cache = Path.of(args[1]).toRealPath(),
             game = Path.of(args[2]).toRealPath();
        Path vanilla = cache.resolve("versions/26.2/server-26.2.jar");
        var urls = new ArrayList<URL>();
        urls.add(loader.toUri().toURL());
        urls.add(vanilla.toUri().toURL());
        try (var libs = Files.walk(cache.resolve("libraries"))) {
            for (Path f : libs.filter(i -> i.toString().endsWith(".jar")).sorted().toList())
                urls.add(f.toUri().toURL());
        }
        var cl = Class.forName("mc.unity.bootstrap.GameLoader");
        var ctor = cl.getDeclaredConstructor(URL[].class, Path.class);
        ctor.setAccessible(true);
        String resource = "laserbladetools.mixins.json";
        try (var domain = (URLClassLoader) ctor.newInstance(urls.toArray(URL[] ::new), vanilla)) {
            Thread.currentThread().setContextClassLoader(domain);
            var prepare = cl.getDeclaredMethod("mixins", Path.class, boolean.class);
            prepare.setAccessible(true);
            prepare.invoke(domain, game, false);
            byte[] expected;
            try (var jar = new JarFile(Path.of(args[3]).toFile(), true);
                var in = jar.getInputStream(jar.getJarEntry(resource))) {
                expected = in.readAllBytes();
            }
            for (int batch = -2; batch < 7; batch++) {
                long start = System.nanoTime();
                for (int i = 0; i < 500; i++) try (var input = domain.getResourceAsStream(resource)) {
                        if (input == null || !Arrays.equals(input.readAllBytes(), expected))
                            throw new IOException("Resource bytes differ");
                    }
                if (batch >= 0)
                    System.out.println("UNITY_READ_NS " + (System.nanoTime() - start));
            }
            System.out.println("UNITY_RESOURCE "
                + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected)));
        }
    }
}
