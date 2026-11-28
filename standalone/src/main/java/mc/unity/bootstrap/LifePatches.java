package mc.unity.bootstrap;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;

final class LifePatches {
    private static final ClassDesc HOOK=ClassDesc.of("mc.unity.runtime.LifeHooks");
    private static final String PLAYER="net/minecraft/server/level/ServerPlayer",P="L"+PLAYER+";", SL="Lnet/minecraft/server/level/ServerLevel;",
        SOURCE="Lnet/minecraft/world/damagesource/DamageSource;",M="Lnet/minecraft/world/inventory/AbstractContainerMenu;",
        MP="Lnet/minecraft/world/MenuProvider;",T="Lnet/minecraft/world/level/portal/TeleportTransition;";
    private static MethodTypeDesc sig(String s){return MethodTypeDesc.ofDescriptor(s);}
    interface Body {void transform(CodeBuilder b,CodeElement e,int[] hits);}
    private static void body(TransformPlan plan,String owner,String method,String desc,int count,Body fn){
        plan.add(owner,260,"Unity player lifecycle",method,(cf,model)->{
            int[] hits={0};var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString(method)&&m.methodType().equalsString(desc),(b,e)->fn.transform(b,e,hits)));
            if(hits[0]!=count)throw new IOException(owner+"."+method+" expected "+count+" anchors, got "+hits[0]);return bytes;
        });
    }
    private static void head(TransformPlan plan,String owner,String method,String desc,java.util.function.Consumer<CodeBuilder> inject){
        plan.add(owner,261,"Unity player lifecycle",method,(cf,model)->{
            int[] hits={0};var data=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString(method)&&m.methodType().equalsString(desc),new CodeTransform(){
                public void atStart(CodeBuilder b){hits[0]++;inject.accept(b);}
                public void accept(CodeBuilder b,CodeElement e){b.with(e);}
            }));if(hits[0]!=1)throw new IOException(owner+"."+method+" missing");return data;
        });
    }
    private static boolean call(CodeElement e,String name){return e instanceof InvokeInstruction i && i.name().equalsString(name);}
    static void add(TransformPlan plan){
        var opt=ClassDesc.of("java.util.OptionalInt");
        body(plan,PLAYER,"openMenu","("+MP+")Ljava/util/OptionalInt;",1,(b,e,h)->{
            b.with(e);if(call(e,"createMenu")){h[0]++;var ok=b.newLabel();b.dup().aload(0).aload(1).invokestatic(HOOK,"open",sig("("+M+P+MP+")Z")).ifne(ok)
                .pop().invokestatic(opt,"empty",MethodTypeDesc.of(opt)).areturn().labelBinding(ok);}
        });
        head(plan,PLAYER,"doCloseContainer","()V",b->{var go=b.newLabel();b.aload(0).invokestatic(HOOK,"closing",sig("("+P+")Z")).ifne(go).return_().labelBinding(go);});
        body(plan,"net/minecraft/server/players/PlayerList","respawn","("+P+"ZLnet/minecraft/world/entity/Entity$RemovalReason;)"+P,1,(b,e,h)->{
            b.with(e);if(call(e,"findRespawnPositionAndUseSpawnBlock")){h[0]++;b.aload(1).iload(2).invokestatic(HOOK,"respawn",sig("("+T+P+"Z)"+T));}
        });
        body(plan,PLAYER,"restoreFrom","("+P+"Z)V",1,(b,e,h)->{
            if(e instanceof ReturnInstruction){h[0]++;b.aload(0).aload(1).iload(2).invokestatic(HOOK,"restored",sig("("+P+P+"Z)V"));}b.with(e);
        });
        head(plan,"net/minecraft/world/entity/player/Player","dropEquipment","("+SL+")V",b->{var go=b.newLabel();b.aload(0).invokestatic(HOOK,"equipment",sig("(Lnet/minecraft/world/entity/player/Player;)Z")).ifeq(go).return_().labelBinding(go);});
        head(plan,"net/minecraft/world/entity/LivingEntity","dropExperience","("+SL+"Lnet/minecraft/world/entity/Entity;)V",
            b->{var go=b.newLabel();b.aload(0).aload(1).aload(2).invokestatic(HOOK,"experience",sig("(Lnet/minecraft/world/entity/LivingEntity;"+SL+"Lnet/minecraft/world/entity/Entity;)Z")).ifeq(go).return_().labelBinding(go);});
        boolean[] show={false};
        body(plan,PLAYER,"die","("+SOURCE+")V",2,(b,e,h)->{
            b.with(e);if(call(e,"getDeathMessage")){h[0]++;b.aload(0).invokestatic(HOOK,"message",sig("(Lnet/minecraft/network/chat/Component;"+P+")Lnet/minecraft/network/chat/Component;"));}
            if(e instanceof FieldInstruction field && field.name().equalsString("SHOW_DEATH_MESSAGES"))show[0]=true;
            if(show[0] && call(e,"booleanValue")){show[0]=false;h[0]++;b.aload(0).invokestatic(HOOK,"show",sig("(Z"+P+")Z"));}
        });
    }
}
