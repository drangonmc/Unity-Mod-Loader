package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
final class PortalPatches {
    private static final ClassDesc H=ClassDesc.of("mc.unity.runtime.PortalHooks"), PLAN=H.nested("Plan");
    private static final String P=PLAN.descriptorString(),W="Lnet/minecraft/server/level/ServerLevel;",B="Lnet/minecraft/core/BlockPos;",
        E="Lnet/minecraft/world/entity/Entity;", BORDER="Lnet/minecraft/world/level/border/WorldBorder;",F="Lnet/minecraft/world/level/portal/PortalForcer;",T="Lnet/minecraft/world/level/portal/TeleportTransition;";
    private static MethodTypeDesc sig(String v){return MethodTypeDesc.ofDescriptor(v);}
    static void add(TransformPlan plan){
        plan.add("net/minecraft/world/level/block/NetherPortalBlock",269,"Unity portal search","getExitPortal",(cf,model)->{
            int[] hits={0,0,0};var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("getExitPortal") &&
                m.methodType().equalsString("("+W+E+B+B+"Z"+BORDER+")"+T),new CodeTransform(){
                int slot;
                public void atStart(CodeBuilder b){
                    hits[0]++;slot=b.allocateLocal(TypeKind.REFERENCE);var ok=b.newLabel();
                    b.aload(1).aload(2).aload(4).iload(5).invokestatic(H,"nether",sig("("+W+E+B+"Z)"+P)).astore(slot)
                        .aload(slot).ifnonnull(ok).aconst_null().areturn().labelBinding(ok).aload(slot).invokevirtual(PLAN,"world",sig("()"+W)).astore(1)
                        .aload(slot).invokestatic(H,"destination",sig("("+P+")"+B)).astore(4).aload(slot).invokestatic(H,"netherDimension",sig("("+P+")Z")).istore(5)
                        .aload(1).invokevirtual(ClassDesc.of("net.minecraft.server.level.ServerLevel"),"getWorldBorder",sig("()"+BORDER)).astore(6);
                }
                public void accept(CodeBuilder b,CodeElement e){
                    if(e instanceof InvokeInstruction i && i.name().equalsString("findClosestPortalPosition")){hits[1]++;b.aload(slot).invokestatic(H,"find",sig("("+F+B+"Z"+BORDER+P+")Ljava/util/Optional;"));}
                    else if(e instanceof InvokeInstruction i && i.name().equalsString("createPortal")){hits[2]++;b.aload(slot).invokestatic(H,"create",sig("("+F+B+"Lnet/minecraft/core/Direction$Axis;"+P+")Ljava/util/Optional;"));}
                    else b.with(e);
                }
            }));if(!java.util.Arrays.equals(hits,new int[]{1,1,1}))throw new IOException("Nether portal anchors");return bytes;
        });
        plan.add("net/minecraft/world/level/portal/PortalForcer",269,"Unity portal search","radii",(cf,model)->{
            int[] hits={0,0};var bytes=cf.transformClass(model,ClassTransform.transformingMethods(m->m.methodName().equalsString("createPortal") || m.methodName().equalsString("findClosestPortalPosition"),(mb,element)->{
                if(!(element instanceof CodeModel code)){mb.with(element);return;}
                boolean create=code.parent().orElseThrow().methodName().equalsString("createPortal");
                mb.transformCode(code,(b,e)->{b.with(e);if(e instanceof ConstantInstruction k && (Integer.valueOf(16).equals(k.constantValue()) || !create && Integer.valueOf(128).equals(k.constantValue()))){
                    hits[create?1:0]++;b.invokestatic(H,"radius",sig("(I)I"));
                }});
            }));if(!java.util.Arrays.equals(hits,new int[]{2,1}))throw new IOException("Portal radius anchors");return bytes;
        });
        plan.add("net/minecraft/world/level/block/EndPortalBlock",269,"Unity portal search","end destination",(cf,model)->{
            int[] hits={0,0};
            byte[] bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("getPortalDestination"),new CodeTransform(){
                int pos;
                public void atStart(CodeBuilder b){pos=b.allocateLocal(TypeKind.REFERENCE);b.aconst_null().astore(pos);}
                public void accept(CodeBuilder b,CodeElement e){
                    if(e instanceof InvokeInstruction call && call.owner().asInternalName().equals("net/minecraft/world/level/levelgen/feature/EndPlatformFeature") &&
                            call.name().equalsString("createEndPlatform") && call.type().equalsString("(Lnet/minecraft/world/level/ServerLevelAccessor;"+B+"Z)V")){
                        hits[0]++;b.pop().astore(pos).pop();return;
                    }
                    if(e instanceof ReturnInstruction){hits[1]++;b.aload(2).aload(pos).invokestatic(H,"end",sig("("+T+E+B+")"+T));}
                    b.with(e);
                }
            }));
            if(!java.util.Arrays.equals(hits,new int[]{1,3}))throw new IOException("End portal creation/transition anchors");
            return bytes;
        });
    }
}
