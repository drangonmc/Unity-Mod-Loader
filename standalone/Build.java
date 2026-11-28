import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;

/** JDK-only source build. Arguments: project directory, original 26.2 Mojang server bundle. */
class Build {
    static final String VERSION = "0.3.1-Beta";
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3)
            throw new IllegalArgumentException(
                "java standalone/Build.java <project> <original-server.jar> [vanilla-client-home]");
        Path root = Path.of(args[0]).toRealPath(), bundle = Path.of(args[1]).toRealPath();
        Path src = root.resolve("standalone/src"),
             work = Files.createTempDirectory(Files.createDirectories(root.resolve(".work")), "unity-build-");
        try {
            Path bootstrap = work.resolve("bootstrap"), classes = work.resolve("classes"),
                 example = work.resolve("example");
            Path empty = Files.createDirectory(work.resolve("empty"));
            compile(List.of(src.resolve("main/java/mc/unity/bootstrap/VanillaBundle.java"),src.resolve("main/java/mc/unity/bootstrap/ClientFiles.java")), List.of(empty), bootstrap, empty);
            List<Path> game = new ArrayList<>();
            try (var loader = new URLClassLoader(
                     new URL[] {bootstrap.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                var type = loader.loadClass("mc.unity.bootstrap.VanillaBundle");
                var runtime = type.getMethod("prepare", Path.class, Path.class)
                                  .invoke(null, root.resolve("cache/" + VERSION), bundle);
                game.add((Path) runtime.getClass().getMethod("game").invoke(runtime));
                for (var path : (List<?>) runtime.getClass().getMethod("libraries").invoke(runtime))
                    game.add((Path) path);
            }
            // NeoForge's holder type implements the vanilla interface. Present the same
            // unsealed 26.2 interface to javac that the runtime supplies to the JVM.
            Path compileApi=work.resolve("compile-api");
            String holder="net/minecraft/core/Holder.class";
            try(var original=new JarFile(game.getFirst().toFile(),true);var in=original.getInputStream(original.getJarEntry(holder))){
                var cf=java.lang.classfile.ClassFile.of();var def=cf.parse(in.readAllBytes());
                byte[] adjusted=cf.transformClass(def,(b,e)->{if(!(e instanceof java.lang.classfile.attribute.PermittedSubclassesAttribute))b.with(e);});
                Files.createDirectories(compileApi.resolve(holder).getParent());Files.write(compileApi.resolve(holder),adjusted);
            }
            game.addFirst(compileApi);
            var sources = javaFiles(src.resolve("main/java"));
            sources.add(root.resolve("src/main/java/mc/unity/api/UnityMod.java"));
            sources.add(root.resolve("src/main/java/mc/unity/api/PluginHost.java"));
            sources.add(root.resolve("src/main/java/mc/unity/loader/NativeLoader.java"));
            sources.add(root.resolve("src/main/java/net/minecraftforge/fml/common/Mod.java"));
            var libraryDir=root.resolve("standalone/lib");
            var bundled=Files.createDirectory(work.resolve("bytecode-libraries"));
            for(String row:Files.readAllLines(root.resolve("standalone/lib/SHA256SUMS"))){
                var part=row.split("  ");var file=libraryDir.resolve(part[1]);var md=java.security.MessageDigest.getInstance("SHA-256");
                if(!HexFormat.of().formatHex(md.digest(Files.readAllBytes(file))).equals(part[0]))throw new IOException("Changed bytecode library "+file);
                game.add(file);
                try(var jar=new JarFile(file.toFile(),true)){for(var e:Collections.list(jar.entries())){
                    var n=e.getName();if(e.isDirectory()||n.endsWith("module-info.class")||n.startsWith("META-INF/versions/"))continue;
                    boolean license=n.toLowerCase(Locale.ROOT).contains("license")||n.toLowerCase(Locale.ROOT).contains("notice");
                    boolean neoSource=part[1].startsWith("unity-neo-transfer-") && n.startsWith("META-INF/unity-sources/") && n.endsWith(".java");
                    if(!n.endsWith(".class")&&!n.equals("org/spongepowered/asm/mixin/refmap/mixin.refmap.json")&&!license&&!neoSource)continue;
                    var out=license?bundled.resolve("META-INF/licenses/"+part[1]+"/"+n):bundled.resolve(n);
                    Files.createDirectories(out.getParent());try(var in=jar.getInputStream(e)){Files.copy(in,out);}
                }}
            }
            List<Path> compileGame = new ArrayList<>(game);
            if (args.length == 3) {
                try (var loader = new URLClassLoader(new URL[]{bootstrap.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                    for (var file : (List<?>) loader.loadClass("mc.unity.bootstrap.ClientFiles").getMethod("paths", Path.class).invoke(null, Path.of(args[2]).toRealPath()))
                        compileGame.add((Path) file);
                }
            } else {
                // The dedicated-only SDK lacks the client class named by the Fabric tick callback.
                // This signature lives in the temporary javac class path and never enters the loader JAR.
                var type = java.lang.constant.ClassDesc.of("net.minecraft.client.Minecraft");
                Path declaration = compileApi.resolve("net/minecraft/client/Minecraft.class");
                Files.createDirectories(declaration.getParent());
                Files.write(declaration, java.lang.classfile.ClassFile.of().build(type,
                    b -> b.withFlags(java.lang.reflect.AccessFlag.PUBLIC).withMethod("getInstance",
                        java.lang.constant.MethodTypeDesc.of(type), java.lang.classfile.ClassFile.ACC_PUBLIC |
                        java.lang.classfile.ClassFile.ACC_STATIC | java.lang.classfile.ClassFile.ACC_NATIVE, m -> {})));
            }
            compile(sources, compileGame, classes, empty);
            var manifest = new Manifest();
            var attr = manifest.getMainAttributes();
            attr.putValue("Manifest-Version", "1.0");
            attr.putValue("Main-Class", "mc.unity.bootstrap.Main");
            attr.putValue("Implementation-Version", VERSION);
            var asmVersion=new Attributes();asmVersion.putValue("Implementation-Version","9.10.1");manifest.getEntries().put("org/objectweb/asm/",asmVersion);
            var weAdapter=root.resolve("src/plugin/resources/META-INF/unity/worldedit-7.4.6.jar");
            String weLock=Files.readString(root.resolve("standalone/adapters/worldedit/SHA256SUMS")).strip();
            if(!weLock.equals(checksum(weAdapter)+"  "+weAdapter.getFileName()))
                throw new IOException("Changed WorldEdit adapter "+weAdapter);
            Path pluginDir=root.resolve("standalone/vendor/bukkit");
            Path embedded=Files.createDirectories(bundled.resolve("META-INF/unity/bukkit"));
            List<Path> pluginClasspath=new ArrayList<>(List.of(classes));
            List<String> pluginIndex=new ArrayList<>();
            for(String row:Files.readAllLines(pluginDir.resolve("SHA256SUMS"))){
                var fields=row.split("  ",2);var lib=pluginDir.resolve(fields[1]);
                if(!checksum(lib).equals(fields[0]))throw new IOException("Changed Bukkit API dependency "+lib);
                pluginClasspath.add(lib); Files.copy(lib,embedded.resolve(fields[1]));pluginIndex.add(row);
            }
            pluginClasspath.addAll(game);
            var adapterClasses=work.resolve("plugin-classes");
            compile(javaFiles(root.resolve("src/plugin/java")),pluginClasspath,adapterClasses,empty);
            var apiList=work.resolve("facade-classpath.txt");
            Files.write(apiList,pluginClasspath.stream().map(Path::toString).toList());
            if(new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    root.resolve("standalone/FacadeBuild.java").toString(),adapterClasses.toString(),apiList.toString()).inheritIO().start().waitFor()!=0)
                throw new IOException("Bukkit facade generation failed");
            var adapter=embedded.resolve("unity-bukkit.jar");
            var metadata=new Manifest();metadata.getMainAttributes().putValue("Manifest-Version","1.0");
            pack(adapter,metadata,adapterClasses,root.resolve("src/plugin/resources"));
            pluginIndex.add(checksum(adapter)+"  unity-bukkit.jar");
            Files.writeString(embedded.resolve("index"),String.join("\n",pluginIndex)+"\n");
            Path artifact = work.resolve("unity-loader-" + VERSION + ".jar");
            pack(artifact, manifest, classes, src.resolve("main/resources"),bundled);
            game.add(0, artifact);
            var serverExamples=javaFiles(src.resolve("example/java"));serverExamples.addAll(javaFiles(src.resolve("testsupport/java")));
            compile(serverExamples,game,example,empty);
            var modManifest = new Manifest();
            modManifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            Path mod = work.resolve("unity-world-marker.jar");
            pack(mod, modManifest, example, src.resolve("example/resources"));
            Path release = Files.createDirectories(Path.of(System.getProperty("mc.unity.buildOutput",
                root.resolve("versions/" + VERSION).toString())));
            var outputs = new ArrayList<>(List.of(artifact, mod));
            if (args.length == 3) {
                Path home = Path.of(args[2]).toRealPath();
                List<Path> clientPath = new ArrayList<>(List.of(artifact));
                try(var loader=new URLClassLoader(new URL[]{bootstrap.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
                    for(var file:(List<?>)loader.loadClass("mc.unity.bootstrap.ClientFiles").getMethod("paths",Path.class).invoke(null,home))clientPath.add((Path)file);
                }
                var exampleClient = work.resolve("client-example");
                var clientExamples=javaFiles(src.resolve("client-example/java"));clientExamples.addAll(javaFiles(src.resolve("testsupport/java")));
                compile(clientExamples,clientPath,exampleClient,empty);
                var clientMod = work.resolve("unity-client-witness.jar");
                pack(clientMod, modManifest, exampleClient, src.resolve("client-example/resources"));
                outputs.add(clientMod);
            }
            for (Path file : outputs) {
                Files.move(file, release.resolve(file.getFileName()), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("Built independent Unity in " + release);
        } finally {
            if (!work.toRealPath().getParent().equals(root.resolve(".work").toRealPath()))
                throw new IOException("Unexpected build directory");
            try (var paths = Files.walk(work)) {
                for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
    private static String checksum(Path file) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    private static ArrayList<Path> javaFiles(Path dir) throws IOException {
        try (var files = Files.walk(dir)) {
            return new ArrayList<>(files.filter(p -> p.toString().endsWith(".java")).sorted().toList());
        }
    }
    private static void compile(List<Path> sources, List<Path> cp, Path output, Path empty) throws IOException {
        Files.createDirectories(output);
        var args = new ArrayList<>(List.of("--release", "25", "-encoding", "UTF-8", "-proc:none", "-sourcepath",
            empty.toString(), "-d", output.toString(), "-classpath",
            String.join(File.pathSeparator, cp.stream().map(Path::toString).toList())));
        sources.forEach(p -> args.add(p.toString()));
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || compiler.run(null, null, null, args.toArray(String[] ::new)) != 0)
            throw new IOException("Compilation failed");
    }
    private static void pack(Path output, Manifest manifest, Path... roots) throws IOException {
        try (var jar = new JarOutputStream(Files.newOutputStream(output))) {
            var entry = new JarEntry("META-INF/MANIFEST.MF");
            entry.setTime(0);
            jar.putNextEntry(entry);
            manifest.write(jar);
            jar.closeEntry();
            for (var root : roots) try (var files = Files.walk(root)) {
                    for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                        entry = new JarEntry(root.relativize(file).toString().replace(File.separatorChar, '/'));
                        entry.setTime(0);
                        jar.putNextEntry(entry);
                        Files.copy(file, jar);
                        jar.closeEntry();
                    }
                }
        }
    }
}
