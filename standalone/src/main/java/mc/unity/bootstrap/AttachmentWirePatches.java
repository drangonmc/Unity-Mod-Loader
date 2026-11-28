package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
final class AttachmentWirePatches {
    private static final ClassDesc W=ClassDesc.of("net.neoforged.neoforge.attachment.AttachmentWire");
    private static final String P="Lnet/minecraft/server/level/ServerPlayer;",C="Lnet/minecraft/world/level/chunk/LevelChunk;";
    interface Insert {void write(CodeBuilder b);}
    private static void tail(TransformPlan plan,String owner,String name,String desc,Insert code){
        plan.add(owner,267,"Unity attachment tracking",name,(cf,model)->{
            int[] hits={0};var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString(name) && m.methodType().equalsString(desc),(b,e)->{
                if(e instanceof ReturnInstruction){hits[0]++;code.write(b);}b.with(e);
            }));if(hits[0]!=1)throw new IOException("Tracking anchor "+owner+"."+name+" = "+hits[0]);return bytes;
        });
    }
    static void add(TransformPlan plan){
        plan.add("net/minecraft/server/level/ServerPlayer",267,"Unity attachment tracking","dimension change",(cf,m)->{
            int[] hits={0};var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(v->v.methodName().equalsString("teleport") &&
                v.methodType().equalsString("(Lnet/minecraft/world/level/portal/TeleportTransition;)"+P),(b,e)->{
                b.with(e);if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals("net/minecraft/server/players/PlayerList") && i.name().equalsString("sendLevelInfo")){
                    hits[0]++;b.aload(0).invokestatic(W,"self",MethodTypeDesc.ofDescriptor("("+P+")V"));
                }
            }));if(hits[0]!=1)throw new IOException("Cross-dimension self sync anchor");return bytes;
        });
        String entity="net/minecraft/server/level/ServerEntity";var type=ClassDesc.of("net.minecraft.world.entity.Entity");
        for(String method:new String[]{"addPairing","removePairing"})tail(plan,entity,method,"("+P+")V",
            b->b.aload(0).getfield(ClassDesc.ofInternalName(entity),"entity",type).aload(1)
                .invokestatic(W,method.equals("addPairing")?"pair":"unpair",MethodTypeDesc.ofDescriptor("(Lnet/minecraft/world/entity/Entity;"+P+")V")));
        tail(plan,"net/minecraft/server/network/PlayerChunkSender","sendChunk","(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;"+C+")V",
            b->b.aload(2).aload(0).getfield(ClassDesc.of("net.minecraft.server.network.ServerGamePacketListenerImpl"),"player",ClassDesc.of("net.minecraft.server.level.ServerPlayer"))
                .invokestatic(W,"chunkSent",MethodTypeDesc.ofDescriptor("("+C+P+")V")));
        plan.add("net/minecraft/server/players/PlayerList",267,"Unity attachment tracking","self spawn",(cf,model)->{
            int[] hits={0,0};var data=cf.transformClass(model,ClassTransform.transformingMethods(m->m.methodName().equalsString("respawn")||m.methodName().equalsString("placeNewPlayer"),(builder,element)->{
                if(!(element instanceof CodeModel code)){builder.with(element);return;}
                boolean login=code.parent().orElseThrow().methodName().equalsString("placeNewPlayer");
                builder.transformCode(code,(b,e)->{
                    if(!login && e instanceof ReturnInstruction){hits[1]++;b.dup().invokestatic(W,"self",MethodTypeDesc.ofDescriptor("("+P+")V"));}
                    b.with(e);
                    if(login && e instanceof InvokeInstruction i && i.name().equalsString("resumeFlushing")){
                        hits[0]++;b.aload(2).invokestatic(W,"self",MethodTypeDesc.ofDescriptor("("+P+")V"));
                    }
                });
            }));if(hits[0]!=1 || hits[1]!=1)throw new IOException("Self attachment anchors "+java.util.Arrays.toString(hits));return data;
        });
    }
}
