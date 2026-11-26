package mc.unity.bootstrap;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;

final class ProtectionPatches {
    private static final ClassDesc HOOK=ClassDesc.of("net.minecraft.world.item.UnityProtection");
    private static final String N="net/minecraft/", ITEM=N+"world/item/", ENT=N+"world/entity/",
        PROJ=ENT+"projectile/Projectile", LEVEL=N+"server/level/ServerLevel", HAND=N+"world/InteractionHand";
    private static String t(String n){return "L"+n+";";}
    static void add(TransformPlan plan) {
        VolumePatches.add(plan);
        BukkitPatches.method(plan,ITEM+"BucketItem","use","emptyContents", (b,e)->{
            if(!(e instanceof InvokeInstruction i) || !i.name().equalsString("emptyContents"))return false;
            b.aload(3).invokestatic(HOOK,"empty",MethodTypeDesc.ofDescriptor("("+t(ITEM+"BucketItem")+
                t(ENT+"LivingEntity")+t(N+"world/level/Level")+t(N+"core/BlockPos")+t(N+"world/phys/BlockHitResult")+t(HAND)+")Z"));
            return true;
        });
        BukkitPatches.method(plan,ITEM+"BucketItem","use","pickupBlock", (b,e)->{
            if(!(e instanceof InvokeInstruction i) || !i.name().equalsString("pickupBlock"))return false;
            b.aload(3).aload(5).invokestatic(HOOK,"fill",MethodTypeDesc.ofDescriptor("("+t(N+"world/level/block/BucketPickup")+
                t(ENT+"LivingEntity")+t(N+"world/level/LevelAccessor")+t(N+"core/BlockPos")+t(N+"world/level/block/state/BlockState")+
                t(HAND)+t(N+"world/phys/BlockHitResult")+")"+t(ITEM+"ItemStack")));
            return true;
        });
        BukkitPatches.method(plan,ITEM+"ProjectileWeaponItem","shoot","spawnProjectile",(b,e)->{
            if(!(e instanceof InvokeInstruction i) || !i.name().equalsString("spawnProjectile"))return false;
            var proceed=b.newLabel();
            b.aload(2).aload(3).aload(4).fload(6).invokestatic(HOOK,"shoot",MethodTypeDesc.ofDescriptor("("+t(PROJ)+t(LEVEL)+
                t(ITEM+"ItemStack")+"Ljava/util/function/Consumer;"+t(ENT+"LivingEntity")+t(HAND)+t(ITEM+"ItemStack")+"F)"+t(PROJ)))
                .dup().invokevirtual(ClassDesc.ofInternalName(PROJ),"isRemoved",MethodTypeDesc.of(ConstantDescs.CD_boolean))
                .ifeq(proceed).pop().return_().labelBinding(proceed);
            return true;
        });
        BukkitPatches.method(plan,LEVEL,"addEntity","entity manager admission",(b,e)->{
            if(!(e instanceof FieldInstruction i) || i.opcode()!=Opcode.GETFIELD || !i.name().equalsString("entityManager"))return false;
            var proceed=b.newLabel();
            b.aload(1).invokestatic(HOOK,"allowLaunch",MethodTypeDesc.ofDescriptor("("+t(ENT+"Entity")+")Z"))
                .ifne(proceed).pop().iconst_0().ireturn().labelBinding(proceed).with(e);
            return true;
        });
        BukkitPatches.method(plan,ENT+"projectile/throwableitemprojectile/ThrownEnderpearl","onHit","teleport damage",(b,e)->{
            if(!(e instanceof InvokeInstruction i) || !i.name().equalsString("hurtServer") ||
                !i.owner().asInternalName().equals(N+"server/level/ServerPlayer")) return false;
            b.aload(0).invokestatic(ClassDesc.of("mc.unity.runtime.ServerHooks"),"pearlDamage",
                MethodTypeDesc.ofDescriptor("("+t(N+"server/level/ServerPlayer")+t(LEVEL)+t(N+"world/damagesource/DamageSource")+"F"+t(PROJ)+")Z"));
            return true;
        });
        plan.add(PROJ,209,"Bukkit projectile impact","block contact cancellation flag",(cf,model)->{
            int[] matched={0};
            var out=cf.transformClass(model,ClassTransform.transformingMethodBodies(
                m->m.methodName().equalsString("onHitBlock") && m.methodType().equalsString("("+t(N+"world/phys/BlockHitResult")+")V"),
                new CodeTransform(){
                    public void atStart(CodeBuilder b){
                        matched[0]++;
                        var proceed=b.newLabel();
                        b.aload(0).getfield(ClassDesc.ofInternalName(PROJ),"unity$impactCancelled",ConstantDescs.CD_boolean)
                            .ifeq(proceed).return_().labelBinding(proceed);
                    }
                    public void accept(CodeBuilder b,CodeElement e){b.with(e);}
                }).andThen(ClassTransform.endHandler(b->b.withField("unity$impactCancelled",ConstantDescs.CD_boolean,ClassFile.ACC_PRIVATE))));
            if(matched[0]!=1)throw new java.io.IOException("Missing projectile block contact hook");
            return out;
        });
        BukkitPatches.method(plan,PROJ,"hitTargetOrDeflectSelf","hit type",(b,e)->{
            if(!(e instanceof InvokeInstruction i) || !i.name().equalsString("getType"))return false;
            var proceed=b.newLabel();var deflect=ClassDesc.ofInternalName(ENT+"projectile/ProjectileDeflection");
            var projectile=ClassDesc.ofInternalName(PROJ);
            b.aload(0).aload(0).aload(1).invokestatic(HOOK,"allowHit",MethodTypeDesc.ofDescriptor("("+t(PROJ)+t(N+"world/phys/HitResult")+")Z"))
                .iconst_1().ixor().putfield(projectile,"unity$impactCancelled",ConstantDescs.CD_boolean)
                .aload(0).getfield(projectile,"unity$impactCancelled",ConstantDescs.CD_boolean).ifeq(proceed)
                .aload(1).instanceOf(ClassDesc.ofInternalName(N+"world/phys/BlockHitResult")).ifne(proceed)
                .pop().getstatic(deflect,"NONE",deflect).areturn().labelBinding(proceed).with(e);
            return true;
        });
    }
}
