package mc.unity.bootstrap;

import java.lang.classfile.*;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

final class TransferPatches {
    static void client(TransformPlan plan) {
        var helper=ClassDesc.of("mc.unity.client.MachinePictures");var list=ClassDesc.of("java.util.List");
        plan.add("net/minecraft/client/gui/render/GuiRenderer",170,"NeoForge client","picture-in-picture factories",(cf,m)->{
            int[] hits={0};
            var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(
                method->method.methodName().equalsString("<init>") && method.methodTypeSymbol().parameterCount()==3 && method.methodTypeSymbol().parameterType(2).equals(list),
                new CodeTransform(){
                    public void atStart(CodeBuilder c){hits[0]++;c.aload(3).invokestatic(helper,"append",MethodTypeDesc.of(list,list)).astore(3);}
                    public void accept(CodeBuilder c,CodeElement e){c.with(e);}
                }));
            if(hits[0]!=1)throw new IllegalStateException("Unexpected renderer factory anchor");return bytes;
        });
    }
    static void add(TransformPlan plan) {
        var container=ClassDesc.of("net.minecraft.world.Container");
        var extension=ClassDesc.of("net.neoforged.neoforge.common.extensions.ContainerExtension");
        plan.add("net/minecraft/world/Container",170,"NeoForge transfer","transaction-aware slots",(cf,m)->
            cf.transformClass(m,ClassTransform.endHandler(b->{
                var interfaces=new java.util.ArrayList<ClassDesc>();
                m.interfaces().forEach(i->interfaces.add(i.asSymbol()));interfaces.add(extension);b.withInterfaceSymbols(interfaces);
            })));
        var itemType=ClassDesc.of("net.minecraft.world.item.ItemStack");
        var slots=ClassDesc.of("net.minecraft.core.NonNullList");
        for(String name:java.util.List.of("net/minecraft/world/SimpleContainer","net/minecraft/world/level/block/entity/BaseContainerBlockEntity")) {
            var owner=ClassDesc.ofInternalName(name);
            plan.add(name,170,"NeoForge transfer","defer inventory notifications",(cf,m)->cf.transformClass(m,ClassTransform.endHandler(b->
                b.withMethodBody("setItem",MethodTypeDesc.of(CD_void,CD_int,itemType,CD_boolean),ClassFile.ACC_PUBLIC,c->
                    c.aload(0).aload(0).invokevirtual(owner,"getItems",MethodTypeDesc.of(slots)).iload(1).aload(2).iload(3)
                    .invokestatic(ClassDesc.of("mc.unity.adapter.neoforge.TransferSlots"),"write",
                        MethodTypeDesc.of(CD_void,container,slots,CD_int,itemType,CD_boolean)).return_()))));
        }
        var id=ClassDesc.of("net.minecraft.resources.Identifier");
        var key=ClassDesc.of("net.minecraft.resources.ResourceKey");
        var tag=ClassDesc.of("net.minecraft.tags.TagKey");
        for(String kind:java.util.List.of("Item","Block","Fluid")) {
            plan.add("net/minecraft/tags/"+kind+"Tags",170,"NeoForge transfer","namespaced tag factory",(cf,m)->
                cf.transformClass(m,ClassTransform.endHandler(b->b.withMethodBody("create",MethodTypeDesc.of(tag,id),
                    ClassFile.ACC_PUBLIC|ClassFile.ACC_STATIC,c->c.getstatic(ClassDesc.of("net.minecraft.core.registries.Registries"),
                        kind.toUpperCase(java.util.Locale.ROOT),key).aload(0).invokestatic(tag,"create",MethodTypeDesc.of(tag,key,id)).areturn()))));
        }
        for (boolean item:new boolean[]{true,false}) {
            String source=item?"net.minecraft.world.item.Item":"net.minecraft.world.level.material.Fluid";
            var owner=ClassDesc.of(source);
            var value=ClassDesc.of("net.neoforged.neoforge.transfer."+(item?"item.ItemResource":"fluid.FluidResource"));
            var fn=ClassDesc.of("java.util.function.Function");
            plan.add(source.replace('.','/'),170,"NeoForge transfer","default resource",(cf,model)->cf.transformClass(model,ClassTransform.endHandler(b->{
                b.withField("unityResource",value,ClassFile.ACC_PRIVATE);
                b.withMethodBody("computeDefaultResource",MethodTypeDesc.of(value,fn),ClassFile.ACC_PUBLIC|ClassFile.ACC_SYNCHRONIZED,c->{
                    var done=c.newLabel();c.aload(0).getfield(owner,"unityResource",value).ifnonnull(done)
                        .aload(0).aload(1).aload(0).invokeinterface(fn,"apply",MethodTypeDesc.of(CD_Object,CD_Object)).checkcast(value)
                        .putfield(owner,"unityResource",value).labelBinding(done).aload(0).getfield(owner,"unityResource",value).areturn();
                });
                if (!item) {
                    var type=ClassDesc.of("net.neoforged.neoforge.fluids.FluidType");
                    b.withMethodBody("getFluidType",MethodTypeDesc.of(type),ClassFile.ACC_PUBLIC,c->c.aload(0)
                        .invokestatic(type,"of",MethodTypeDesc.of(type,owner)).areturn());
                }
            })));
        }
        var stack=ClassDesc.of("net.minecraft.world.item.ItemStack");
        var patch=ClassDesc.of("net.minecraft.core.component.DataComponentPatch");
        var components=ClassDesc.of("net.minecraft.core.component.PatchedDataComponentMap");
        plan.add("net/minecraft/core/component/PatchedDataComponentMap",170,"NeoForge transfer","component patch comparisons",
            (cf,m)->cf.transformClass(m,ClassTransform.endHandler(b->{
                b.withMethodBody("isPatchEmpty",MethodTypeDesc.of(CD_boolean),ClassFile.ACC_PUBLIC,c->c.aload(0)
                    .invokevirtual(components,"asPatch",MethodTypeDesc.of(patch)).invokevirtual(patch,"isEmpty",MethodTypeDesc.of(CD_boolean)).ireturn());
                b.withMethodBody("patchEquals",MethodTypeDesc.of(CD_boolean,patch),ClassFile.ACC_PUBLIC,c->c.aload(0)
                    .invokevirtual(components,"asPatch",MethodTypeDesc.of(patch)).aload(1).invokevirtual(patch,"equals",MethodTypeDesc.of(CD_boolean,CD_Object)).ireturn());
            })));
        plan.add("net/minecraft/world/item/ItemStack",170,"NeoForge transfer","component emptiness",
            (cf,m)->cf.transformClass(m,ClassTransform.endHandler(b->b.withMethodBody("isComponentsPatchEmpty",
                MethodTypeDesc.of(CD_boolean),ClassFile.ACC_PUBLIC,c->c.aload(0).invokevirtual(stack,"getComponentsPatch",MethodTypeDesc.of(patch))
                .invokevirtual(patch,"isEmpty",MethodTypeDesc.of(CD_boolean)).ireturn()))));
    }
}
