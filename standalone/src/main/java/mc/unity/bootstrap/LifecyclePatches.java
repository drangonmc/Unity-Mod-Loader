package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.util.function.*;
final class LifecyclePatches {
    private static final ClassDesc EVENTS = ClassDesc.of("mc.unity.core.Events");
    static void add(TransformPlan plan, boolean client) {
        var config = ClassDesc.of("net.neoforged.fml.config.ConfigSync");
        var connection = ClassDesc.of("net.minecraft.network.Connection");
        String serverConfig = "net/minecraft/server/network/ServerConfigurationPacketListenerImpl";
        hook(plan,serverConfig,"startConfiguration","()V",b -> b.aload(0)
            .getfield(ClassDesc.ofInternalName(serverConfig),"connection",connection)
            .invokestatic(config,"initial",MethodTypeDesc.of(ConstantDescs.CD_void,connection)),null);
        if (client) {
            clientLevels(plan);
            String configuration="net/minecraft/client/multiplayer/ClientConfigurationPacketListenerImpl";
            var payload=ClassDesc.of("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
            hook(plan,configuration,"handleCustomPayload","("+payload.descriptorString()+")V",b -> {
                var original=b.newLabel();
                b.aload(1).aload(0).getfield(ClassDesc.ofInternalName(configuration),"connection",connection)
                 .invokestatic(config,"receive",MethodTypeDesc.of(ConstantDescs.CD_boolean,payload,connection))
                 .ifeq(original).return_().labelBinding(original);
            },null);
            for (String listener:java.util.List.of(configuration,"net/minecraft/client/multiplayer/ClientPacketListener")) {
                boolean play=listener.endsWith("/ClientPacketListener");
                hook(plan,listener,play?"close":"onDisconnect",play?"()V":"(Lnet/minecraft/network/DisconnectionDetails;)V",
                    b->{
                        b.aload(0).getfield(ClassDesc.ofInternalName(listener),"connection",connection)
                            .invokestatic(config,"disconnected",MethodTypeDesc.of(ConstantDescs.CD_void,connection));
                        if(play)b.invokestatic(ClassDesc.of("mc.unity.adapter.neoforge.DataMaps"),"clearClient",MethodTypeDesc.of(ConstantDescs.CD_void));
                    },null);
            }
            String target = "net/minecraft/client/Minecraft";
            plan.add(target, 72, "Unity client setup", "<init> first resource reload", (cf, m) -> {
                int[] hits = {0, 0};
                var bytes = cf.transformClass(m,
                    ClassTransform.transformingMethodBodies(
                        method -> method.methodName().equalsString("<init>"), (b, e) -> {
                            if (e instanceof java.lang.classfile.instruction.NewObjectInstruction alloc
                                && alloc.className().asInternalName().equals("net/minecraft/client/Options")) {
                                b.invokestatic(EVENTS, "clientBeforeOptions", MethodTypeDesc.of(ConstantDescs.CD_void));
                                hits[1]++;
                            }
                            if (e instanceof InvokeInstruction i && i.name().equalsString("createReload")
                                && i.owner().asInternalName().equals(
                                    "net/minecraft/server/packs/resources/ReloadableResourceManager")) {
                                hits[0]++;
                                b.invokestatic(ClassDesc.of("mc.unity.runtime.ClientHooks"), "setup",
                                    MethodTypeDesc.of(ConstantDescs.CD_void));
                            }
                            b.with(e);
                        }));
                if (hits[0] != 1 || hits[1] != 1)
                    throw new IOException("Missing first client resource reload");
                return bytes;
            });
            var clientEvents=ClassDesc.of("mc.unity.client.ClientEvents");
            hook(plan,target,"run","()V",b->b.aload(0).invokestatic(clientEvents,"started",MethodTypeDesc.ofDescriptor("(Lnet/minecraft/client/Minecraft;)V")),null);
            hook(plan,target,"close","()V",b->b.aload(0).invokestatic(clientEvents,"stopping",MethodTypeDesc.ofDescriptor("(Lnet/minecraft/client/Minecraft;)V")),null);
            var levelArg=MethodTypeDesc.ofDescriptor("(Lnet/minecraft/client/multiplayer/ClientLevel;)V");
            hook(plan,"net/minecraft/client/multiplayer/ClientLevel","tick","(Ljava/util/function/BooleanSupplier;)V",
                b->b.aload(0).invokestatic(clientEvents,"levelStart",levelArg), b->b.aload(0).invokestatic(clientEvents,"levelEnd",levelArg));
            hook(plan, target, "tick", "()V",
                b -> call(b, "clientPre", "()V"), b -> call(b, "clientPost", "()V"));
        }
        {
            String serverTarget=client?"net/minecraft/client/server/IntegratedServer":"net/minecraft/server/dedicated/DedicatedServer";
            plan.add(serverTarget, 72, "Unity lifecycle", "initServer()Z",
                (cf, m) -> {
                    int[] hits = {0, 0};
                    var bytes = cf.transformClass(m,
                        ClassTransform.transformingMethodBodies(method
                            -> method.methodName().equalsString("initServer")
                                && method.methodType().equalsString("()Z"),
                            (b, e) -> {
                                if (e instanceof InvokeInstruction invoke
                                    && invoke.name().equalsString("loadLevel")) {
                                    hits[0]++;
                                    b.aload(0).invokestatic(ClassDesc.of("mc.unity.runtime.ServerHooks"),"about",MethodTypeDesc.ofDescriptor("(Lnet/minecraft/server/MinecraftServer;)V"));
                                }
                                if (e instanceof Instruction i && i.opcode() == Opcode.IRETURN) {
                                    hits[1]++;
                                    var skip = b.newLabel();
                                    b.dup().ifeq(skip);
                                    call(b.aload(0), "starting", "(Lnet/minecraft/server/MinecraftServer;)V");
                                    b.labelBinding(skip);
                                }
                                b.with(e);
                            }));
                    if (hits[0] != 1 || hits[1] == 0)
                        throw new IOException("Missing DedicatedServer startup anchor");
                    return bytes;
                });
            String player = "Lnet/minecraft/server/level/ServerPlayer;",
                   list = "net/minecraft/server/players/PlayerList";
            hook(plan, list, "placeNewPlayer",
                "(Lnet/minecraft/network/Connection;" + player
                    + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
                null, b -> call(b.aload(2), "joined", "(" + player + ")V"));
            hook(plan, list, "remove", "(" + player + ")V",
                b -> call(b.aload(1), "left", "(" + player + ")V"), null);
            String args = "(Lnet/minecraft/server/MinecraftServer;Ljava/util/function/BooleanSupplier;)V";
            hook(plan, "net/minecraft/server/MinecraftServer", "tickServer",
                "(Ljava/util/function/BooleanSupplier;)V",
                b
                -> call(b.aload(0).aload(1), "tickPre", args),
                b -> call(b.aload(0).aload(1), "tickPost", args));
        }
    }
    private static void call(CodeBuilder b, String method, String desc) {
        b.invokestatic(EVENTS, method, MethodTypeDesc.ofDescriptor(desc));
    }
    private static void hook(TransformPlan plan, String target, String method, String desc,
        Consumer<CodeBuilder> start, Consumer<CodeBuilder> end) {
        plan.add(target, 75, "Unity events", method + desc, (cf, m) -> {
            boolean[] entered = {false}, returned = {false};
            var bytes = cf.transformClass(m,
                ClassTransform.transformingMethodBodies(
                    x -> x.methodName().equalsString(method) && x.methodType().equalsString(desc), (b, e) -> {
                        if (e instanceof Instruction i) {
                            if (!entered[0]) {
                                entered[0] = true;
                                if (start != null)
                                    start.accept(b);
                            }
                            if (i.opcode() == Opcode.RETURN) {
                                returned[0] = true;
                                if (end != null)
                                    end.accept(b);
                            }
                        }
                        b.with(e);
                    }));
            if (!entered[0] || !returned[0])
                throw new IOException("Missing event anchor " + method);
            return bytes;
        });
    }
    private static void clientLevels(TransformPlan plan){
        String mc="net/minecraft/client/Minecraft";
        var type=ClassDesc.of("net.minecraft.client.multiplayer.ClientLevel");
        var level=ClassDesc.of("net.minecraft.world.level.Level");
        var signature=MethodTypeDesc.of(ConstantDescs.CD_void,level,level);
        for(String name:java.util.List.of("setLevel","disconnect","clearClientLevel")){
            plan.add(mc,73,"Unity client world lifecycle",name,(cf,model)->{
                int[] hits={0};
                byte[] bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(m->m.methodName().equalsString(name),(b,e)->{
                    if(e instanceof java.lang.classfile.instruction.FieldInstruction field && field.opcode()==Opcode.PUTFIELD &&
                            field.owner().asInternalName().equals(mc) && field.name().equalsString("level") && field.typeSymbol().equals(type)){
                        int next=b.allocateLocal(TypeKind.REFERENCE), previous=b.allocateLocal(TypeKind.REFERENCE);
                        b.astore(next).dup().getfield(ClassDesc.ofInternalName(mc),"level",type).astore(previous);
                        b.aload(previous).aload(next).invokestatic(EVENTS,"beforeClientLevel",signature);
                        b.aload(next).with(e);
                        b.aload(previous).aload(next).invokestatic(EVENTS,"afterClientLevel",signature);
                        hits[0]++;
                    }else b.with(e);
                }));
                if(hits[0]!=1)throw new IOException("Unexpected Minecraft client world assignment count in "+name+": "+hits[0]);
                return bytes;
            });
        }
    }

}
