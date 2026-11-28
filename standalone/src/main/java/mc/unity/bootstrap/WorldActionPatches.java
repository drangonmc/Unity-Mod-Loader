package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
final class WorldActionPatches {
    private static final ClassDesc H=ClassDesc.of("mc.unity.runtime.WorldActions");
    private static MethodTypeDesc sig(String value){return MethodTypeDesc.ofDescriptor(value);}
    static void add(TransformPlan plan){
        plan.add("net/minecraft/world/entity/player/Player",260,"Unity machine actors","isFakePlayer",(cf,m)->{
            if(m.methods().stream().anyMatch(x->x.methodName().equalsString("isFakePlayer")))throw new IOException("Existing isFakePlayer");
            return cf.transformClass(m,ClassTransform.endHandler(b->b.withMethodBody("isFakePlayer",sig("()Z"),ClassFile.ACC_PUBLIC,
                c->c.aload(0).invokestatic(ClassDesc.of("mc.unity.api.MachineActors"),"isMachine",sig("(Lnet/minecraft/world/entity/player/Player;)Z")).ireturn())));
        });
        plan.add("net/minecraft/server/PlayerAdvancements",260,"Unity machine actors","load",(cf,m)->{
            int[] hits={0};var data=cf.transformClass(m,ClassTransform.transformingMethods(v->v.methodName().equalsString("load") &&
                v.methodType().equalsString("(Lnet/minecraft/server/ServerAdvancementManager;)V"),(b,e)->{
                if(e instanceof AccessFlags flags){hits[0]++;b.withFlags((flags.flagsMask() & ~ClassFile.ACC_PRIVATE)|ClassFile.ACC_PROTECTED);}else b.with(e);
            }));if(hits[0]!=1)throw new IOException("PlayerAdvancements.load access");return data;
        });
        plan.add("net/minecraft/server/players/PlayerList",260,"Unity machine actors","advancements",(cf,m)->{
            int[] hits={0};var data=cf.transformClass(m,ClassTransform.transformingMethodBodies(v->v.methodName().equalsString("getPlayerAdvancements") &&
                v.methodType().equalsString("(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/PlayerAdvancements;"),new CodeTransform(){
                public void atStart(CodeBuilder b){hits[0]++;var real=b.newLabel();b.aload(1).instanceOf(ClassDesc.of("net.neoforged.neoforge.common.util.FakePlayer"))
                    .ifeq(real).aload(0).aload(1).invokestatic(ClassDesc.of("mc.unity.api.MachineActors"),"advancements",
                        sig("(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/PlayerAdvancements;")).areturn().labelBinding(real);}
                public void accept(CodeBuilder b,CodeElement e){b.with(e);}
            }));if(hits[0]!=1)throw new IOException("FakePlayer advancements anchor");return data;
        });
        plan.add("net/minecraft/world/entity/EntityType",260,"Unity spawn events","create reason",(cf,model)->{
            int[] hits={0};
            var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("create") &&
                m.methodType().equalsString("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Lnet/minecraft/world/entity/Entity;"),(b,e)->{
                if(e instanceof ReturnInstruction){hits[0]++;b.dup().aload(2).invokevirtual(ClassDesc.of("net.minecraft.world.entity.EntitySpawnRequest"),"reason",sig("()Lnet/minecraft/world/entity/EntitySpawnReason;")).invokestatic(H,"remember",sig("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntitySpawnReason;)V"));}b.with(e);
            }));if(hits[0]!=2)throw new IOException("Entity construction anchor");return bytes;
        });
        for(var simple:new String[]{"BedBlock","RespawnAnchorBlock"}){
            plan.add("net/minecraft/world/level/block/"+simple,260,"Unity explosion events","original block",(cf,model)->{
                int[] hits={0};var data=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString(simple.equals("BedBlock")?"useWithoutItem":"explode"),(b,e)->{
                    b.with(e);if(e instanceof InvokeInstruction i && i.name().equalsString("badRespawnPointExplosion")){
                        hits[0]++;b.aload(1).invokestatic(H,"blockSource",sig("(Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/damagesource/DamageSource;"));
                    }
                }));if(hits[0]!=1)throw new IOException("Missing "+simple+" explosion source");return data;
            });
        }
        plan.add("net/minecraft/world/level/ServerExplosion",260,"Unity explosion events","blocks after damage",(cf,model)->{
            var method=model.methods().stream().filter(m->m.methodName().equalsString("explode")).findFirst().orElseThrow();
            var list=method.code().orElseThrow().elementStream().filter(LocalVariable.class::isInstance).map(LocalVariable.class::cast)
                .filter(v->v.type().equalsString("Ljava/util/List;")).findFirst().orElseThrow();
            int[] hits={0};var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("explode"),(b,e)->{
                b.with(e);if(e instanceof InvokeInstruction i && i.name().equalsString("hurtEntities")){hits[0]++;b.aload(0).aload(list.slot())
                    .invokestatic(H,"explosions",sig("(Lnet/minecraft/world/level/ServerExplosion;Ljava/util/List;)Ljava/util/List;")).astore(list.slot());}
            }));if(hits[0]!=1)throw new IOException("Explosion phase anchor");return bytes;
        });
        plan.add("net/minecraft/world/level/block/state/BlockBehaviour",260,"Unity explosion events","loot yield",(cf,model)->{
            int[] hits={0,0,0};var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("onExplosionHit"),(b,e)->{
                if(e instanceof InvokeInstruction i && i.name().equalsString("getBlockInteraction") && ++hits[0]==2)
                    b.invokestatic(H,"decay",sig("(Lnet/minecraft/world/level/Explosion;)Lnet/minecraft/world/level/Explosion$BlockInteraction;"));
                else if(e instanceof InvokeInstruction i && i.name().equalsString("dropFromExplosion")){
                    hits[2]++;b.invokestatic(H,"drop",sig("(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/Explosion;)Z"));
                }else if(e instanceof InvokeInstruction i && i.name().equalsString("radius")){
                    hits[1]++;b.invokestatic(H,"radius",sig("(Lnet/minecraft/world/level/Explosion;)F"));
                }else b.with(e);
            }));if(hits[0]!=2 || hits[1]!=1 || hits[2]!=1)throw new IOException("Explosion loot anchors");return bytes;
        });
    }
}
