package mc.unity.bootstrap;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.*;
import java.util.jar.JarFile;

/** Checked before the server ticks. Preserve the author's pathfinding, filters, drops and transactions. */
final class MachineModPatches {
    private static final ClassDesc ACCESS=ClassDesc.of("mc.unity.runtime.MachineAccess");
    private static final String QUARRY="net/mego/quietquarry/block/QuarryCoreBlockEntity";
    private static final String DRONE="com/guilei12/dronemod/entity/DroneEntity", GOAL=DRONE+"$DroneWorkGoal", ITEM="com/guilei12/dronemod/item/DroneItem";
    private static final String POS="Lnet/minecraft/core/BlockPos;",LEVEL="Lnet/minecraft/world/level/Level;",SL="Lnet/minecraft/server/level/ServerLevel;",
        STATE="Lnet/minecraft/world/level/block/state/BlockState;",ENTITY="Lnet/minecraft/world/entity/Entity;",BE="Lnet/minecraft/world/level/block/entity/BlockEntity;";
    private static MethodTypeDesc sig(String s){return MethodTypeDesc.ofDescriptor(s);}
    static void prepare(Collection<JarFile> jars,Collection<?> entries,Map<String,byte[]> patches,ClassLoader loader)throws Exception {
        var machines=new HashMap<java.nio.file.Path,List<String>>();
        for(var entry:entries){
            String id=(String)entry.getClass().getMethod("id").invoke(entry);
            var file=(java.nio.file.Path)entry.getClass().getMethod("file").invoke(entry);
            if(id.equals("quietquarry"))machines.put(file,List.of(QUARRY));
            if(id.equals("logisticsdrones"))machines.put(file,List.of(GOAL,ITEM));
        }
        var cf=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(loader)));
        for(var jar:jars){
            for(var name:machines.getOrDefault(java.nio.file.Path.of(jar.getName()),List.of())){
                var entry=jar.getJarEntry(name+".class");if(entry==null)throw new IOException("Missing required machine protection anchor "+jar.getName()+"!/"+name);
                byte[] data=patches.get(name);
                if(data==null)try(var in=jar.getInputStream(entry)){data=in.readAllBytes();}
                var model=cf.parse(data);
                if(name.equals(QUARRY))data=quarry(cf,model);else if(name.equals(GOAL))data=drone(cf,model);else data=item(cf,model);
                patches.put(name,data);
            }
        }
    }
    private static byte[] quarry(ClassFile cf,ClassModel model)throws IOException{
        int[] count={0,0,0};String descriptor="("+SL+"Lnet/mego/quietquarry/quarry/FrameValidator$FrameArea;)V";
        var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("mineNextBlock") && m.methodType().equalsString(descriptor),(b,e)->{
            if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals(QUARRY) && i.name().equalsString("computeDrops") && i.type().equalsString("("+SL+POS+STATE+")Ljava/util/List;")){
                if(count[1]!=0 || count[2]!=0)throw new IllegalStateException("Quarry mutation precedes authorization");
                count[0]++;int state=b.allocateLocal(TypeKind.REFERENCE);var ok=b.newLabel();
                b.astore(state).dup2().aload(0).aload(state).invokestatic(ACCESS,"mine",sig("("+SL+POS+BE+STATE+")Z")).ifne(ok)
                    .pop().pop().pop().return_().labelBinding(ok).aload(state);
            }
            if(e instanceof InvokeInstruction i && i.name().equalsString("insertDrops"))count[1]++;
            if(e instanceof InvokeInstruction i && i.name().equalsString("destroyBlock"))count[2]++;
            b.with(e);
        }));
        if(!Arrays.equals(count,new int[]{1,1,1}))throw new IOException("Unsupported QuietQuarry mining contract "+Arrays.toString(count));return bytes;
    }
    private static byte[] drone(ClassFile cf,ClassModel model)throws IOException{
        int[] count={0,0,0};var methods=List.of("hasInputToCollect","collectItems","depositItems");
        var drone=ClassDesc.ofInternalName(DRONE);var goal=ClassDesc.ofInternalName(GOAL);var pos=ClassDesc.of("net.minecraft.core.BlockPos");
        var bytes=cf.transformClass(model,ClassTransform.transformingMethods(m->methods.contains(m.methodName().stringValue()) && m.methodType().equalsString("()Z"),(builder,element)->{
            if(!(element instanceof CodeModel code)){builder.with(element);return;}
            int idx=methods.indexOf(code.parent().orElseThrow().methodName().stringValue());
            builder.transformCode(code,(b,e)->{
                if(e instanceof InvokeInstruction i && i.name().equalsString("itemHandlerAt") && i.owner().asInternalName().equals(DRONE) &&
                        i.type().equalsString("("+LEVEL+POS+")Lnet/neoforged/neoforge/transfer/ResourceHandler;")){
                    count[idx]++;var ok=b.newLabel();b.dup2().aload(0).getfield(goal,"this$0",drone).dup()
                        .invokevirtual(drone,"getOwnerUUID",sig("()Ljava/util/UUID;")).aload(0).getfield(goal,"this$0",drone)
                        .getfield(drone,"targetOutput",pos).loadConstant(idx==2?0:1)
                        .invokestatic(ACCESS,"transfer",sig("("+LEVEL+POS+ENTITY+"Ljava/util/UUID;"+POS+"Z)Z"))
                        .ifne(ok).pop().pop().iconst_0().ireturn().labelBinding(ok);
                }b.with(e);
            });
        }));
        if(!Arrays.equals(count,new int[]{1,1,1}))throw new IOException("Unsupported Logistics Drones transfer contract "+Arrays.toString(count));return bytes;
    }
    private static byte[] item(ClassFile cf,ClassModel model)throws IOException{
        int[] count={0};var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("useOn"),(b,e)->{
            if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals(DRONE) && i.name().equalsString("getOwnerUUID") && i.type().equalsString("()Ljava/util/UUID;")){
                count[0]++;b.dup().aload(1).invokestatic(ACCESS,"deployed",sig("("+ENTITY+"Lnet/minecraft/world/item/context/UseOnContext;)V"));
            }b.with(e);
        }));if(count[0]!=1)throw new IOException("Unsupported drone deployment contract");return bytes;
    }
}
