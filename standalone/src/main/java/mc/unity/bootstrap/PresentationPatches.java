package mc.unity.bootstrap;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.constant.*;

/** Preserve vanilla tracking and saving decisions, adding the Bukkit entity overrides first. */
final class PresentationPatches {
    static void add(TransformPlan plan){
        String storage="net/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess";
        for(boolean opening:new boolean[]{true,false}){
            String method=opening?"<init>":"close";
            plan.add(storage,218,"Bukkit world identity",method,(cf,model)->{
                int[] hits={0};
                byte[] bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString(method),(b,e)->{
                    if(e instanceof java.lang.classfile.instruction.ReturnInstruction){
                        hits[0]++;b.aload(0).invokestatic(ClassDesc.of("mc.unity.runtime.WorldOrigins"),opening?"opened":"closed",MethodTypeDesc.ofDescriptor("(L"+storage+";)V"));
                    }
                    b.with(e);
                }));
                if(hits[0]!=1)throw new IOException("Unexpected world storage anchor "+method+" "+hits[0]);
                return bytes;
            });
        }
        String entity="net/minecraft/world/entity/Entity";
        for(String name:new String[]{"broadcastToPlayer","shouldBeSaved","saveAsPassenger"}){
            boolean visibility=name.equals("broadcastToPlayer");
            String desc=visibility?"(Lnet/minecraft/server/level/ServerPlayer;)Z":
                name.equals("shouldBeSaved")?"()Z":"(Lnet/minecraft/world/level/storage/ValueOutput;)Z";
            plan.add(entity,218,"Bukkit entity presentation",name,(cf,model)->{
                int[] hit={0};
                byte[] bytes=cf.transformClass(model,ClassTransform.transformingMethods(m->m.methodName().equalsString(name) && m.methodType().equalsString(desc),
                    MethodTransform.transformingCode(new CodeTransform(){
                        public void atStart(CodeBuilder b){
                            hit[0]++;
                            var original=b.newLabel();b.aload(0);if(visibility)b.aload(1);
                            var hooks=ClassDesc.of("mc.unity.runtime.EntityPresentation");
                            var args=MethodTypeDesc.ofDescriptor("(L"+entity+";"+(visibility?"Lnet/minecraft/server/level/ServerPlayer;":"")+")Z");
                            b.invokestatic(hooks,visibility?"visible":"canSave",args).ifne(original).iconst_0().ireturn().labelBinding(original);
                        }
                        public void accept(CodeBuilder b,CodeElement e){b.with(e);}
                    })));
                if(hit[0]!=1)throw new IOException("Missing entity presentation anchor "+name+desc);
                return bytes;
            });
        }
    }
}
