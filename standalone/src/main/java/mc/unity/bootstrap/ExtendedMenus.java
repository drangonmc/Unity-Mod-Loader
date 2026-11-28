package mc.unity.bootstrap;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

final class ExtendedMenus {
    static void add(TransformPlan plan){
        var helper=ClassDesc.of("mc.unity.adapter.neoforge.MenuData");
        var factory=ClassDesc.of("net.neoforged.neoforge.network.IContainerFactory");
        var type=ClassDesc.of("net.minecraft.world.inventory.MenuType");
        var supplier=type.nested("MenuSupplier");var inv=ClassDesc.of("net.minecraft.world.entity.player.Inventory");
        var menu=ClassDesc.of("net.minecraft.world.inventory.AbstractContainerMenu");
        var player=ClassDesc.of("net.minecraft.world.entity.player.Player");
        var server=ClassDesc.of("net.minecraft.server.level.ServerPlayer");
        var provider=ClassDesc.of("net.minecraft.world.MenuProvider");
        var result=ClassDesc.of("java.util.OptionalInt");
        var packet=ClassDesc.of("net.minecraft.network.protocol.game.ClientboundOpenScreenPacket");
        plan.add("net/minecraft/world/entity/player/Player",173,"NeoForge menus","extra open data",(cf,m)->
            cf.transformClass(m,ClassTransform.endHandler(b->{
                for(var arg:java.util.List.of(ClassDesc.of("java.util.function.Consumer"),ClassDesc.of("net.minecraft.core.BlockPos")))
                    b.withMethodBody("openMenu",MethodTypeDesc.of(result,provider,arg),ClassFile.ACC_PUBLIC,c->c.aload(0).aload(1).aload(2)
                        .invokestatic(helper,"open",MethodTypeDesc.of(result,player,provider,arg)).areturn());
            })));
        plan.add("net/minecraft/world/inventory/MenuType",202,"NeoForge menus","factory dispatch",(cf,m)->{
            int[] hits={0,0};
            var out=cf.transformClass(m,(b,element)->{
                if(element instanceof MethodModel method && method.methodName().equalsString("<init>") && method.methodTypeSymbol().parameterType(0).equals(supplier)){
                    b.transformMethod(method,MethodTransform.transformingCode((c,e)->{
                        if(e instanceof ReturnInstruction){hits[0]++;c.aload(1).invokestatic(helper,"checkFactory",MethodTypeDesc.of(CD_void,CD_Object));}c.with(e);
                    }));
                }else if(element instanceof MethodModel method && method.methodName().equalsString("create") && method.methodTypeSymbol().equals(MethodTypeDesc.of(menu,CD_int,inv))){
                    b.transformMethod(method,MethodTransform.transformingCode(new CodeTransform(){
                        public void atStart(CodeBuilder c){hits[1]++;var normal=c.newLabel();
                            c.aload(0).getfield(type,"constructor",supplier).instanceOf(factory).ifeq(normal)
                                .aload(0).getfield(type,"constructor",supplier).aload(0).iload(1).aload(2)
                                .invokestatic(helper,"create",MethodTypeDesc.of(menu,CD_Object,type,CD_int,inv)).areturn().labelBinding(normal);}
                        public void accept(CodeBuilder c,CodeElement e){c.with(e);}
                    }));
                }else b.with(element);
            });if(hits[0]!=1 || hits[1]!=1)throw new IllegalStateException("Menu factory anchors");return out;
        });
        plan.add("net/minecraft/server/level/ServerPlayer",173,"NeoForge menus","send before open screen",(cf,m)->{
            int[] hits={0};var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(
                method->method.methodName().equalsString("openMenu")&&method.methodTypeSymbol().equals(MethodTypeDesc.of(result,provider)),(c,e)->{
                    c.with(e);if(e instanceof InvokeInstruction i && i.name().equalsString("<init>") && i.owner().asSymbol().equals(packet)){
                        hits[0]++;c.dup().aload(0).invokestatic(helper,"beforeOpen",MethodTypeDesc.of(CD_void,packet,server));}
                }));if(hits[0]!=1)throw new IllegalStateException("Menu opening packet anchor");return out;
        });
    }
}
