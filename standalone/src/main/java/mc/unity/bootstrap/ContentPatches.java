package mc.unity.bootstrap;
import static java.lang.constant.ConstantDescs.CD_void;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;
final class ContentPatches {
    private static final String TAB = "net/minecraft/world/item/CreativeModeTab";
    private static final String BUILDER = TAB + "$Builder";
    private static final String CONNECTION = "net/minecraft/network/Connection";
    private static final String SERVER = "net/minecraft/server/network/ServerLoginPacketListenerImpl";
    private static final String CLIENT = "net/minecraft/client/multiplayer/ClientHandshakePacketListenerImpl";
    private static final String LOGIN = "net/minecraft/network/protocol/login/";
    private static final ClassDesc CONTENT = ClassDesc.of("mc.unity.core.Content"),
                                   SYNC = ClassDesc.of("mc.unity.core.LoginSync");
    private record
        Rule(String method, String descriptor, String invoke, boolean after, Consumer<CodeBuilder> emit) {}
    static void add(TransformPlan plan, boolean client) {
        var rules = new LinkedHashMap<String, List<Rule>>();
        rules.put("net/minecraft/core/registries/BuiltInRegistries",
                  List.of(new Rule("bootStrap", "()V", "freeze", false,
                                   b -> b.invokestatic(CONTENT, "register", MethodTypeDesc.of(CD_void))),
                          new Rule("bootStrap", "()V", "freeze", true,
                                   b -> b.invokestatic(CONTENT, "finish", MethodTypeDesc.of(CD_void)))));
        rules.put("net/minecraft/server/packs/repository/PackRepository",
                  List.of(new Rule(
                      "discoverAvailable", "()Ljava/util/Map;", "copyOf", false,
                      b
                      -> b.dup()
                             .aload(0)
                             .getfield(ClassDesc.of("net.minecraft.server.packs.repository.PackRepository"),
                                       "sources", ClassDesc.of("java.util.Set"))
                             .invokestatic(CONTENT, "packs", mt("(Ljava/util/Map;Ljava/util/Set;)V")))));
        rules.put(TAB,
                  List.of(new Rule(
                      "buildContents", "(L" + TAB + "$ItemDisplayParameters;)V", "accept", true,
                      b
                      -> b.aload(0).aload(2).aload(1).invokestatic(
                          CONTENT, "creative",
                          mt("(L" + TAB + ";Ljava/lang/Object;L" + TAB + "$ItemDisplayParameters;)V")))));
        rules.put(
            "net/minecraft/world/item/CreativeModeTabs",
            List.of(new Rule("streamAllTabs", "()Ljava/util/stream/Stream;", "stream", true,
                             b
                             -> b.invokestatic(CONTENT, "order",
                                               mt("(Ljava/util/stream/Stream;)Ljava/util/stream/Stream;")))));
        rules.put(BUILDER, List.of(new Rule("build", "()L" + TAB + ";", "<init>", true,
                                            b
                                            -> b.dup().aload(0).invokestatic(
                                                CONTENT, "built", mt("(L" + TAB + ";L" + BUILDER + ";)V")))));
        if (client) {
            rules.put(CLIENT,
                      List.of(new Rule("onDisconnect", "(Lnet/minecraft/network/DisconnectionDetails;)V",
                                       null, false,
                                       b -> {
                                           connection(b, CLIENT);
                                           b.invokestatic(SYNC, "forget", mt("(L" + CONNECTION + ";)V"));
                                       }),
                              guard(CLIENT, "handleCustomQuery", LOGIN + "ClientboundCustomQueryPacket",
                                    "query", false),
                              guard(CLIENT, "handleLoginFinished", LOGIN + "ClientboundLoginFinishedPacket",
                                    "rejectMissing", false)));
        }
        {
            rules.put(LOGIN + "ServerboundCustomQueryAnswerPacket",
                      List.of(new Rule("readPayload",
                                       "(ILnet/minecraft/network/FriendlyByteBuf;)L" + LOGIN +
                                           "custom/CustomQueryAnswerPayload;",
                                       null, false, b -> {
                                           var original = b.newLabel();
                                           b.iload(0)
                                               .ldc(0x554e4954)
                                               .if_icmpne(original)
                                               .aload(1)
                                               .invokestatic(SYNC, "readReply",
                                                             mt("(Lnet/minecraft/network/FriendlyByteBuf;)L" +
                                                                LOGIN + "custom/CustomQueryAnswerPayload;"))
                                               .areturn()
                                               .labelBinding(original);
                                       })));
            rules.put(SERVER, List.of(new Rule("onDisconnect",
                                               "(Lnet/minecraft/network/DisconnectionDetails;)V", null, false,
                                               b -> {
                                                   connection(b, SERVER);
                                                   b.invokestatic(SYNC, "forget",
                                                                  mt("(L" + CONNECTION + ";)V"));
                                               }),
                                      guard(SERVER, "handleCustomQueryPacket",
                                            LOGIN + "ServerboundCustomQueryAnswerPacket", "answer", true)));
        }
        for (var slot : rules.entrySet()) {
            String name = slot.getKey();
            int priority = 100;
            for (var rule : slot.getValue()) {
                plan.add(name, priority++, "Unity core", rule.method + rule.descriptor, (cf, model) -> {
                    int[] hits = {0};
                    var bytes =
                        cf.transformClass(model, ClassTransform.transformingMethodBodies(
                                                     m
                                                     -> m.methodName().equalsString(rule.method) &&
                                                            m.methodType().equalsString(rule.descriptor),
                                                     (b, e) -> {
                                                         boolean at =
                                                             rule.invoke == null
                                                                 ? (hits[0] == 0 && e instanceof Instruction)
                                                                 : (e instanceof InvokeInstruction i &&
                                                                    i.name().equalsString(rule.invoke) &&
                                                                    (!name.equals(BUILDER) ||
                                                                     i.owner().asInternalName().equals(TAB)));
                                                         if (at && !rule.after)
                                                             rule.emit.accept(b);
                                                         b.with(e);
                                                         if (at && rule.after)
                                                             rule.emit.accept(b);
                                                         if (at)
                                                             hits[0]++;
                                                     }));
                    if (hits[0] != 1)
                        throw new IOException("Expected one " + name + "." + rule.method +
                                              " anchor, observed " + hits[0]);
                    return bytes;
                });
            }
        }
    }
    /**
     * The Fabric login decoder owns unknown transactions. Route Unity's reserved ID before either
     * receiver consumes the packet. Other transactions still execute the entire transformed method.
     */
    static byte[] routeLogin(GameLoader loader, String owner, byte[] bytes) {
        String packet = LOGIN + "ServerboundCustomQueryAnswerPacket";
        Rule route;
        if (owner.equals(packet)) {
            route = new Rule("readPayload",
                             "(ILnet/minecraft/network/FriendlyByteBuf;)L" + LOGIN +
                                 "custom/CustomQueryAnswerPayload;",
                             null, false, b -> {
                                 var other = b.newLabel();
                                 b.iload(0)
                                     .ldc(0x554e4954)
                                     .if_icmpne(other)
                                     .aload(1)
                                     .invokestatic(SYNC, "readReply",
                                                   mt("(Lnet/minecraft/network/FriendlyByteBuf;)L" + LOGIN +
                                                      "custom/CustomQueryAnswerPayload;"))
                                     .areturn()
                                     .labelBinding(other);
                             });
        } else if (owner.equals(SERVER))
            route = guard(SERVER, "handleCustomQueryPacket", packet, "answer", true);
        else if (owner.equals(CLIENT))
            route =
                guard(CLIENT, "handleCustomQuery", LOGIN + "ClientboundCustomQueryPacket", "query", false);
        else
            return bytes;
        var file = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofResourceParsing(loader).cached()));
        var def = file.parse(bytes);
        return file.transformClass(def, ClassTransform.transformingMethodBodies(
                                            m
                                            -> m.methodName().equalsString(route.method) &&
                                                   m.methodType().equalsString(route.descriptor),
                                            new CodeTransform() {
                                                public void atStart(CodeBuilder b) {
                                                    route.emit.accept(b);
                                                }
                                                public void accept(CodeBuilder b, CodeElement e) {
                                                    b.with(e);
                                                }
                                            }));
    }
    private static MethodTypeDesc mt(String s) {
        return MethodTypeDesc.ofDescriptor(s);
    }
    private static void connection(CodeBuilder b, String owner) {
        b.aload(0).getfield(ClassDesc.ofInternalName(owner), "connection",
                            ClassDesc.ofInternalName(CONNECTION));
    }
    private static Rule guard(String owner, String method, String packet, String name, boolean server) {
        return new Rule(method, "(L" + packet + ";)V", null, false, b -> {
            if (server)
                b.aload(0);
            connection(b, owner);
            b.aload(1);
            b.invokestatic(
                SYNC, name,
                mt("(" + (server ? "L" + owner + ";" : "") + "L" + CONNECTION + ";L" + packet + ";)Z"));
            var normal = b.newLabel();
            b.ifeq(normal).return_().labelBinding(normal);
        });
    }
}
