package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.util.ArrayList;
final class InventoryPatches {
    static void add(TransformPlan plan) {
        String block = "net/minecraft/world/level/block/entity/BlockEntity";
        var type = ClassDesc.ofInternalName(block);
        var caps = ClassDesc.of("mc.unity.adapter.forge.InventoryCaps");
        var noArgs = MethodTypeDesc.of(ConstantDescs.CD_void);
        plan.add(block, 75, "Forge item capability", "block entity lifetime", (cf, model) -> {
            int[] hits = {0};
            var patch =
                ClassTransform
                    .transformingMethodBodies(
                        m
                        -> m.methodName().equalsString("setRemoved") && m.methodType().equalsString("()V"),
                        (b, e) -> {
                            if (e instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                                var done = b.newLabel();
                                b.aload(0)
                                    .getfield(type, "unity$itemViews", caps)
                                    .ifnull(done)
                                    .aload(0)
                                    .getfield(type, "unity$itemViews", caps)
                                    .invokevirtual(caps, "invalidate", noArgs)
                                    .aload(0)
                                    .aconst_null()
                                    .putfield(type, "unity$itemViews", caps)
                                    .labelBinding(done);
                                hits[0]++;
                            }
                            b.with(e);
                        })
                    .andThen(ClassTransform.endHandler(b -> {
                        var faces = new ArrayList<ClassDesc>();
                        model.interfaces().forEach(f -> faces.add(f.asSymbol()));
                        faces.add(ClassDesc.of("mc.unity.adapter.forge.InventoryCaps$Owner"));
                        b.withInterfaceSymbols(faces)
                            .withField("unity$itemViews", caps, ClassFile.ACC_PRIVATE)
                            .withMethodBody("unity$items", MethodTypeDesc.of(caps), ClassFile.ACC_PUBLIC,
                                            code -> {
                                                var present = code.newLabel();
                                                code.aload(0)
                                                    .getfield(type, "unity$itemViews", caps)
                                                    .ifnonnull(present)
                                                    .aload(0)
                                                    .new_(caps)
                                                    .dup()
                                                    .invokespecial(caps, "<init>", noArgs)
                                                    .putfield(type, "unity$itemViews", caps)
                                                    .labelBinding(present)
                                                    .aload(0)
                                                    .getfield(type, "unity$itemViews", caps)
                                                    .areturn();
                                            });
                    }));
            var bytes = cf.transformClass(model, patch);
            if (hits[0] != 1)
                throw new IOException("BlockEntity.setRemoved callback anchor");
            return bytes;
        });
    }
}
