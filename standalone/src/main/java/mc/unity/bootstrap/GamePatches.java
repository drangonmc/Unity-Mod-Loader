package mc.unity.bootstrap;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.*;
import java.util.*;
import java.util.jar.JarFile;

/** All injection points are tied to the verified 26.2 class files. Installation fails on a missing anchor. */
final class GamePatches {
    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final String COMMANDS = "net/minecraft/commands/Commands";
    private static final ClassDesc HOOKS = ClassDesc.of("mc.unity.runtime.ServerHooks");
    private static final MethodTypeDesc SERVER_ARG = MethodTypeDesc.ofDescriptor("(L" + SERVER + ";)V");
    private static final String COMMAND_ARGS =
        "(L" + COMMANDS + "$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V";

    static void install(GameLoader gameLoader, java.nio.file.Path original) throws Exception {
        installSide(gameLoader, original, false);
    }
    static void installClient(GameLoader loader, java.nio.file.Path original) throws Exception {
        installSide(loader, original, true);
    }
    private static void installSide(GameLoader loader, java.nio.file.Path original, boolean client)
        throws Exception {
        var cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofResourceParsing(loader).cached()));
        var plan = new TransformPlan();
        plan.add("net/minecraft/core/Holder", 40, "NeoForge holder ABI", "permitted subclasses",
            (file, model) -> file.transformClass(model, (b, e) -> {
                if (!(e instanceof java.lang.classfile.attribute.PermittedSubclassesAttribute))
                    b.with(e);
            }));
        if (client)
            plan.add(
                "net/minecraft/client/Minecraft", 50, "Unity lifecycle", "tick()V", GamePatches::clientTick);
        {
            plan.add(COMMANDS, 50, "Unity commands", "<init>" + COMMAND_ARGS, GamePatches::commands);
            plan.add(SERVER, 50, "Unity lifecycle", "*", GamePatches::server);
        }
        if (client && Boolean.getBoolean("mc.unity.hiddenQA") && Boolean.getBoolean("mc.unity.standaloneQA")) {
            // 26.2 creates the GLFW window hidden. Keep that native window and its real graphics context.
            plan.add("net/minecraft/client/Minecraft", 51, "Hidden QA window", "glfwShowWindow", (file, model) -> {
                int[] hits = {0};
                var bytes = file.transformClass(model, ClassTransform.transformingMethodBodies((b,e) -> {
                    if (e instanceof java.lang.classfile.instruction.InvokeInstruction i
                        && i.owner().asInternalName().equals("org/lwjgl/glfw/GLFW")
                        && i.name().equalsString("glfwShowWindow") && i.type().equalsString("(J)V")) {
                        b.pop2(); hits[0]++;
                    } else b.with(e);
                }));
                if (hits[0] != 1) throw new IOException("Unexpected GLFW visibility calls: " + hits[0]);
                return bytes;
            });
        }
        AttachmentPatches.add(plan);
        ExtensionPatches.add(plan);
        EcologyPatches.add(plan);
        InventoryPatches.add(plan);
        LifecyclePatches.add(plan, client);
        LevelPatches.add(plan);
        ActionPatches.add(plan, client);
        NeoDataPatches.add(plan);
        NeoGameplayPatches.add(plan,client);
        if (client) {
            CreativePagesPatch.add(plan);
            ClientModelPatches.add(plan);
        }
        WorldRegistryPatches.add(plan);
        ContentPatches.add(plan, client);
        TransportPatches.add(plan, client);
        ConfigurationPatches.add(plan, client);
        mc.unity.adapter.forge.transform.ForgeGamePatches.add(plan, client);
        try (var jar = new JarFile(original.toFile(), true)) {
            loader.install(plan.build(jar, cf));
        }
    }
    private static byte[] clientTick(ClassFile cf, ClassModel model) {
        int[] matches = {0};
        var transform = ClassTransform.transformingMethodBodies(
            m -> m.methodName().equalsString("tick") && m.methodType().equalsString("()V"), (b, e) -> {
                if (e instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                    b.invokestatic(ClassDesc.of("mc.unity.runtime.ClientHooks"), "tick",
                        MethodTypeDesc.of(ConstantDescs.CD_void));
                    matches[0]++;
                }
                b.with(e);
            });
        var bytes = cf.transformClass(model, transform);
        if (matches[0] == 0)
            throw new IllegalStateException("Missing Minecraft 26.2 client tick hook");
        return bytes;
    }

    private static byte[] commands(ClassFile cf, ClassModel model) {
        int[] matches = {0};
        var transform = ClassTransform.transformingMethodBodies(
            m -> m.methodName().equalsString("<init>") && m.methodType().equalsString(COMMAND_ARGS), (b, e) -> {
                if (e instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                    b.aload(0).aload(1).aload(2).invokestatic(HOOKS, "commands",
                        MethodTypeDesc.ofDescriptor("(L" + COMMANDS + ";" + COMMAND_ARGS.substring(1)));
                    matches[0]++;
                }
                b.with(e);
            });
        byte[] result = cf.transformClass(model, transform);
        if (matches[0] != 1)
            throw new IllegalStateException("Commands constructor hook count: " + matches[0]);
        return result;
    }

    private static byte[] server(ClassFile cf, ClassModel model) {
        Set<String> found = new HashSet<>();
        byte[] result = cf.transformClass(model, (builder, element) -> {
            if (!(element instanceof MethodModel m)) {
                builder.with(element);
                return;
            }
            String key = m.methodName().stringValue() + m.methodType().stringValue();
            if (key.equals("stopServer()V")) {
                var owner = ClassDesc.ofInternalName(SERVER);
                var empty = MethodTypeDesc.of(ConstantDescs.CD_void);
                builder.withMethod(
                    "unity$stopBody", empty, ClassFile.ACC_PRIVATE, mb -> mb.with(m.code().orElseThrow()));
                builder.withMethod(m.methodName(), m.methodType(), m.flags().flagsMask(), mb -> {
                    for (var el : m)
                        if (!(el instanceof CodeModel) && !(el instanceof AccessFlags))
                            mb.with(el);
                    mb.withCode(b -> {
                        var start = b.newLabel();
                        var end = b.newLabel();
                        var handler = b.newLabel();
                        b.labelBinding(start)
                            .aload(0)
                            .invokestatic(HOOKS, "stopping", SERVER_ARG)
                            .aload(0)
                            .invokespecial(owner, "unity$stopBody", empty)
                            .labelBinding(end)
                            .aload(0)
                            .invokestatic(HOOKS, "stopped", SERVER_ARG)
                            .return_()
                            .labelBinding(handler)
                            .aload(0)
                            .invokestatic(HOOKS, "stopped", SERVER_ARG)
                            .athrow()
                            .exceptionCatchAll(start, end, handler);
                    });
                });
                found.add("stopping");
                return;
            }
            String hook = switch (key) {
                case "runServer()V" -> "started";
                case "tickServer(Ljava/util/function/BooleanSupplier;)V" -> "tick";
                default -> null;
            };
            if (hook == null) {
                builder.with(m);
                return;
            }
            boolean[] inserted = {false};
            builder.transformMethod(m, MethodTransform.transformingCode((b, e) -> {
                if (hook.equals("tick") && e instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                    b.aload(0).invokestatic(HOOKS, hook, SERVER_ARG);
                    inserted[0] = true;
                }
                b.with(e);
                if (hook.equals("started") && e instanceof FieldInstruction f && f.opcode() == Opcode.PUTFIELD
                    && f.owner().asInternalName().equals(SERVER) && f.name().equalsString("status")) {
                    if (inserted[0])
                        throw new IllegalStateException("Duplicate server-ready anchor");
                    b.aload(0).invokestatic(HOOKS, hook, SERVER_ARG);
                    inserted[0] = true;
                }
            }));
            if (inserted[0])
                found.add(hook);
        });
        if (!found.equals(Set.of("started", "tick", "stopping")))
            throw new IllegalStateException("Server hooks found: " + found);
        return result;
    }
}
