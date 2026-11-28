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
        static final int[] ORDINALS = ordinals();
        private static int[] ordinals() {
            int[] ids = new int[Material.values().length];
            Arrays.fill(ids,-1);
            for (int i=0;i<ALL.length;i++) ids[ALL[i].ordinal()]=i;
            return ids;
        }
    }
    public static Material[] values() { return Values.ALL.clone(); }
    public static int ordinal(Material material) {
        int id=Values.ORDINALS[material.ordinal()];
        if(id<0)throw new IllegalArgumentException("Legacy material "+material);
        return id;
    }
    static byte[] transform(byte[] bytes,ClassLoader loader) {
        var cf=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
        var model=cf.parse(bytes);
        boolean rewrite=false;
        for(var entry:model.constantPool())
            if(entry instanceof MethodRefEntry ref && ref.owner().asInternalName().equals("org/bukkit/Material") &&
                (ref.name().equalsString("values") || ref.name().equalsString("ordinal"))) { rewrite=true; break; }
        if(!rewrite)return bytes;
        var owner=ClassDesc.of("mc.unity.plugins.ModernMaterials");
        return cf.transformClass(model,ClassTransform.transformingMethodBodies(m->true,(code,e)->{
            if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals("org/bukkit/Material")){
                if(i.opcode()==Opcode.INVOKESTATIC && i.name().equalsString("values") && i.type().equalsString("()[Lorg/bukkit/Material;")) {
                    code.invokestatic(owner,"values",i.typeSymbol());return;
                }
                if(i.opcode()==Opcode.INVOKEVIRTUAL && i.name().equalsString("ordinal") && i.type().equalsString("()I")) {
                    code.invokestatic(owner,"ordinal",i.typeSymbol().insertParameterTypes(0,ClassDesc.of("org.bukkit.Material")));return;
                }
            }
            code.with(e);
        }));
    }
}
