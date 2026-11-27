package mc.unity.bootstrap;

import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public final class Main {
    public static final String VERSION = "0.2.8-Beta";
    public static void main(String[] args) throws Throwable {
        if (args.length > 0 && args[0].equals("--client-home")) {
            if (args.length < 2)
                throw new IllegalArgumentException("Expected an installed vanilla launcher root");
            ClientEntry.launch(Path.of(args[1]), Arrays.copyOfRange(args, 2, args.length));
            return;
        }
        if (args.length == 2 && args[0].equals("--install-client")) {
            ClientEntry.installProfile(Path.of(args[1]));
            return;
        }
        Path cache = Path.of(".unity-runtime", "26.2"), bundle = null;
        boolean prepareOnly = false;
        var gameArgs = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server-jar" -> bundle = Path.of(args[++i]);
                case "--runtime-dir" -> cache = Path.of(args[++i]);
                case "--prepare-only" -> prepareOnly = true;
                default -> gameArgs.add(args[i]);
            }
        }
        var runtime = VanillaBundle.prepare(cache, bundle);
        if (prepareOnly)
            return;
        var urls = new ArrayList<URL>();
        urls.add(
            Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toUri().toURL());
        urls.add(runtime.game().toUri().toURL());
        for (Path lib : runtime.libraries()) urls.add(lib.toUri().toURL());
        var loader = new GameLoader(urls.toArray(URL[] ::new), runtime.game());
        var previous = Thread.currentThread().getContextClassLoader();
        try (loader) {
            Thread.currentThread().setContextClassLoader(loader);
            GamePatches.install(loader, runtime.game());
            loader.mixins(Path.of(".").toAbsolutePath().normalize(), false);
            System.out.println("[Unity] " + VERSION
                + " standalone | Minecraft 26.2 | Mojang libraries: " + runtime.libraries().size());
            try {
                Class.forName("mc.unity.runtime.Dedicated", true, loader)
                    .getMethod("launch", String[].class)
                    .invoke(null, (Object) gameArgs.toArray(String[] ::new));
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
