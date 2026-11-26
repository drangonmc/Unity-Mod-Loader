package mc.unity.bootstrap;

import java.lang.classfile.*;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

final class NeoGameplayPatches {
    private static final int PUBLIC=ClassFile.ACC_PUBLIC, PRIVATE=ClassFile.ACC_PRIVATE;
    static void add(TransformPlan plan, boolean client) {
        var pack=ClassDesc.of("net.minecraft.server.packs.repository.Pack");
        var meta=ClassDesc.of("net.minecraft.server.packs.repository.Pack$Metadata");
        var four=MethodTypeDesc.ofDescriptor("(Lnet/minecraft/network/chat/Component;Lnet/minecraft/server/packs/repository/PackCompatibility;Lnet/minecraft/world/flag/FeatureFlagSet;Ljava/util/List;)V");
        plan.add(meta.descriptorString().substring(1,meta.descriptorString().length()-1),147,"Pack metadata","hidden",
            (cf,m)->cf.transformClass(m,ClassTransform.endHandler(b->{
                b.withField("hidden",CD_boolean,PRIVATE);
                b.withMethodBody("isHidden",MethodTypeDesc.of(CD_boolean),PUBLIC,c->c.aload(0).getfield(meta,"hidden",CD_boolean).ireturn());
                b.withMethodBody("<init>",four.insertParameterTypes(4,CD_boolean),PUBLIC,c->
                    c.aload(0).aload(1).aload(2).aload(3).aload(4).invokespecial(meta,"<init>",four)
                     .aload(0).iload(5).putfield(meta,"hidden",CD_boolean).return_());
            })));
        plan.add("net/minecraft/server/packs/repository/Pack",147,"Pack visibility","hidden",
            (cf,m)->cf.transformClass(m,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("<init>"),(b,e)->{
                if(e instanceof ReturnInstruction)
                    b.aload(0).aload(3).invokevirtual(meta,"isHidden",MethodTypeDesc.of(CD_boolean)).putfield(pack,"hidden",CD_boolean);
                b.with(e);
            }).andThen(ClassTransform.endHandler(b->{
                b.withField("hidden",CD_boolean,PRIVATE);
                b.withMethodBody("isHidden",MethodTypeDesc.of(CD_boolean),PUBLIC,c->c.aload(0).getfield(pack,"hidden",CD_boolean).ireturn());
            }))));
        if(client) plan.add("net/minecraft/client/Options",147,"Client key mappings","keyMappings assignment",(cf,m)-> {
            var keyArray=ClassDesc.of("net.minecraft.client.KeyMapping").arrayType();
            var options=ClassDesc.of("net.minecraft.client.Options");
            int[] hits={0};
            var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("<init>"),(b,e)->{
                b.with(e);
                if(e instanceof java.lang.classfile.instruction.FieldInstruction field && field.opcode()==Opcode.PUTFIELD && field.name().equalsString("keyMappings")) {
                    hits[0]++;
                    b.aload(0).aload(0).getfield(options,"keyMappings",keyArray).aload(0)
                        .invokestatic(ClassDesc.of("net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent"),"collect",MethodTypeDesc.of(keyArray,keyArray,options))
                        .putfield(options,"keyMappings",keyArray).aload(0)
                        .invokestatic(ClassDesc.of("net.minecraftforge.client.event.RegisterKeyMappingsEvent"),"fire",MethodTypeDesc.of(CD_void,options));
                }
            }).andThen((builder,element)->{
                if(element instanceof FieldModel f && f.fieldName().equalsString("keyMappings") && f.fieldTypeSymbol().equals(keyArray)) {
                    builder.transformField(f,(field,part)->{
                        if(part instanceof AccessFlags flags)field.withFlags(flags.flagsMask() & ~ClassFile.ACC_FINAL);
                        else field.with(part);
                    });
                } else builder.with(element);
            }));
            if(hits[0]!=1)throw new java.io.IOException("Unexpected Options key mapping assignments: "+hits[0]);
            return bytes;
        });
        peer(plan,false);
        if(client) peer(plan,true);
    }
    private static void peer(TransformPlan plan,boolean client) {
        String target=client ? "net/minecraft/client/multiplayer/ClientCommonPacketListenerImpl" : "net/minecraft/server/network/ServerCommonPacketListenerImpl";
        var conn=ClassDesc.of("net.minecraft.network.Connection");
        var type=ClassDesc.of("net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type");
        plan.add(target,147,"Negotiated payloads","hasChannel",(cf,m)->cf.transformClass(m,ClassTransform.endHandler(b->{
            var interfaces=new java.util.ArrayList<ClassDesc>();
            for(var i:m.interfaces()) interfaces.add(i.asSymbol());
            interfaces.add(ClassDesc.of("mc.unity.core.PlayNetwork$Peer"));
            b.withInterfaceSymbols(interfaces);
            b.withMethodBody("hasChannel",MethodTypeDesc.of(CD_boolean,type),PUBLIC,c->
                c.aload(0).aload(1)
                 .invokestatic(ClassDesc.of("mc.unity.core.ConfigurationNetwork"),"hasChannel",MethodTypeDesc.of(CD_boolean,ClassDesc.of("mc.unity.core.ConfigurationNetwork$Link"),type)).ireturn());
        })));
    }
}
