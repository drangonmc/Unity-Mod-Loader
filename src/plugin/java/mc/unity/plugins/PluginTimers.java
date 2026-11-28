package mc.unity.plugins;

import java.lang.classfile.*;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.*;
import java.util.Timer;

public final class PluginTimers {
    private PluginTimers(){}
    public static void track(Timer timer,Class<?> owner){
        if(owner.getClassLoader() instanceof PluginFile.Loader loader)loader.ownTimer(timer);
    }
    static byte[] migration(byte[] original,ClassLoader loader){
        var cf=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
        var model=cf.parse(original);
        var timer=ClassDesc.of("java.util.Timer");
        int[] attached={0};
        var result=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("<init>"),(b,e)->{
            if(e instanceof FieldInstruction field && field.opcode()==Opcode.PUTFIELD && field.owner().asInternalName().equals(model.thisClass().asInternalName())
                    && field.name().equalsString("timer") && field.typeSymbol().equals(timer)){
                attached[0]++;b.dup().ldc(model.thisClass().asSymbol()).invokestatic(ClassDesc.of("mc.unity.plugins.PluginTimers"),"track",
                    MethodTypeDesc.of(ConstantDescs.CD_void,timer,ConstantDescs.CD_Class));
            }
            b.with(e);
        }));
        if(attached[0]==0 && model.fields().stream().anyMatch(f->f.fieldName().equalsString("timer") && f.fieldTypeSymbol().equals(timer)))throw new IllegalStateException("Changed WorldGuard timer initialization");
        return result;
    }
}
