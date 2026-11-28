package mc.unity.bootstrap;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.util.*;
import static java.lang.constant.ConstantDescs.*;

final class MachineRenderPatches {
    private static final ClassDesc HOOK=ClassDesc.of("mc.unity.client.MachineModels");
    private static ClassDesc t(String n){return ClassDesc.of("net.minecraft."+n);}
    private static int slot(MethodModel m,String name) {
        var values=m.code().orElseThrow().elementStream().filter(LocalVariable.class::isInstance)
                .map(LocalVariable.class::cast).filter(v->v.name().equalsString(name)).map(LocalVariable::slot).distinct().toList();
        if(values.size()!=1)throw new IllegalStateException(m.methodName()+" missing local "+name);
        return values.getFirst();
    }
    static void add(TransformPlan plan) {
        var mapper=t("util.ExtraCodecs$LateBoundIdMapper");
        for(boolean special:new boolean[]{false,true}){
            String target="net/minecraft/client/renderer/"+(special?"special/SpecialModelRenderers":"item/ItemModels");
            plan.add(target,205,"NeoForge client","model codec registration",(cf,m)->{
                int[] hits={0};var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(
                    method->method.methodName().equalsString("bootstrap") && method.methodType().equalsString("()V"),(c,e)->{
                        if(e instanceof ReturnInstruction){hits[0]++;c.getstatic(ClassDesc.ofInternalName(target),"ID_MAPPER",mapper)
                            .loadConstant(special?1:0).invokestatic(HOOK,"models",MethodTypeDesc.of(CD_void,mapper,CD_boolean));}c.with(e);
                    }));if(hits[0]!=1)throw new IllegalStateException(target+" bootstrap");return bytes;
            });
        }
        var state=t("client.renderer.state.level.LevelRenderState");var camera=t("client.Camera");
        var extractor=t("client.renderer.extract.LevelExtractor");var level=t("client.multiplayer.ClientLevel");
        var outline=MethodTypeDesc.of(CD_boolean,extractor,level,t("core.BlockPos"),t("world.level.block.state.BlockState"),
            t("world.phys.BlockHitResult"),t("world.phys.shapes.CollisionContext"),CD_boolean,CD_boolean,camera,state);
        plan.add("net/minecraft/client/renderer/extract/LevelExtractor",205,"NeoForge client","block outline",(cf,model)->{
            int[] hits={0};var bytes=cf.transformClass(model,(b,e)->{
                if(e instanceof MethodModel m && m.methodName().equalsString("extractBlockOutline")){
                    int ctx=slot(m,"context"),pos=slot(m,"pos"),block=slot(m,"state"),hit=slot(m,"blockHitResult"),
                        translucent=slot(m,"isBlockTranslucent"),contrast=slot(m,"highContrast");
                    b.transformMethod(m,MethodTransform.transformingCode((c,ins)->{
                        c.with(ins);
                        if(ins instanceof StoreInstruction store && store.slot()==ctx && store.typeKind()==TypeKind.REFERENCE){
                            hits[0]++;var go=c.newLabel();c.aload(0).aload(0).getfield(extractor,"level",level).aload(pos).aload(block)
                                .aload(hit).aload(ctx).iload(translucent).iload(contrast).aload(1).aload(2)
                                .invokestatic(HOOK,"outline",outline).ifeq(go).return_().labelBinding(go);
                        }
                    }));
                }else b.with(e);
            });if(hits[0]!=1)throw new IllegalStateException("26.2 block outline anchor");return bytes;
        });
        var renderer=t("client.renderer.LevelRenderer");
        plan.add("net/minecraft/client/renderer/LevelRenderer",205,"NeoForge client","custom geometry",(cf,model)->{
            int[] hits={0};var bytes=cf.transformClass(model,(b,e)->{
                if(e instanceof MethodModel m && m.methodName().equalsString("submitFeatures")){
                    int pose=slot(m,"poseStack");b.transformMethod(m,MethodTransform.transformingCode((c,ins)->{
                        if(ins instanceof InvokeInstruction i && i.name().equalsString("finalizeGizmoCollection")){
                            hits[0]++;c.aload(1).aload(2).aload(pose).aload(0).getfield(renderer,"visibleSections",ClassDesc.of("it.unimi.dsi.fastutil.objects.ObjectArrayList"))
                                .invokestatic(HOOK,"submit",MethodTypeDesc.of(CD_void,state,t("client.renderer.SubmitNodeCollector"),ClassDesc.of("com.mojang.blaze3d.vertex.PoseStack"),ClassDesc.of("java.lang.Iterable")));
                        }c.with(ins);
                    }));
                }else b.with(e);
            });if(hits[0]!=1)throw new IllegalStateException("26.2 custom geometry anchor");return bytes;
        });
    }
}
