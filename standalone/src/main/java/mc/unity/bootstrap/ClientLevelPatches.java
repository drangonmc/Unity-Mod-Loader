package mc.unity.bootstrap;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

final class ClientLevelPatches {
    private static final ClassDesc EVENTS=ClassDesc.of("mc.unity.core.Events");
    private static final String CL="net/minecraft/client/multiplayer/ClientLevel", CHUNK="Lnet/minecraft/world/level/chunk/LevelChunk;";
    private static MethodTypeDesc sig(String s){return MethodTypeDesc.ofDescriptor(s);}
    static void add(TransformPlan plan){
        plan.add(CL,250,"Unity client level","tick",(cf,m)->{
            int[] hits={0,0};var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(v->v.methodName().equalsString("tick") && v.methodType().equalsString("(Ljava/util/function/BooleanSupplier;)V"),new CodeTransform(){
                void signal(CodeBuilder b,String name){b.aload(0).aload(1).invokestatic(EVENTS,name,sig("(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"));}
                public void atStart(CodeBuilder b){hits[0]++;signal(b,"clientTickPre");}
                public void accept(CodeBuilder b,CodeElement e){if(e instanceof ReturnInstruction){hits[1]++;signal(b,"clientTickPost");}b.with(e);}
            }));check(hits,1,1);return out;});
        plan.add(CL,250,"Unity client level","addEntity",(cf,m)->{
            int[] hits={0};var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(v->v.methodName().equalsString("addEntity") && v.methodType().equalsString("(Lnet/minecraft/world/entity/Entity;)V"),new CodeTransform(){
                public void atStart(CodeBuilder b){hits[0]++;var accepted=b.newLabel();b.aload(1).iconst_0().invokestatic(EVENTS,"joinEntity",sig("(Ljava/lang/Object;Z)Z")).ifne(accepted).return_().labelBinding(accepted);}
                public void accept(CodeBuilder b,CodeElement e){b.with(e);}
            }));check(hits,1);return out;});
        plan.add("net/minecraft/client/multiplayer/ClientChunkCache",250,"Unity client level","chunk lifetime",(cf,m)->{
            int[] hits={0,0};var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(v->v.methodName().equalsString("drop") || v.methodName().equalsString("replaceWithPacketData"),
                (b,e)->{
                    if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals("net/minecraft/client/multiplayer/ClientChunkCache$Storage") && i.name().equalsString("drop") && i.type().equalsString("(I"+CHUNK+")V")){
                        hits[0]++;b.dup().invokestatic(EVENTS,"chunkUnloaded",sig("("+CHUNK+")V"));
                    }
                    if(e instanceof ReturnInstruction ret && ret.opcode()==Opcode.ARETURN){hits[1]++;b.dup().invokestatic(EVENTS,"clientChunkLoaded",sig("("+CHUNK+")V"));}
                    b.with(e);
                }));check(hits,1,2);return out;});
        plan.add("net/minecraft/client/multiplayer/MultiPlayerGameMode",250,"Unity client level","destroyBlock",(cf,m)->{
            int[] hits={0,0};var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(v->v.methodName().equalsString("destroyBlock") && v.methodType().equalsString("(Lnet/minecraft/core/BlockPos;)Z"),(b,e)->{
                if(e instanceof InvokeInstruction i && i.name().equalsString("blockActionRestricted")){
                    hits[0]++;b.invokestatic(EVENTS,"restrictedClientBreak",sig("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/GameType;)Z"));
                }else if(e instanceof InvokeInstruction i && i.name().equalsString("canDestroyBlock")){
                    hits[1]++;b.pop().pop().pop().pop().pop().iconst_1();
                }else b.with(e);
            }));check(hits,1,1);return out;});
    }
    private static void check(int[] hits,int... expected)throws IOException {
        if(!java.util.Arrays.equals(hits,expected))throw new IOException("26.2 client level anchors "+java.util.Arrays.toString(hits));
    }
}
