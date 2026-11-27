package mc.unity.runtime;

import java.lang.classfile.*;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

/** Extend Material while its enum universe is being built, before Bukkit or any plugin can cache values. */
final class BukkitMaterialPatch {
    static byte[] apply(byte[] original, ClassLoader loader) {
        ClassDesc material = ClassDesc.of("org.bukkit.Material");
        ClassDesc key = ClassDesc.of("org.bukkit.NamespacedKey");
        var array = material.arrayType();
        var bridge = ClassDesc.of("mc.unity.plugins.ModMaterials");
        var cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
        int[] anchors = {0};
        var model = cf.parse(original);
        ClassTransform append = (b,e) -> {
            if (e instanceof MethodModel m && m.methodName().equalsString("<clinit>"))
                b.transformMethod(m, MethodTransform.transformingCode((c,ins) -> {
                    if (ins instanceof FieldInstruction f && f.opcode()==Opcode.PUTSTATIC &&
                            f.name().equalsString("$VALUES") && f.owner().asSymbol().equals(material)) {
                        anchors[0]++;
                        c.invokestatic(bridge, "append", MethodTypeDesc.of(array,array));
                    }
                    c.with(ins);
                }));
            else if (e instanceof MethodModel m && m.methodName().equalsString("matchMaterial")
                    && m.methodType().equalsString("(Ljava/lang/String;Z)Lorg/bukkit/Material;"))
                b.transformMethod(m, MethodTransform.transformingCode(new CodeTransform() {
                    public void atStart(CodeBuilder c) {
                        var vanilla=c.newLabel();
                        var normal=c.newLabel();
                        c.iload(1).ifne(vanilla);
                        c.aload(0).invokestatic(bridge,"namespaced",MethodTypeDesc.of(material,CD_String))
                            .dup().ifnull(normal).areturn().labelBinding(normal).pop().labelBinding(vanilla);
                    }
                    public void accept(CodeBuilder c,CodeElement i) { c.with(i); }
                }));
            else b.with(e);
        };
        var bytes = cf.transformClass(model, append.andThen(ClassTransform.endHandler(b ->
            b.withMethodBody("<init>",MethodTypeDesc.of(CD_void,CD_String,CD_int,key),ClassFile.ACC_PRIVATE,c ->
                c.aload(0).aload(1).iload(2).iconst_m1().invokespecial(material,"<init>",
                    MethodTypeDesc.of(CD_void,CD_String,CD_int,CD_int))
                    .aload(0).aload(3).putfield(material,"key",key).return_()))));
        if (anchors[0]!=1) throw new IllegalStateException("Unexpected Bukkit Material initializer");
        return bytes;
    }
}
