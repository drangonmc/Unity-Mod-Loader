package mc.unity.bootstrap;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.*;
import static java.lang.constant.ConstantDescs.*;

final class SocialPatches {
    private static final ClassDesc HOOK=ClassDesc.of("mc.unity.runtime.SocialHooks");
    private static final String GAME="net/minecraft/server/network/ServerGamePacketListenerImpl",PLAYER="net/minecraft/server/level/ServerPlayer";
    private static final String P="L"+PLAYER+";",TRANS="Lnet/minecraft/world/level/portal/TeleportTransition;";
    private static MethodTypeDesc sig(String desc){return MethodTypeDesc.ofDescriptor(desc);}
    interface Patch {void emit(CodeBuilder b,InvokeInstruction call);}
    private static void invoke(TransformPlan plan,String owner,String method,String target,String called,String descriptor,Patch patch){
        plan.add(owner,220,"Unity player events",method,(cf,model)->{
            int[] hits={0};
            var data=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString(method),(b,e)->{
                if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals(target) && i.name().equalsString(called) && i.type().equalsString(descriptor)){
                    hits[0]++;patch.emit(b,i);
                }else b.with(e);
            }));
            if(hits[0]!=1)throw new IOException("Expected one "+owner+"."+method+" call to "+called+", found "+hits[0]);return data;
        });
    }
    static void add(TransformPlan plan){
        String list="net/minecraft/server/players/PlayerList";
        String args="(Lnet/minecraft/network/chat/PlayerChatMessage;"+P+"Lnet/minecraft/network/chat/ChatType$Bound;)V";
        invoke(plan,GAME,"broadcastChatMessage",list,"broadcastChatMessage",args,(b,i)->
            b.invokestatic(HOOK,"chat",sig("(L"+list+";"+args.substring(1))));
        for(var name:List.of("performSignedChatCommand","performUnsignedChatCommand")){
            var command="net/minecraft/commands/Commands";var desc="(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V";
            invoke(plan,GAME,name,command,"performCommand",desc,(b,i)->b.aload(0).getfield(ClassDesc.ofInternalName(GAME),"player",ClassDesc.ofInternalName(PLAYER))
                .invokestatic(HOOK,"command",sig("(L"+command+";Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;"+P+")V")));
        }
        String td="(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z";
        invoke(plan,"net/minecraft/server/commands/TeleportCommand","performTeleport","net/minecraft/world/entity/Entity","teleportTo",td,(b,i)->
            b.ldc("COMMAND").invokestatic(HOOK,"teleportTo",sig("(Lnet/minecraft/world/entity/Entity;"+td.substring(1,td.length()-2)+"Ljava/lang/String;)Z")));
        invoke(plan,PLAYER,"teleportTo",GAME,"teleport","(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
            (b,call)->b.aload(0).invokestatic(HOOK,"directTeleport",sig("(L"+GAME+";Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;"+P+")V")));
        plan.add(PLAYER,220,"Unity player events","teleport transition",(cf,model)->{
            int[] hits={0};var data=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("teleport") && m.methodType().equalsString("("+TRANS+")"+P),new CodeTransform(){
                public void atStart(CodeBuilder b){hits[0]++;var valid=b.newLabel();b.aload(0).aload(1).invokestatic(HOOK,"teleport",sig("("+P+TRANS+")"+TRANS))
                    .astore(1).aload(1).ifnonnull(valid).aconst_null().areturn().labelBinding(valid);}
                public void accept(CodeBuilder b,CodeElement e){b.with(e);}
            }));if(hits[0]!=1)throw new IOException("Missing player teleport transition");return data;
        });
        plan.add(GAME,220,"Unity player events","accepted movement",(cf,model)->{
            var m=model.methods().stream().filter(v->v.methodName().equalsString("handleMovePlayer")).findFirst().orElseThrow();
            var slots=new HashMap<String,Integer>();
            for(var e:m.code().orElseThrow().elementList())if(e instanceof LocalVariable v && v.type().equalsString("D"))
                slots.put(v.name().stringValue(),v.slot());
            if(!slots.keySet().containsAll(List.of("startX","startY","startZ")))throw new IOException("Missing original player coordinates");
            int[] hits={0};var data=cf.transformClass(model,ClassTransform.transformingMethodBodies(v->v.methodName().equalsString("handleMovePlayer"),(b,e)->{
                if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals(PLAYER) && i.name().equalsString("absSnapTo") && i.type().equalsString("(DDDFF)V") && ++hits[0]==2){
                    var ok=b.newLabel();b.dload(slots.get("startX")).dload(slots.get("startY")).dload(slots.get("startZ"))
                        .invokestatic(HOOK,"move",sig("("+P+"DDDFFDDD)Z")).ifne(ok).return_().labelBinding(ok);
                }else b.with(e);
            }));if(hits[0]!=2)throw new IOException("Unexpected accepted movement anchors "+hits[0]);return data;
        });
    }
}
