package mc.unity.bootstrap;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.io.IOException;
final class WorldRegistryPatches {
    private static final ClassDesc CORE = ClassDesc.of("mc.unity.core.WorldRegistries");
    static void add(TransformPlan plan) {
        plan.add("net/minecraft/core/registries/Registries", 145, "World registry directory", "registryDirPath",
                 (cf, m) -> {
                     int[] hits = {0};
                     var bytes = cf.transformClass(
                         m, ClassTransform.transformingMethodBodies(
                                method -> method.methodName().equalsString("registryDirPath"), (b, e) -> {
                                    if (e instanceof InvokeInstruction i && i.name().equalsString("getPath")) {
                                        hits[0]++;
                                        b.invokestatic(
                                            CORE, "directory",
                                            MethodTypeDesc.ofDescriptor(
                                                "(Lnet/minecraft/resources/Identifier;)Ljava/lang/String;"));
                                    } else
                                        b.with(e);
                                }));
                     if (hits[0] != 1)
                         throw new IOException("Missing registry directory anchor");
                     return bytes;
                 });
        plan.add("net/minecraft/resources/RegistryDataLoader", 145, "World registry declarations",
                 "codec lists", (cf, m) -> {
                     int[] hits = {0};
                     var bytes = cf.transformClass(
                         m, ClassTransform.transformingMethodBodies(
                                method -> method.methodName().equalsString("<clinit>"), (b, e) -> {
                                    if (e instanceof FieldInstruction f && f.opcode() == Opcode.PUTSTATIC &&
                                        (f.name().equalsString("WORLDGEN_REGISTRIES") ||
                                         f.name().equalsString("SYNCHRONIZED_REGISTRIES"))) {
                                        hits[0]++;
                                        b.loadConstant(f.name().equalsString("SYNCHRONIZED_REGISTRIES") ? 1 : 0)
                                            .invokestatic(CORE, "wrap",
                                                          MethodTypeDesc.ofDescriptor(
                                                              "(Ljava/util/List;Z)Ljava/util/List;"));
                                    }
                                    b.with(e);
                                }));
                     if (hits[0] != 2)
                         throw new IOException("World registry lists: " + hits[0]);
                     return bytes;
                 });
        plan.add(
            "net/minecraft/core/RegistrySynchronization", 145, "World registry synchronization",
            "network tags and empty maps", (cf, m) -> {
                int[] hits = {0, 0};
                String callback=MethodTarget.callback("packRegistry","accept").resolve(m);
                var bytes = cf.transformClass(m, (b, element) -> {
                    if (!(element instanceof MethodModel method)) {
                        b.with(element);
                        return;
                    }
                    b.transformMethod(method, MethodTransform.transformingCode((code, e) -> {
                        if (method.methodName().equalsString("isNetworkable") && e instanceof
                                                                                     ReturnInstruction) {
                            code.aload(0).invokestatic(
                                CORE, "networkable",
                                MethodTypeDesc.ofDescriptor("(ZLnet/minecraft/resources/ResourceKey;)Z"));
                            hits[0]++;
                        }
                        if (method.methodName().equalsString(callback) &&
                            e instanceof InvokeInstruction i && i.name().equalsString("accept") &&
                            i.owner().asInternalName().equals("java/util/function/BiConsumer")) {
                            code.invokestatic(
                                CORE, "send",
                                MethodTypeDesc.ofDescriptor(
                                    "(Ljava/util/function/BiConsumer;Ljava/lang/Object;Ljava/lang/Object;)V"));
                            hits[1]++;
                        } else
                            code.with(e);
                    }));
                });
                if (hits[0] != 1 || hits[1] != 1)
                    throw new IOException("World registry network anchors");
                return bytes;
            });
    }
}
