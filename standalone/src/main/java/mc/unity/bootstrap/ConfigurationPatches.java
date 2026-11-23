package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.*;
import java.util.function.Consumer;

final class ConfigurationPatches {
    static final ClassDesc NETWORK = ClassDesc.of("mc.unity.core.ConfigurationNetwork"),
                           PEER = ClassDesc.of("mc.unity.core.ConfigurationNetwork$Peer"),
                           SERVER = ClassDesc.of("mc.unity.core.ConfigurationNetwork$ServerPeer"),
                           STATE = ClassDesc.of("mc.unity.core.ConfigurationNetwork$State"),
                           CONN = ClassDesc.of("net.minecraft.network.Connection"),
                           EXEC = ClassDesc.of("net.minecraft.util.thread.BlockableEventLoop"),
                           TASK = ClassDesc.of("net.minecraft.server.network.ConfigurationTask"),
                           TASK_TYPE = ClassDesc.of("net.minecraft.server.network.ConfigurationTask$Type"),
                           QUEUE = ClassDesc.of("java.util.Queue"),
                           PAYLOAD = ClassDesc.of(
                               "net.minecraft.network.protocol.common.custom.CustomPacketPayload");
    static MethodTypeDesc mt(ClassDesc ret, ClassDesc... args) {
        return MethodTypeDesc.of(ret, args);
    }
    static void add(TransformPlan plan, boolean client) {
        side(plan, false);
        if (client)
            side(plan, true);
    }
    static void side(TransformPlan plan, boolean client) {
        String pkg = client ? "net/minecraft/client/multiplayer/" : "net/minecraft/server/network/";
        String listener = pkg + (client ? "Client" : "Server") + "ConfigurationPacketListenerImpl",
               common = pkg + (client ? "Client" : "Server") + "CommonPacketListenerImpl";
        ClassDesc owner = ClassDesc.ofInternalName(listener), base = ClassDesc.ofInternalName(common);
        String packet = "net/minecraft/network/protocol/common/" + (client ? "Clientbound" : "Serverbound") +
                        "CustomPayloadPacket";
        String commonInterface = "net/minecraft/network/protocol/common/" + (client ? "Client" : "Server") +
                                 "CommonPacketListener";
        plan.add(commonInterface, 308, "NeoForge configuration listener", "extension interface",
                 (cf, m) -> cf.transformClass(m, ClassTransform.endHandler(b -> {
                     var faces = new ArrayList<ClassDesc>();
                     m.interfaces().forEach(i -> faces.add(i.asSymbol()));
                     faces.add(
                         ClassDesc.of("net.neoforged.neoforge.common.extensions.ICommonPacketListener"));
                     b.withInterfaceSymbols(faces);
                 })));
        plan.add(common, 308, "Configuration connection", "connection access / custom packet", (cf, m) -> {
            int[] hits = {0};
            var transform =
                ClassTransform
                    .transformingMethodBodies(method
                                              -> method.methodName().equalsString("handleCustomPayload") &&
                                                     method.methodType().equalsString("(L" + packet + ";)V"),
                                              (b, e) -> {
                                                  if (hits[0] == 0 && e instanceof Instruction) {
                                                      var original = b.newLabel();
                                                      b.aload(0)
                                                          .aload(1)
                                                          .invokevirtual(ClassDesc.ofInternalName(packet),
                                                                         "payload", mt(PAYLOAD))
                                                          .invokestatic(NETWORK, "dispatch",
                                                                        mt(ConstantDescs.CD_boolean,
                                                                           ConstantDescs.CD_Object, PAYLOAD))
                                                          .ifeq(original)
                                                          .return_()
                                                          .labelBinding(original);
                                                      hits[0]++;
                                                  }
                                                  b.with(e);
                                              })
                    .andThen(ClassTransform.endHandler(b -> {
                        var faces = new ArrayList<ClassDesc>();
                        m.interfaces().forEach(i -> faces.add(i.asSymbol()));
                        faces.add(ClassDesc.of("mc.unity.core.ConfigurationNetwork$Link"));
                        faces.add(
                            ClassDesc.of("net.neoforged.neoforge.common.extensions.ICommonPacketListener"));
                        b.withInterfaceSymbols(faces).withMethodBody(
                            "unity$connection", mt(CONN), ClassFile.ACC_PUBLIC,
                            c -> c.aload(0).getfield(base, "connection", CONN).areturn());
                    }));
            var result = cf.transformClass(m, transform);
            if (hits[0] != 1)
                throw new IOException("Missing configuration receive hook " + common);
            return result;
        });
        plan.add(listener, 308, "Configuration lifecycle", "task queue and listener session", (cf, m) -> {
            Set<String> hits = new HashSet<>();
            var bytes = cf.transformClass(m, (b, element) -> {
                if (!(element instanceof MethodModel method)) {
                    b.with(element);
                    return;
                }
                String name = method.methodName().stringValue();
                boolean[] first = {true};
                b.transformMethod(method, MethodTransform.transformingCode((code, e) -> {
                    if (!client && first[0] && e instanceof Instruction) {
                        first[0] = false;
                        if (name.equals("returnToWorld") || name.equals("tick")) {
                            String hook = name.equals("tick") ? "tick" : "configure";
                            code.aload(0).invokestatic(NETWORK, hook, mt(ConstantDescs.CD_void, SERVER));
                            hits.add(hook);
                        }
                    }
                    if (e instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                        if (name.equals("<init>")) {
                            code.aload(0)
                                .new_(STATE)
                                .dup()
                                .invokespecial(STATE, "<init>", mt(ConstantDescs.CD_void))
                                .putfield(owner, "unity$config", STATE)
                                .aload(0)
                                .invokestatic(NETWORK, "init", mt(ConstantDescs.CD_void, PEER));
                            hits.add(name);
                        } else if (name.equals("onDisconnect")) {
                            code.aload(0).iconst_1().invokestatic(
                                NETWORK, "close", mt(ConstantDescs.CD_void, PEER, ConstantDescs.CD_boolean));
                            hits.add(name);
                        } else if (!client && name.equals("handleConfigurationFinished")) {
                            code.aload(0).invokestatic(NETWORK, "complete", mt(ConstantDescs.CD_void, PEER));
                            hits.add(name);
                        }
                    }
                    if (client && name.equals("handleConfigurationFinished") &&
                        e instanceof InvokeInstruction i && i.name().equalsString("setupInboundProtocol")) {
                        code.aload(0).invokestatic(NETWORK, "complete", mt(ConstantDescs.CD_void, PEER));
                        hits.add(name);
                    }
                    if (!client && name.equals("startConfiguration") && e instanceof InvokeInstruction i) {
                        if (i.name().equalsString("getServerModName")) {
                            code.aload(0).invokestatic(NETWORK, "start", mt(ConstantDescs.CD_void, SERVER));
                            hits.add("start");
                        }
                    }
                    code.with(e);
                }));
            });
            if (hits.size() != (client ? 3 : 6))
                throw new IOException("Missing configuration lifecycle anchors " + listener + " " + hits);
            return cf.transformClass(cf.parse(bytes), ClassTransform.endHandler(b -> {
                var faces = new ArrayList<ClassDesc>();
                m.interfaces().forEach(i -> faces.add(i.asSymbol()));
                faces.add(client ? PEER : SERVER);
                if (!client)
                    faces.add(ClassDesc.of(
                        "net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl"));
                b.withInterfaceSymbols(faces)
                    .withField("unity$config", STATE, ClassFile.ACC_PRIVATE)
                    .withMethodBody("unity$state", mt(STATE), ClassFile.ACC_PUBLIC,
                                    c -> c.aload(0).getfield(owner, "unity$config", STATE).areturn())
                    .withMethodBody(
                        "unity$executor", mt(EXEC), ClassFile.ACC_PUBLIC,
                        c
                        -> c.aload(0)
                               .getfield(base, client ? "minecraft" : "server",
                                         ClassDesc.of(client ? "net.minecraft.client.Minecraft"
                                                             : "net.minecraft.server.MinecraftServer"))
                               .areturn());
                if (!client) {
                    var packetType = ClassDesc.of("net.minecraft.network.protocol.Packet");
                    b.withMethodBody(
                        "send", mt(ConstantDescs.CD_void, packetType), ClassFile.ACC_PUBLIC,
                        c
                        -> c.aload(0)
                               .aload(1)
                               .invokestatic(NETWORK, "checkTaskPacket",
                                             mt(ConstantDescs.CD_void, PEER, packetType))
                               .aload(0)
                               .aload(1)
                               .invokespecial(base, "send", mt(ConstantDescs.CD_void, packetType))
                               .return_());
                    b.withMethodBody("unity$tasks", mt(QUEUE), ClassFile.ACC_PUBLIC,
                                     c -> c.aload(0).getfield(owner, "configurationTasks", QUEUE).areturn())
                        .withMethodBody("unity$current", mt(TASK), ClassFile.ACC_PUBLIC,
                                        c -> c.aload(0).getfield(owner, "currentTask", TASK).areturn())
                        .withMethodBody("unity$finish", mt(ConstantDescs.CD_void, TASK_TYPE),
                                        ClassFile.ACC_PUBLIC,
                                        c
                                        -> c.aload(0)
                                               .aload(1)
                                               .invokespecial(owner, "finishCurrentTask",
                                                              mt(ConstantDescs.CD_void, TASK_TYPE))
                                               .return_());
                }
            }));
        });
    }
}
