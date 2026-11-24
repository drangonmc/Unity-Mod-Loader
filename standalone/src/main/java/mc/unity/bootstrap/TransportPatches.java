package mc.unity.bootstrap;
import static java.lang.constant.ConstantDescs.*;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.*;
final class TransportPatches {
    static ClassDesc cls(String s) {
        return ClassDesc.ofInternalName(s);
    }
    static MethodTypeDesc mt(String s) {
        return MethodTypeDesc.ofDescriptor(s);
    }
    static final String PACKET = "net/minecraft/network/protocol/common/",
                        PAYLOAD = PACKET + "custom/CustomPacketPayload",
                        CONN = "net/minecraft/network/Connection",
                        EXEC = "net/minecraft/util/thread/BlockableEventLoop",
                        SERVER = "net/minecraft/server/MinecraftServer",
                        PLAYER = "net/minecraft/server/level/ServerPlayer",
                        CODEC = "net/minecraft/network/codec/StreamCodec";
    static final ClassDesc NETWORK = ClassDesc.of("mc.unity.core.PlayNetwork"),
                           LOGIN = ClassDesc.of("mc.unity.core.LoginSync"),
                           RESUME = ClassDesc.of("mc.unity.core.LoginSync$Resume");
    static void add(TransformPlan plan, boolean client) {
        for (boolean incoming : new boolean[] {true, false}) {
            String packet = PACKET + (incoming ? "Serverbound" : "Clientbound") + "CustomPayloadPacket",
                   field = incoming ? "STREAM_CODEC" : "GAMEPLAY_STREAM_CODEC";
            plan.add(packet, 310, "Unity play", field, (cf, m) -> {
                int[] hits = {0};
                byte[] bytes = cf.transformClass(
                    m, ClassTransform.transformingMethodBodies(
                           method -> method.methodName().equalsString("<clinit>"), (b, e) -> {
                               if (!incoming && e instanceof FieldInstruction cfg &&
                                   cfg.opcode() == Opcode.PUTSTATIC &&
                                   cfg.name().equalsString("CONFIG_STREAM_CODEC")) {
                                   b.invokestatic(ClassDesc.of("net.neoforged.fml.config.ConfigSync"),
                                                  "configurationCodec",
                                                  mt("(L" + CODEC + ";)L" + CODEC + ";"));
                                   b.invokestatic(ConfigurationPatches.NETWORK, "clientbound",
                                                  mt("(L" + CODEC + ";)L" + CODEC + ";"));
                                   hits[0]++;
                               }
                               if (e instanceof FieldInstruction f && f.opcode() == Opcode.PUTSTATIC &&
                                   f.name().equalsString(field)) {
                                   hits[0]++;
                                   if (incoming)
                                       b.invokestatic(ConfigurationPatches.NETWORK, "serverbound",
                                                      mt("(L" + CODEC + ";)L" + CODEC + ";"));
                                   b.invokestatic(NETWORK, incoming ? "serverbound" : "clientbound",
                                                  mt("(L" + CODEC + ";)L" + CODEC + ";"));
                               }
                               b.with(e);
                           }));
                if (hits[0] != (incoming ? 1 : 2))
                    throw new IllegalStateException("Missing packet codec " + packet);
                return bytes;
            });
        }
        handler(plan, false);
        if (client)
            handler(plan, true);
        {
            String listener = "net/minecraft/server/network/ServerLoginPacketListenerImpl",
                   profile = "com/mojang/authlib/GameProfile", desc = "(L" + profile + ";)V";
            plan.add(listener, 315, "Unity login", "verifyLoginAndFinishConnectionSetup",
                     (cf, m) -> cf.transformClass(m, (b, e) -> {
                         if (e instanceof java.lang.classfile.Interfaces i) {
                             var list = new ArrayList<>(i.interfaces());
                             list.add(b.constantPool().classEntry(RESUME));
                             b.withInterfaces(list);
                         } else if (e instanceof MethodModel method &&
                                    method.methodName().equalsString("verifyLoginAndFinishConnectionSetup") &&
                                    method.methodType().equalsString(desc)) {
                             b.withMethodBody(
                                 "unity$resume", mt("()V"), ClassFile.ACC_PUBLIC,
                                 cb
                                 -> cb.aload(0)
                                        .aload(0)
                                        .getfield(cls(listener), "authenticatedProfile", cls(profile))
                                        .invokespecial(cls(listener), "verifyLoginAndFinishConnectionSetup",
                                                       mt(desc))
                                        .return_());
                             b.transformMethod(method, MethodTransform.transformingCode(new CodeTransform() {
                                 public void atStart(CodeBuilder cb) {
                                     var accepted = cb.newLabel();
                                     cb.aload(0)
                                         .getfield(cls(listener), "connection", cls(CONN))
                                         .aload(0)
                                         .aload(0)
                                         .getfield(cls(listener), "server", cls(SERVER))
                                         .invokestatic(LOGIN, "ready",
                                                       mt("(L" + CONN + ";Lmc/unity/core/LoginSync$Resume;L" +
                                                          SERVER + ";)Z"))
                                         .ifne(accepted)
                                         .return_()
                                         .labelBinding(accepted);
                                 }
                                 public void accept(CodeBuilder cb, CodeElement element) {
                                     cb.with(element);
                                 }
                             }));
                         } else
                             b.with(e);
                     }));
        }
    }
    private static void handler(TransformPlan plan, boolean client) {
        String handler = client ? "net/minecraft/client/multiplayer/ClientPacketListener"
                                : "net/minecraft/server/network/ServerGamePacketListenerImpl";
        String type = client ? PAYLOAD : PACKET + "ServerboundCustomPayloadPacket";
        plan.add(handler, 310, "Unity play", "handleCustomPayload", (cf, m) -> {
            int[] hits = {0};
            var bytes = cf.transformClass(
                m, ClassTransform.transformingMethodBodies(
                       method
                       -> method.methodName().equalsString("handleCustomPayload") &&
                              method.methodType().equalsString("(L" + type + ";)V"),
                       (b, e) -> {
                           if (hits[0] == 0 && e instanceof Instruction) {
                               hits[0]++;
                               b.aload(1);
                               if (!client)
                                   b.invokevirtual(cls(type), "payload", mt("()L" + PAYLOAD + ";"));
                               b.aload(0).getfield(cls(handler), "connection", cls(CONN));
                               if (client)
                                   b.aconst_null().aload(0).getfield(cls(handler), "minecraft",
                                                                     cls("net/minecraft/client/Minecraft"));
                               else
                                   b.aload(0)
                                       .getfield(cls(handler), "player", cls(PLAYER))
                                       .aload(0)
                                       .getfield(cls(handler), "server", cls(SERVER));
                               var next = b.newLabel();
                               b.invokestatic(
                                    NETWORK, "dispatch",
                                    mt("(L" + PAYLOAD + ";L" + CONN + ";L" + PLAYER + ";L" + EXEC + ";)Z"))
                                   .ifeq(next)
                                   .return_()
                                   .labelBinding(next);
                           }
                           b.with(e);
                       }));
            if (hits[0] != 1)
                throw new IllegalStateException("Missing play handler " + handler);
            return bytes;
        });
    }
}
