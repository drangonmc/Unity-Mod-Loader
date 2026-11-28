package mc.unity.bootstrap;
import java.lang.classfile.*;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

final class PluginLoginPatches {
    static void add(TransformPlan plan) {
        String login="net/minecraft/server/network/ServerLoginPacketListenerImpl";
        var self=ClassDesc.ofInternalName(login);
        var conn=ClassDesc.of("net.minecraft.network.Connection");
        var future=ClassDesc.of("java.util.concurrent.CompletableFuture");
        var profile=ClassDesc.of("com.mojang.authlib.GameProfile");
        var player=ClassDesc.of("net.minecraft.server.level.ServerPlayer");
        var hook=ClassDesc.of("mc.unity.runtime.ServerHooks");
        // Apply after the registry gate so pre-login runs while vanilla is still in VERIFYING.
        plan.add("net/minecraft/network/Connection",321,"Bukkit connection","disconnect cleanup",(cf,m)->{
            int[] hits={0};
            var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("handleDisconnection") && method.methodType().equalsString("()V"),
                (c,e)->{if(e instanceof ReturnInstruction){hits[0]++;c.aload(0).invokestatic(hook,"pluginDisconnected",MethodTypeDesc.of(CD_void,conn));}c.with(e);}));
            if(hits[0]==0)throw new IllegalStateException("Missing disconnect anchor");return bytes;
        });
        plan.add(login,321,"Bukkit connection","asynchronous pre-login", (cf,m)->{
            int[] hits={0};
            var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(
                method->method.methodName().equalsString("verifyLoginAndFinishConnectionSetup") && method.methodTypeSymbol().equals(MethodTypeDesc.of(CD_void,profile)),
                new CodeTransform(){
                    public void atStart(CodeBuilder c){hits[0]++;
                        var pending=c.newLabel();var allow=c.newLabel();
                        c.aload(0).getfield(self,"unityPreLogin",future).ifnonnull(pending)
                            .aload(0).aload(0).getfield(self,"connection",conn).aload(1)
                            .invokestatic(hook,"beginPluginLogin",MethodTypeDesc.of(future,conn,profile)).putfield(self,"unityPreLogin",future)
                            .labelBinding(pending).aload(0).getfield(self,"unityPreLogin",future).aload(0).getfield(self,"connection",conn)
                            .invokestatic(hook,"allowPluginLogin",MethodTypeDesc.of(CD_boolean,future,conn)).ifne(allow).return_().labelBinding(allow);
                    }
                    public void accept(CodeBuilder c,CodeElement e){c.with(e);}
                }).andThen(ClassTransform.endHandler(b->b.withField("unityPreLogin",future,ClassFile.ACC_PRIVATE))));
            if(hits[0]!=1)throw new java.io.IOException("Unexpected pre-login anchor");return out;
        });
        plan.add("net/minecraft/server/players/PlayerList",169,"Bukkit connection","login before placement",(cf,m)->{
            int[] hits={0};var out=cf.transformClass(m,ClassTransform.transformingMethodBodies(
                method->method.methodName().equalsString("placeNewPlayer") && method.methodTypeSymbol().parameterList().equals(
                    java.util.List.of(conn,player,ClassDesc.of("net.minecraft.server.network.CommonListenerCookie"))),new CodeTransform(){
                    public void atStart(CodeBuilder c){hits[0]++;var ok=c.newLabel();c.aload(1).aload(2)
                        .invokestatic(hook,"allowPluginPlacement",MethodTypeDesc.of(CD_boolean,conn,player)).ifne(ok).return_().labelBinding(ok);}
                    public void accept(CodeBuilder c,CodeElement e){c.with(e);}
                }));
            if(hits[0]!=1)throw new java.io.IOException("Unexpected player placement anchor");return out;
        });
    }
}
