package mc.unity.plugins;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.util.Arrays;
import org.bukkit.Material;

/** Modern Bukkit plugins see the non-legacy Material universe and its matching ordinal space. */
public final class ModernMaterials {
    private static final class Values {
        static final Material[] ALL = Arrays.stream(Material.values()).filter(m -> !m.isLegacy()).toArray(Material[]::new);
    }
    public static Material[] values(){return Values.ALL.clone();}
    static byte[] transform(byte[] bytes,ClassLoader loader) {
        var cf=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
        var model=cf.parse(bytes);
        boolean rewrite=false;
        for(var entry:model.constantPool())
            if(entry instanceof MethodRefEntry ref && ref.owner().asInternalName().equals("org/bukkit/Material") &&
                ref.name().equalsString("values")) { rewrite=true; break; }
        if(!rewrite)return bytes;
        var owner=ClassDesc.of("mc.unity.plugins.ModernMaterials");
        boolean switchTable=model.fields().stream().anyMatch(f->f.fieldName().stringValue().startsWith("$SwitchMap$org$bukkit$Material") && f.flags().has(java.lang.reflect.AccessFlag.SYNTHETIC));
        return cf.transformClass(model,ClassTransform.transformingMethodBodies(m->!switchTable || !m.methodName().equalsString("<clinit>"),(code,e)->{
            if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals("org/bukkit/Material")){
                if(i.opcode()==Opcode.INVOKESTATIC && i.name().equalsString("values") && i.type().equalsString("()[Lorg/bukkit/Material;")) {
                    code.invokestatic(owner,"values",i.typeSymbol());return;
                }

            }
            code.with(e);
        }));
    }
}
