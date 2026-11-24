package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
final class LevelPatches {
    static void add(TransformPlan plan) {
        String level = "net/minecraft/server/level/ServerLevel";
        var events = ClassDesc.of("mc.unity.core.Events");
        plan.add(
            "net/minecraft/server/MinecraftServer", 171, "Level lifecycle", "load/tick/unload", (cf, m) -> {
                int[] hits = {0, 0, 0};
                var bytes = cf.transformClass(m, (b, element) -> {
                    if (!(element instanceof MethodModel method)) {
                        b.with(element);
                        return;
                    }
                    b.transformMethod(method, MethodTransform.transformingCode((code, e) -> {
                        if (e instanceof InvokeInstruction i) {
                            if (method.methodName().equalsString("createLevels") &&
                                i.owner().asInternalName().equals("java/util/Map") &&
                                i.name().equalsString("put")) {
                                hits[0]++;
                                int slot = code.allocateLocal(TypeKind.REFERENCE);
                                code.dup().astore(slot).with(e).aload(slot).invokestatic(
                                    events, "loadLevel",
                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));
                                return;
                            }
                            if (method.methodName().equalsString("stopServer") &&
                                i.owner().asInternalName().equals(level) && i.name().equalsString("close")) {
                                hits[1]++;
                                code.dup().invokestatic(events, "unloadLevel",
                                                        MethodTypeDesc.ofDescriptor("(L" + level + ";)V"));
                            }
                            if (method.methodName().equalsString("tickChildren") &&
                                i.owner().asInternalName().equals(level) && i.name().equalsString("tick")) {
                                hits[2]++;
                                int slot = code.allocateLocal(TypeKind.REFERENCE);
                                var signal = MethodTypeDesc.ofDescriptor("(L" + level + ";)V");
                                code.dup2()
                                    .pop()
                                    .dup()
                                    .astore(slot)
                                    .invokestatic(events, "beforeLevelTick", signal)
                                    .with(e)
                                    .aload(slot)
                                    .invokestatic(events, "afterLevelTick", signal);
                                return;
                            }
                        }
                        code.with(e);
                    }));
                });
                if (hits[0] != 2 || hits[1] != 1 || hits[2] != 1)
                    throw new IOException("Level lifecycle anchors " + java.util.Arrays.toString(hits));
                return bytes;
            });
    }
}
