package mc.unity.bootstrap;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.*;
import static java.lang.constant.ConstantDescs.*;

/** Minecraft 26.2 damage phases and per-owner loot capture. Each call-site replacement checks its shape. */
final class LivingEventPatches {
    private static final String LIVING="net/minecraft/world/entity/LivingEntity", PLAYER="net/minecraft/world/entity/player/Player",
        SERVER_PLAYER="net/minecraft/server/level/ServerPlayer", ENTITY="net/minecraft/world/entity/Entity";
    private static final ClassDesc FLOW=ClassDesc.of("mc.unity.core.LivingFlow"), DROPS=FLOW.nested("Drops"),
        MOB=ClassDesc.ofInternalName(LIVING), E=ClassDesc.ofInternalName(ENTITY), LEVEL=ClassDesc.of("net.minecraft.world.level.Level"),
        SERVER=ClassDesc.of("net.minecraft.server.level.ServerLevel"), SOURCE=ClassDesc.of("net.minecraft.world.damagesource.DamageSource");
    private static MethodTypeDesc mt(ClassDesc ret,ClassDesc... args){return MethodTypeDesc.of(ret,args);}
    private static boolean call(CodeElement e,String name){return e instanceof InvokeInstruction i && i.name().equalsString(name);}
    private static void count(int[] hits,int... expected)throws IOException{
        if(!Arrays.equals(hits,expected))throw new IOException("26.2 living hooks: "+Arrays.toString(hits)+" expected "+Arrays.toString(expected));
    }
    static void add(TransformPlan plan) {
        plan.add("net/minecraft/world/level/chunk/ChunkAccess",250,"Unity chunk events","getLevel",(cf,model)->{
            if(model.methods().stream().anyMatch(m->m.methodName().equalsString("getLevel") && m.methodTypeSymbol().equals(mt(LEVEL))))
                throw new IOException("Existing ChunkAccess.getLevel");
            return cf.transformClass(model,ClassTransform.endHandler(b->b.withMethodBody("getLevel",mt(LEVEL),ClassFile.ACC_PUBLIC,code->code.aconst_null().areturn())));
        });
        for(String owner:List.of(LIVING,PLAYER))actual(plan,owner);
        plan.add(LIVING,250,"Unity living events","hurtServer", (cf,m)->{
            int[] hits=new int[6];
            var hurt=m.methods().stream().filter(x->x.methodName().equalsString("hurtServer") && x.methodTypeSymbol().equals(mt(CD_boolean,SERVER,SOURCE,CD_float))).findFirst().orElseThrow();
            var slots=hurt.code().orElseThrow().elementStream().filter(LocalVariable.class::isInstance).map(LocalVariable.class::cast)
                .filter(v->v.name().equalsString("tookFullDamage") && v.type().equalsString("Z")).toList();
            if(slots.size()!=1)throw new IOException("Expected one tookFullDamage local");
            int effectSlot=slots.getFirst().slot();
            var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("hurtServer") &&
                method.methodTypeSymbol().equals(mt(CD_boolean,SERVER,SOURCE,CD_float)), new CodeTransform(){
                int token;Label start,end,fail; boolean excess;
                public void atStart(CodeBuilder b){
                    start=b.newLabel();end=b.newLabel();fail=b.newLabel();token=b.allocateLocal(TypeKind.REFERENCE);
                    b.aload(0).aload(2).fload(3).invokestatic(FLOW,"begin",mt(CD_Object,MOB,SOURCE,CD_float)).astore(token).labelBinding(start);
                }
                public void accept(CodeBuilder b,CodeElement e){
                    if(e instanceof InvokeInstruction i && i.name().equalsString("die") && i.owner().asInternalName().equals(LIVING)
                            && i.typeSymbol().equals(mt(CD_void,SOURCE))) {
                        hits[5]++; b.invokestatic(FLOW,"die",mt(CD_void,MOB,SOURCE)); return;
                    }
                    if(call(e,"isSleeping")){
                        hits[0]++;var ok=b.newLabel();
                        // Receiver for isSleeping is already on the stack.
                        b.fload(3).invokestatic(FLOW,"incoming",mt(CD_float,CD_float)).fstore(3)
                            .fload(3).fconst_0().fcmpg().ifge(ok).pop().iconst_0().aload(token)
                            .invokestatic(FLOW,"end",mt(CD_void,CD_Object)).ireturn().labelBinding(ok);
                    }
                    if(e instanceof FieldInstruction f && f.opcode()==Opcode.GETFIELD && f.name().equalsString("lastHurt"))excess=true;
                    if(excess && e instanceof OperatorInstruction op && op.opcode()==Opcode.FSUB){
                        hits[1]++;excess=false;
                        b.with(e).aload(0).getfield(MOB,"invulnerableTime",CD_int).invokestatic(FLOW,"excess",mt(CD_void,CD_int))
                            .fload(3).iconst_0().invokestatic(FLOW,"reduction",mt(CD_float,CD_float,CD_float,CD_int));return;
                    }
                    if(e instanceof FieldInstruction f && f.opcode()==Opcode.PUTFIELD && f.name().equalsString("invulnerableTime")){
                        hits[2]++;b.invokestatic(FLOW,"ticks",mt(CD_int,CD_int));
                    }
                    if(e instanceof StoreInstruction s && s.slot()==effectSlot && s.typeKind()==TypeKind.INT){
                        hits[3]++;b.invokestatic(FLOW,"effects",mt(CD_boolean,CD_boolean));
                    }
                    if(e instanceof ReturnInstruction){
                        b.aload(token).invokestatic(FLOW,"end",mt(CD_void,CD_Object));
                    }
                    b.with(e);
                }
                public void atEnd(CodeBuilder b){hits[4]++;b.labelBinding(end).labelBinding(fail).aload(token)
                    .invokestatic(FLOW,"end",mt(CD_void,CD_Object)).athrow().exceptionCatchAll(start,end,fail);}
            }));
            count(hits,1,1,1,2,1,1);return bytes;
        });
        plan.add(LIVING,250,"Unity living events","magic reductions",(cf,m)->{
            int[] hits={0};var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->
                method.methodName().equalsString("getDamageAfterMagicAbsorb") && method.methodTypeSymbol().equals(mt(CD_float,SOURCE,CD_float)),(b,e)->{
                if(e instanceof StoreInstruction st && st.slot()==2 && st.typeKind()==TypeKind.FLOAT){
                    int index=hits[0]++;b.fload(2).loadConstant(index==0?2:3).invokestatic(FLOW,"reduction",mt(CD_float,CD_float,CD_float,CD_int));
                }b.with(e);
            }));count(hits,2);return bytes;
        });
        plan.add(LIVING,250,"Unity living events","shield block",(cf,model)->{
            int[] hits={0,0};
            var method=model.methods().stream().filter(m->m.methodName().equalsString("applyItemBlocking")).findFirst().orElseThrow();
            var local=method.code().orElseThrow().elementStream().filter(LocalVariable.class::isInstance).map(LocalVariable.class::cast)
                .filter(v->v.name().equalsString("blocksAttacks")).findFirst().orElseThrow();
            var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("applyItemBlocking"),(b,e)->{
                b.with(e);
                if(call(e,"booleanValue")){hits[0]++;b.invokestatic(FLOW,"shieldBypassed",mt(CD_boolean,CD_boolean));}
                if(call(e,"resolveBlockedDamage")){
                    hits[1]++;var ok=b.newLabel();b.aload(local.slot()).aload(2).invokestatic(FLOW,"shield",mt(CD_float,CD_float,
                        ClassDesc.of("net.minecraft.world.item.component.BlocksAttacks"),SOURCE))
                        .dup().fconst_0().fcmpg().ifge(ok).pop().fconst_0().freturn().labelBinding(ok);
                }
            }));count(hits,1,1);return bytes;
        });
        plan.add("net/minecraft/world/item/component/BlocksAttacks",250,"Unity living events","shield durability",(cf,model)->{
            int[] hits={0};var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("hurtBlockingItem"),(b,e)->{
                b.with(e);if(e instanceof InvokeInstruction i && i.name().equalsString("apply") && i.type().equalsString("(F)I")){
                    hits[0]++;b.invokestatic(FLOW,"shieldWear",mt(CD_int,CD_int));
                }
            }));count(hits,1);return bytes;
        });
        for(String owner:List.of(LIVING,SERVER_PLAYER))death(plan,owner);
        drops(plan);
        var events=ClassDesc.of("mc.unity.core.Events");
        plan.add("net/minecraft/world/level/entity/PersistentEntitySectionManager",250,"Unity entity events","addEntity",(cf,m)->{
            int[] hits={0};var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->
                method.methodName().equalsString("addEntity") && method.methodType().equalsString("(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z"),new CodeTransform(){
                    public void atStart(CodeBuilder b){hits[0]++;var go=b.newLabel();b.aload(1).iload(2)
                        .invokestatic(events,"joinEntity",mt(CD_boolean,CD_Object,CD_boolean)).ifne(go).iconst_0().ireturn().labelBinding(go);}
                    public void accept(CodeBuilder b,CodeElement e){b.with(e);}
                }));count(hits,1);return out;
        });
        plan.add("net/minecraft/server/level/ServerLevel",250,"Unity chunk events","unload",(cf,m)->{
            var chunk=ClassDesc.of("net.minecraft.world.level.chunk.LevelChunk");
            int[] hits={0};var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->
                method.methodName().equalsString("unload") && method.methodTypeSymbol().equals(mt(CD_void,chunk)),new CodeTransform(){
                    public void atStart(CodeBuilder b){hits[0]++;b.aload(1).invokestatic(events,"chunkUnloaded",mt(CD_void,chunk));}
                    public void accept(CodeBuilder b,CodeElement e){b.with(e);}
                }));count(hits,1);return out;
        });
    }
    private static void actual(TransformPlan plan,String owner){
        plan.add(owner,250,"Unity living events","actuallyHurt",(cf,m)->{
            int[] hits=new int[5];boolean[] absorption={false};var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->
                method.methodName().equalsString("actuallyHurt") && method.methodTypeSymbol().equals(mt(CD_void,SERVER,SOURCE,CD_float)),(b,e)->{
                b.with(e);
                if(call(e,"getDamageAfterArmorAbsorb")){hits[0]++;b.fload(3).iconst_1().invokestatic(FLOW,"reduction",mt(CD_float,CD_float,CD_float,CD_int));}
                if(call(e,"getDamageAfterMagicAbsorb")){hits[1]++;b.invokestatic(FLOW,"pre",mt(CD_float,CD_float));}
                if(call(e,"getAbsorptionAmount")) absorption[0]=true;
                if(call(e,"setAbsorptionAmount")) absorption[0]=false;
                if(call(e,"setHealth")) {hits[4]++;b.invokestatic(FLOW,"post",mt(CD_void));}
                if(absorption[0] && e instanceof InvokeInstruction i && i.owner().asInternalName().equals("java/lang/Math") && i.name().equalsString("max") &&
                        i.typeSymbol().equals(mt(CD_float,CD_float,CD_float))  ) {hits[2]++; absorption[0]=false;
                    b.fload(3).iconst_4().invokestatic(FLOW,"reduction",mt(CD_float,CD_float,CD_float,CD_int));}
                if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals("mc/unity/core/Actions") && i.name().equalsString("damaged")){
                    hits[3]++;b.dup().invokestatic(FLOW,"finalAmount",mt(CD_void,CD_float));}
            }));count(hits,1,1,1,1,1);return bytes;
        });
    }
    private static void death(TransformPlan plan,String owner){
        plan.add(owner,250,"Unity living events","die",(cf,m)->{
            int[] hits={0};var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("die") &&
                method.methodTypeSymbol().equals(mt(CD_void,SOURCE)),new CodeTransform() {
                int scope; Label start,end,failure;
                public void atStart(CodeBuilder b){
                    scope=b.allocateLocal(TypeKind.REFERENCE); start=b.newLabel();end=b.newLabel();failure=b.newLabel();
                    b.aload(0).aload(1).invokestatic(FLOW,"openDeath",mt(DROPS,MOB,SOURCE)).astore(scope).labelBinding(start);
                    if(owner.equals(SERVER_PLAYER))test(b);
                }
                public void atEnd(CodeBuilder b){
                    b.labelBinding(end).aload(scope).iconst_1().invokestatic(FLOW,"finishDeath",mt(CD_void,DROPS,CD_boolean)).return_()
                     .labelBinding(failure).aload(scope).iconst_0().invokestatic(FLOW,"finishDeath",mt(CD_void,DROPS,CD_boolean))
                     .athrow().exceptionCatchAll(start,end,failure);
                }
                private void test(CodeBuilder b){hits[0]++;var go=b.newLabel();b.aload(0).aload(1)
                    .invokestatic(FLOW,"dying",mt(CD_boolean,MOB,SOURCE)).ifeq(go).goto_(end).labelBinding(go);}
                public void accept(CodeBuilder b,CodeElement e){
                    if(e instanceof ReturnInstruction){b.goto_(end);return;}
                    if(owner.equals(LIVING) && hits[0]==0 && call(e,"getEntity")){
                        // The damage source receiver is retained on the allowed branch.
                        var go=b.newLabel();hits[0]++;b.aload(0).aload(1).invokestatic(FLOW,"dying",mt(CD_boolean,MOB,SOURCE)).ifeq(go).pop().goto_(end).labelBinding(go);
                    }b.with(e);
                }
            }));count(hits,1);return bytes;
        });
    }
    private static void drops(TransformPlan plan){
        plan.add(LIVING,250,"Unity living events","collect death drops",(cf,m)->{
            int[] hits={0};var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("dropAllDeathLoot") &&
                method.methodTypeSymbol().equals(mt(CD_void,SERVER,SOURCE)),new CodeTransform(){
                int local;Label start,end,fail;
                public void atStart(CodeBuilder b){
                    local=b.allocateLocal(TypeKind.REFERENCE);start=b.newLabel();end=b.newLabel();fail=b.newLabel();
                    b.aload(0).aload(1).aload(2).aload(0).getfield(MOB,"lastHurtByPlayerMemoryTime",CD_int)
                        .invokestatic(FLOW,"beginDrops",mt(DROPS,MOB,SERVER,SOURCE,CD_int)).astore(local).labelBinding(start);
                }
                public void accept(CodeBuilder b,CodeElement e){if(e instanceof ReturnInstruction){hits[0]++;b.goto_(end);}else b.with(e);}
                public void atEnd(CodeBuilder b){b.labelBinding(end).aload(local).iconst_1().invokestatic(FLOW,"endDrops",mt(CD_void,DROPS,CD_boolean)).return_()
                    .labelBinding(fail).aload(local).iconst_0().invokestatic(FLOW,"endDrops",mt(CD_void,DROPS,CD_boolean)).athrow().exceptionCatchAll(start,end,fail);}
            }));count(hits,1);return bytes;
        });
        for(var owner:List.of(ENTITY,LIVING))plan.add(owner,250,"Unity living events","owned drop spawning",(cf,m)->{
            int[] hits={0};var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->
                method.methodName().equalsString(owner.equals(ENTITY)?"spawnAtLocation":"drop") && method.methodType().equalsString(
                    owner.equals(ENTITY)?"(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;":
                    "(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"),(b,e)->{
                    if(call(e,"addFreshEntity")){hits[0]++;b.aload(0).invokestatic(FLOW,"spawn",mt(CD_boolean,LEVEL,E,E));}
                    else b.with(e);
                }));count(hits,1);return bytes;
        });
    }
}
