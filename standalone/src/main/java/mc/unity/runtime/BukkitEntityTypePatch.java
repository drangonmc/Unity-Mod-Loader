package mc.unity.runtime;

import java.lang.classfile.*;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

/** Install registered mod entity keys before Bukkit and WorldEdit take their enum registry snapshots. */
final class BukkitEntityTypePatch {
    static byte[] apply(byte[] bytes,ClassLoader loader){
        var cf=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
        var type=ClassDesc.of("org.bukkit.entity.EntityType");var key=ClassDesc.of("org.bukkit.NamespacedKey");
        var array=type.arrayType();var bridge=ClassDesc.of("mc.unity.plugins.ModEntityTypes");int[] hits={0};
        var append=ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("<clinit>"),(code,e)->{
            if(e instanceof FieldInstruction f && f.opcode()==Opcode.PUTSTATIC && f.owner().asSymbol().equals(type) &&
                    f.typeSymbol().equals(array) && f.name().equalsString("$VALUES")){
                hits[0]++;code.invokestatic(bridge,"append",MethodTypeDesc.of(array,array));
            }
            code.with(e);
        });
        var output=cf.transformClass(cf.parse(bytes),append.andThen(ClassTransform.endHandler(b->
            b.withMethodBody("<init>",MethodTypeDesc.of(CD_void,CD_String,CD_int,key,CD_Class),ClassFile.ACC_PRIVATE,c->
                c.aload(0).aload(1).iload(2).aconst_null().aload(4).iconst_m1().iconst_1()
                 .invokespecial(type,"<init>",MethodTypeDesc.of(CD_void,CD_String,CD_int,CD_String,CD_Class,CD_int,CD_boolean))
                 .aload(0).aload(3).putfield(type,"key",key).aload(0).aload(3).invokevirtual(key,"toString",MethodTypeDesc.of(CD_String)).putfield(type,"name",CD_String).return_()))));
        if(hits[0]!=1)throw new IllegalStateException("Unexpected Bukkit EntityType initializer");
        return output;
    }
}
