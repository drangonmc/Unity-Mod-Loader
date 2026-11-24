package qa.fabric;

import java.nio.file.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.Manifest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class FullFabricProbe implements ModInitializer {
    static final Set<String> targets = new TreeSet<>();
    static void verifyTransforms() {
        for (var target : targets)
            try {
                Class.forName(target, false, FullFabricProbe.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                throw new IllegalStateException("Original Fabric Mixin target " + target, e);
            }
        System.out.println("QA23 FULL TRANSFORMS targets=" + targets.size());
    }
    private static void readTargets(Path root, boolean client) throws IOException {
        var meta = com.google.gson.JsonParser.parseString(Files.readString(root.resolve("fabric.mod.json")))
                       .getAsJsonObject();
        if (!meta.has("mixins"))
            return;
        for (var row : meta.getAsJsonArray("mixins")) {
            String config;
            if (row.isJsonPrimitive())
                config = row.getAsString();
            else {
                var obj = row.getAsJsonObject();
                String side = obj.has("environment") ? obj.get("environment").getAsString() : "*";
                if (!side.equals("*") && !side.equals(client ? "client" : "server"))
                    continue;
                config = obj.get("config").getAsString();
            }
            var doc = com.google.gson.JsonParser.parseString(Files.readString(root.resolve(config)))
                          .getAsJsonObject();
            for (var phase : List.of("mixins", client ? "client" : "server")) {
                if (!doc.has(phase))
                    continue;
                for (var value : doc.getAsJsonArray(phase)) {
                    String cls = doc.get("package").getAsString() + "." + value.getAsString();
                    var parsed = java.lang.classfile.ClassFile.of().parse(
                        Files.readAllBytes(root.resolve(cls.replace('.', '/') + ".class")));
                    var anns = new ArrayList<java.lang.classfile.Annotation>();
                    parsed.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                        .ifPresent(a -> anns.addAll(a.annotations()));
                    parsed.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations())
                        .ifPresent(a -> anns.addAll(a.annotations()));
                    for (var ann : anns)
                        if (ann.className().equalsString("Lorg/spongepowered/asm/mixin/Mixin;"))
                            for (var arg : ann.elements())
                                if (arg.name().equalsString("value") || arg.name().equalsString("targets"))
                                    for (var t : ((java.lang.classfile.AnnotationValue.OfArray)arg.value())
                                                     .values()) {
                                        String n = t instanceof java.lang.classfile.AnnotationValue.OfClass c
                                                       ? c.classSymbol().descriptorString()
                                                       : ((java.lang.classfile.AnnotationValue.OfString)t)
                                                             .stringValue();
                                        if (n.startsWith("L") && n.endsWith(";"))
                                            n = n.substring(1, n.length() - 1);
                                        n = n.replace('/', '.');
                                        if (n.startsWith("net.minecraft.") || n.startsWith("com.mojang."))
                                            targets.add(n);
                                    }
                }
            }
        }
    }
    public void onInitialize() {
        FabricPlayProbe.bind();
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registries,
             side) -> dispatcher.register(Commands.literal("qa_full_api").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal("QA23 FULL API COMMAND"), false);
                return 1;
            })));
        inspect();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
            server -> verifyTransforms());
    }
    private static void inspect() {
        var loader = FabricLoader.getInstance();
        boolean client = loader.getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT;
        var classes = FullFabricProbe.class.getClassLoader();
        int total = 0, modules = 0;
        try (var input = new BufferedReader(new InputStreamReader(
                 Objects.requireNonNull(classes.getResourceAsStream("qa23-official-modules.txt")),
                 java.nio.charset.StandardCharsets.UTF_8))) {
            for (String line : input.lines().toList()) {
                var row = line.split(" ");
                String id = row[1];
                if (!client && row[3].equals("client")) {
                    if (loader.isModLoaded(id))
                        throw new AssertionError("Client module on server " + id);
                    continue;
                }
                var mod = loader.getModContainer(id).orElseThrow();
                if (!mod.getMetadata().getVersion().getFriendlyString().equals(row[2]))
                    throw new AssertionError(id);
                Path root = mod.getRootPaths().getFirst(), api = root.resolve("net/fabricmc/fabric/api");
                var archive = Path.of(root.getFileSystem().toString()).toRealPath();
                try (var source = Files.newInputStream(archive)) {
                    if (!HexFormat.of()
                             .formatHex(MessageDigest.getInstance("SHA-256").digest(source.readAllBytes()))
                             .equals(row[0]))
                        throw new AssertionError("Wrong module original " + id);
                }
                var skip = new HashSet<String>();
                if (!client)
                    try (var stream = Files.newInputStream(root.resolve("META-INF/MANIFEST.MF"))) {
                        var attr = new Manifest(stream).getMainAttributes().getValue(
                            "Fabric-Loom-Client-Only-Entries");
                        if (attr != null)
                            Collections.addAll(skip, attr.split(";"));
                    }
                readTargets(root, client);
                int count = 0;
                if (Files.isDirectory(api))
                    try (var stream = Files.walk(api)) {
                        for (var file : stream.filter(Files::isRegularFile).toList()) {
                            String member = root.relativize(file).toString();
                            if (!member.endsWith(".class") || member.endsWith("package-info.class") ||
                                skip.contains(member))
                                continue;
                            String name = member.substring(0, member.length() - 6).replace('/', '.');
                            var type = Class.forName(name, false, classes);
                            if (!Files.isSameFile(
                                    archive,
                                    Path.of(
                                        type.getProtectionDomain().getCodeSource().getLocation().toURI())))
                                throw new AssertionError("Shadowed official API " + name);
                            count++;
                        }
                    }
                total += count;
                modules++;
                System.out.println("QA23 MODULE " + id + " classes=" + count);
            }
            System.out.println("QA23 FULL API " + (client ? "CLIENT" : "SERVER") + " modules=" + modules +
                               " classes=" + total);
        } catch (Exception | LinkageError e) {
            throw new IllegalStateException("Official Fabric API link audit", e);
        }
    }
}
