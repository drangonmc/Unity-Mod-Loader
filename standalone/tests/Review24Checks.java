import java.io.*;
import java.nio.file.*;
import java.lang.classfile.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import mc.unity.adapter.Entry;
import mc.unity.adapter.common.*;
import net.minecraft.network.FriendlyByteBuf;
import mc.unity.core.LoginInventory;

class Review24Checks {
    private static ClassModel model(String name, String target, String platform, boolean onlyClient,
                                    boolean foreignInterface) {
        var w = new ClassWriter(0);
        w.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (platform != null) {
            String ns = platform.equals("neo") ? "net/neoforged" : "net/minecraftforge";
            var a = w.visitAnnotation("L" + ns + "/fml/common/Mod;", true);
            a.visit("value", "sided");
            a.visitEnd();
            if (onlyClient) {
                var side = w.visitAnnotation("L" + ns + "/api/distmarker/OnlyIn;", true);
                side.visitEnum("value", "L" + ns + "/api/distmarker/Dist;", "CLIENT");
                if (foreignInterface)
                    side.visit("_interface", Type.getType("Ljava/lang/Runnable;"));
                side.visitEnd();
            }
        }
        if (target != null) {
            if (target.startsWith("field:"))
                w.visitField(Opcodes.ACC_PUBLIC, "value", "[L" + target.substring(6) + ";", null, null)
                    .visitEnd();
            var method = w.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "run", "()V", null, null);
            method.visitCode();
            if (!target.startsWith("field:"))
                method.visitMethodInsn(Opcodes.INVOKESTATIC, target, "work", "()V", false);
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        w.visitEnd();
        return ClassFile.of().parse(w.toByteArray());
    }
    public static void main(String[] args) throws Exception {
        for (String platform : List.of("forge", "neo")) {
            var choice = new PlatformChoice.Choice(
                platform.equals("neo") ? Entry.Kind.NEOFORGE : Entry.Kind.FORGE, false);
            var common = model("probe/Common", null, platform, false, false);
            var client = model("probe/Client", "probe/ClientHelper", platform, true, false);
            var helper =
                model("probe/ClientHelper", "net/neoforged/unsupported/ClientFeature", null, false, false);
            var models = List.of(common, client, helper);
            if (!SidedClasses.inactive(models, choice, false, null)
                     .equals(Set.of("probe/Client", "probe/ClientHelper")))
                throw new AssertionError(platform);
            if (!SidedClasses.inactive(models, choice, true, null).isEmpty())
                throw new AssertionError("Client preflight skipped");
            try {
                PlatformAbi.check(helper);
                throw new AssertionError("Active unknown API accepted");
            } catch (IOException expected) {}
            if (!SidedClasses
                     .inactive(List.of(model("probe/Common", "probe/Client", platform, false, false), client,
                                       helper),
                               choice, false, null)
                     .equals(Set.of("probe/Client", "probe/ClientHelper")))
                throw new AssertionError("Lazy side reference promoted");
            try {
                SidedClasses.inactive(
                    List.of(model("probe/Common", "field:probe/Client", platform, false, false), client,
                            helper),
                    choice, false, null);
                throw new AssertionError("Common link escaped");
            } catch (IOException expected) {
                if (!expected.getMessage().contains("probe/Client"))
                    throw expected;
            }
            if (!SidedClasses
                     .inactive(
                         List.of(model("probe/Common", "probe/ClientHelper", platform, true, true), helper),
                         choice, false, null)
                     .isEmpty())
                throw new AssertionError("An interface annotation suppressed the entire class");
        }
        System.out.println("PASS Forge and NeoForge physical side filters and active dependency rejection");

        // Simulate a plugin whose main class fails to load after its host has attached its adapter.
        Path testDir = Files.createTempDirectory("unity-host-rollback-");
        try {
            var plugins = Files.createDirectory(testDir.resolve("plugins"));
            var file = plugins.resolve("broken.jar");
            try (var out = new JarOutputStream(Files.newOutputStream(file))) {
                out.putNextEntry(new JarEntry("plugin.yml"));
                out.write(
                    "name: BrokenProbe\nversion: 1\napi-version: '26.2'\nmain: missing.Entry\n".getBytes());
                out.closeEntry();
            }
            var field = mc.unity.core.Actions.class.getDeclaredField("adapters");
            field.setAccessible(true);
            var adapters = (java.util.List<?>)field.get(null);
            int baseline = adapters.size();
            var session = Class.forName("mc.unity.runtime.PluginSession");
            var open = session.getDeclaredMethod("open", Path.class);
            open.setAccessible(true);
            var access = session.getDeclaredMethod("host");
            access.setAccessible(true);
            try (var owner = (AutoCloseable)open.invoke(null, testDir)) {
                var host = (mc.unity.api.PluginHost)access.invoke(owner);
                try {
                    host.aboutToStart(null);
                    throw new AssertionError("Expected missing plugin entry");
                } catch (IllegalStateException expected) {
                    if (!(expected.getCause() instanceof ClassNotFoundException))
                        throw expected;
                }
                if (adapters.size() != baseline)
                    throw new AssertionError("Plugin adapter survived failed startup");
                host.close();
                if (adapters.size() != baseline)
                    throw new AssertionError("Unexpected adapter removal");
            }
            Files.delete(file);
            Files.delete(plugins);
        } finally {
            Files.deleteIfExists(testDir.resolve("plugins/broken.jar"));
            Files.deleteIfExists(testDir.resolve("plugins"));
            Files.deleteIfExists(testDir);
        }
        System.out.println("PASS host startup rollback without closing Platforms");
        var first = new mc.unity.core.Actions.Adapter() {
            public boolean handles(mc.unity.core.Actions.Kind k) {
                return true;
            }
            public void accept(mc.unity.core.Actions.Action a) {}
        };
        var detachFirst = mc.unity.core.Actions.attachScoped(first);
        var detachSecond = mc.unity.core.Actions.attachScoped(first);
        detachFirst.run();
        detachFirst.run();
        if (!mc.unity.core.Actions.active(mc.unity.core.Actions.Kind.BREAK))
            throw new AssertionError("Detached another registration");
        detachSecond.run();
        if (mc.unity.core.Actions.active(mc.unity.core.Actions.Kind.BREAK))
            throw new AssertionError("Leaked gameplay registration");
        System.out.println("PASS independent and idempotent gameplay detach");
        var expected = new LoginInventory(Map.of("alpha", "2@a", "missing", "1"),
                                          Map.of("minecraft:item", "111"), false);
        var peer =
            new LoginInventory(Map.of("alpha", "1@b", "extra", "3"), Map.of("minecraft:item", "222"), false);
        var b = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            peer.write(b);
            if (!b.readUtf(64).equals("unity-diff-1"))
                throw new AssertionError();
            var read = LoginInventory.class.getDeclaredMethod("read", FriendlyByteBuf.class);
            read.setAccessible(true);
            var actual = (LoginInventory)read.invoke(null, b);
            var diff = LoginInventory.class.getDeclaredMethod("difference", LoginInventory.class);
            diff.setAccessible(true);
            var message = (String)diff.invoke(expected, actual);
            for (String member : List.of("alpha", "extra", "missing", "minecraft:item"))
                if (!message.contains(member))
                    throw new AssertionError(message);
        } finally {
            b.release();
        }
        System.out.println("PASS bounded diagnostic inventory version, missing, extra, registry difference");
    }
}
