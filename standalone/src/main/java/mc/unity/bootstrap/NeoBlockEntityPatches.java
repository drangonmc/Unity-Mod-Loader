package mc.unity.bootstrap;

import java.lang.classfile.*;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

final class NeoBlockEntityPatches {
    static void add(TransformPlan plan) {
        String name = "net/minecraft/world/level/block/entity/BlockEntityType";
        ClassDesc owner = ClassDesc.ofInternalName(name);
        ClassDesc factory = owner.nested("BlockEntitySupplier");
        ClassDesc blocks = ClassDesc.of("net.minecraft.world.level.block.Block").arrayType();
        ClassDesc set = ClassDesc.of("java.util.Set");
        var exception = ClassDesc.of("java.lang.IllegalArgumentException");
        var vanilla = MethodTypeDesc.of(CD_void, factory, set);
        var explicit = MethodTypeDesc.of(CD_void, factory, set, CD_boolean);
        var arguments = MethodTypeDesc.of(CD_void, factory, CD_boolean, blocks);
        plan.add(name, 150, "NeoForge", "block entity constructors and NBT permissions", (cf, model) -> {
            boolean hasFlag = model.methods().stream().anyMatch(m -> m.methodName().equalsString("onlyOpCanSetNbt")
                    && m.methodType().equalsString("()Z"));
            if (!hasFlag || model.methods().stream().noneMatch(m -> m.methodName().equalsString("<init>")
                    && m.methodTypeSymbol().equals(vanilla))) throw new IllegalStateException("26.2 block entity type shape");
            ClassTransform protect = (builder, e) -> {
                if (e instanceof MethodModel m && m.methodName().equalsString("onlyOpCanSetNbt"))
                    builder.transformMethod(m, MethodTransform.transformingCode((c, element) -> {
                        if (element instanceof java.lang.classfile.instruction.ReturnInstruction ret
                                && ret.opcode() == Opcode.IRETURN)
                            c.aload(0).getfield(owner, "unityOpOnly", CD_boolean).ior();
                        c.with(element);
                    }));
                else builder.with(e);
            };
            return cf.transformClass(model, protect.andThen(ClassTransform.endHandler(b -> {
                b.withField("unityOpOnly", CD_boolean, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
                b.withMethodBody("<init>", explicit, ClassFile.ACC_PUBLIC, c -> c.aload(0).aload(1).aload(2)
                    .invokespecial(owner,"<init>",vanilla).aload(0).iload(3).putfield(owner,"unityOpOnly",CD_boolean).return_());
                b.withMethodBody("<init>", MethodTypeDesc.of(CD_void,factory,blocks), ClassFile.ACC_PUBLIC | ClassFile.ACC_VARARGS,
                    c -> c.aload(0).aload(1).iconst_0().aload(2).invokespecial(owner,"<init>",arguments).return_());
                b.withMethodBody("<init>", arguments, ClassFile.ACC_PUBLIC | ClassFile.ACC_VARARGS, c -> {
                    var valid = c.newLabel();
                    c.aload(3).arraylength().ifne(valid).new_(exception).dup()
                        .ldc("Expected at least one valid block").invokespecial(exception,
                            "<init>", MethodTypeDesc.of(CD_void,CD_String)).athrow().labelBinding(valid);
                    c.aload(0).aload(1).aload(3).invokestatic(set,"of",MethodTypeDesc.of(set,CD_Object.arrayType()),true)
                        .iload(2).invokespecial(owner,"<init>",explicit).return_();
                });
                b.withMethodBody("getValidBlocks", MethodTypeDesc.of(set), ClassFile.ACC_PUBLIC, c -> c.aload(0)
                    .getfield(owner,"validBlocks",set).invokestatic(ClassDesc.of("java.util.Collections"),
                        "unmodifiableSet",MethodTypeDesc.of(set,set)).areturn());
            })));
        });
    }
}
