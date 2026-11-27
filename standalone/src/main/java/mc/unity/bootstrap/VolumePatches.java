package mc.unity.bootstrap;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;

/** Route actual bucket operations, after the vanilla cauldron fill predicate has accepted the interaction. */
final class VolumePatches {
    private static final String N="net/minecraft/", W=N+"world/", I=W+"item/", B=W+"level/block/",
            S=B+"state/BlockState", L=W+"level/Level", POS=N+"core/BlockPos", P=W+"entity/player/Player",
            HAND=W+"InteractionHand", ST=I+"ItemStack", SOUND=N+"sounds/SoundEvent", RESULT=W+"InteractionResult",
            C=N+"core/cauldron/CauldronInteraction", D=N+"core/dispenser/";
    private static final ClassDesc CAULDRON=ClassDesc.of("mc.unity.runtime.CauldronHooks");
    private static String t(String n){return "L"+n+";";}
    static void add(TransformPlan plan) {
        BukkitPatches.method(plan,B+"AbstractCauldronBlock","useItemOn","cauldron interaction face",(b,e)->{
            if(!(e instanceof InvokeInstruction i) || !i.name().equalsString("interact") || !i.owner().asInternalName().equals(C))return false;
            b.aload(7).invokestatic(CAULDRON,"interact",MethodTypeDesc.ofDescriptor("("+t(C)+t(S)+t(L)+t(POS)+t(P)+t(HAND)+t(ST)+t(W+"phys/BlockHitResult")+")"+t(RESULT)));
            return true;
        });
        for(boolean fill:new boolean[]{true,false}) {
            BukkitPatches.method(plan,N+"core/cauldron/CauldronInteractions",fill?"fillBucket":"emptyBucket","server cauldron bucket transaction",(b,e)->{
                if(!(e instanceof InvokeInstruction i) || !i.name().equalsString("isClientSide"))return false;
                var original=b.newLabel();
                b.aload(fill?1:0).aload(fill?2:1).aload(fill?3:2).aload(fill?4:3).aload(fill?5:4);
                if(fill){
                    b.aload(6).getstatic(ClassDesc.ofInternalName(B+"Blocks"),"CAULDRON",ClassDesc.ofInternalName(B+"Block"))
                        .invokevirtual(ClassDesc.ofInternalName(B+"Block"),"defaultBlockState",MethodTypeDesc.ofDescriptor("()"+t(S))).aload(8).iconst_1();
                }else{
                    b.new_(ClassDesc.ofInternalName(ST)).dup().getstatic(ClassDesc.ofInternalName(I+"Items"),"BUCKET",ClassDesc.ofInternalName(I+"Item"))
                        .invokespecial(ClassDesc.ofInternalName(ST),"<init>",MethodTypeDesc.ofDescriptor("("+t(W+"level/ItemLike")+")V"))
                        .aload(5).aload(6).iconst_0();
                }
                b.invokestatic(CAULDRON,"change",MethodTypeDesc.ofDescriptor("("+t(L)+t(POS)+t(P)+t(HAND)+t(ST)+t(ST)+t(S)+t(SOUND)+"Z)"+t(RESULT)))
                    .dup().ifnull(original).areturn().labelBinding(original).pop().with(e);
                return true;
            });
        }
        var interactions=N+"core/cauldron/CauldronInteractions";
        String sounds=N+"sounds/SoundEvents.";
        for(var entry:java.util.Map.of(
                MethodTarget.fields("bootStrap",sounds+"BOTTLE_EMPTY",B+"Blocks.WATER_CAULDRON"),"BOTTLE_EMPTY",
                MethodTarget.fields("bootStrap",sounds+"BOTTLE_FILL"),"BOTTLE_FILL",
                MethodTarget.fields("bootStrap",sounds+"BOTTLE_EMPTY",B+"LayeredCauldronBlock.LEVEL"),"BOTTLE_EMPTY",
                MethodTarget.named("bannerInteraction"),"BANNER_WASH",
                MethodTarget.named("shulkerBoxInteraction"),"SHULKER_WASH",
                MethodTarget.named("dyedItemIteration"),"ARMOR_WASH").entrySet()) {
            BukkitPatches.method(plan,interactions,entry.getKey(),"accepted item exchange "+entry.getValue()+entry.getKey().description(),(b,e)->{
                if(!(e instanceof InvokeInstruction i) || !i.name().equalsString("isClientSide"))return false;
                b.aload(0).aload(2).aload(3).ldc(entry.getValue()).invokestatic(CAULDRON,"skipItemWork",
                    MethodTypeDesc.ofDescriptor("("+t(L)+t(S)+t(POS)+t(P)+"Ljava/lang/String;)Z"));
                return true;
            });
            BukkitPatches.method(plan,interactions,entry.getKey(),"commit once "+entry.getValue()+entry.getKey().description(),(b,e)->{
                if(!(e instanceof InvokeInstruction i))return false;
                if(i.name().equalsString("lowerFillLevel"))
                    b.invokestatic(CAULDRON,"lowerOnce",MethodTypeDesc.ofDescriptor("("+t(S)+t(L)+t(POS)+")V"));
                else if(i.name().equalsString("setBlockAndUpdate"))
                    b.invokestatic(CAULDRON,"setOnce",MethodTypeDesc.ofDescriptor("("+t(L)+t(POS)+t(S)+")Z"));
                else return false;
                return true;
            });
        }
        for(String behavior:new String[]{"$3","$4"}) {
            String name=D+"DispenseItemBehavior"+behavior;
            plan.add(name,210,"Bukkit dispenser bucket","execute",(cf,model)->{
                int[] hits={0};
                var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("execute") &&
                    m.methodType().equalsString("("+t(D+"BlockSource")+t(ST)+")"+t(ST)),new CodeTransform(){
                        public void atStart(CodeBuilder b){
                            hits[0]++;
                            var proceed=b.newLabel();
                            b.aload(0).aload(1).aload(2).invokestatic(ClassDesc.ofInternalName(B+"UnityDispenser"),"proceed",
                                MethodTypeDesc.ofDescriptor("("+t(D+"DispenseItemBehavior")+t(D+"BlockSource")+t(ST)+")Z"))
                                .ifne(proceed).aload(2).areturn().labelBinding(proceed);
                        }
                        public void accept(CodeBuilder b,CodeElement e){b.with(e);}
                    }));
                if(hits[0]!=1)throw new IOException("Changed bucket dispenser "+name);
                return bytes;
            });
        }
    }
}
