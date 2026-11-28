package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.util.*;

final class AttachmentPatches {
    private static final ClassDesc DATA = ClassDesc.of("mc.unity.core.Attachments"),
                                   OWNER = ClassDesc.of("mc.unity.core.Attachments$Owner"),
                                   RECORD = ClassDesc.of("mc.unity.core.Attachments$Serialized"),
                                   TAG = ClassDesc.of("net.minecraft.nbt.CompoundTag"),
                                   CHUNK = ClassDesc.of("net.minecraft.world.level.chunk.ChunkAccess"),
                                   PROTO = ClassDesc.of("net.minecraft.world.level.chunk.ProtoChunk"),
                                   LEVEL = ClassDesc.of("net.minecraft.server.level.ServerLevel");
    private static final ClassDesc INPUT = ClassDesc.of("net.minecraft.world.level.storage.ValueInput"),
                                   OUTPUT = ClassDesc.of("net.minecraft.world.level.storage.ValueOutput");
    private static String name(ClassDesc c) {
        var descriptor = c.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1);
    }
    static void add(TransformPlan plan) {
        plan.add(name(LEVEL),86,"Unity level attachments","dimension saved data",(cf,m)->cf.transformClass(m,ClassTransform.endHandler(b->
            b.withMethodBody("unity$attachments",MethodTypeDesc.of(DATA,ConstantDescs.CD_boolean),ClassFile.ACC_PUBLIC,c->c.aload(0).iload(1)
                .invokestatic(DATA,"ofLevel",MethodTypeDesc.of(DATA,LEVEL,ConstantDescs.CD_boolean)).areturn()))));

        for (var type : List.of(CHUNK, ClassDesc.of("net.minecraft.world.level.Level"), ClassDesc.of("net.minecraft.world.entity.Entity"),
                                ClassDesc.of("net.minecraft.world.level.block.entity.BlockEntity"))) {
            plan.add(name(type), 85, "Unity attachments", "holder fields",
                     (cf, m) -> cf.transformClass(m, ClassTransform.endHandler(b -> {
                         var faces = new ArrayList<ClassDesc>();
                         m.interfaces().forEach(f -> faces.add(f.asSymbol()));
                         faces.add(ClassDesc.of("net.neoforged.neoforge.attachment.IAttachmentHolder"));
                         faces.add(ClassDesc.of("net.fabricmc.fabric.api.attachment.v1.AttachmentTarget"));
                         b.withInterfaceSymbols(faces)
                             .withField("unity$attachments", DATA, ClassFile.ACC_PRIVATE)
                             .withMethodBody("unity$attachments",
                                             MethodTypeDesc.of(DATA, ConstantDescs.CD_boolean),
                                             ClassFile.ACC_PUBLIC, c -> {
                                                 var done = c.newLabel();
                                                 c.iload(1)
                                                     .ifeq(done)
                                                     .aload(0)
                                                     .getfield(type, "unity$attachments", DATA)
                                                     .ifnonnull(done)
                                                     .aload(0)
                                                     .new_(DATA)
                                                     .dup()
                                                     .invokespecial(DATA, "<init>",
                                                                    MethodTypeDesc.of(ConstantDescs.CD_void))
                                                     .putfield(type, "unity$attachments", DATA)
                                                     .labelBinding(done)
                                                     .aload(0)
                                                     .getfield(type, "unity$attachments", DATA)
                                                     .areturn();
                                             });
                     })));
            if (type.equals(CHUNK) || name(type).equals("net/minecraft/world/level/Level"))
                continue;
            boolean block = name(type).endsWith("/BlockEntity");
            var methods = block ? Set.of("loadCustomOnly", "loadWithComponents", "saveCustomOnly",
                                         "saveWithoutMetadata")
                                : Set.of("load", "saveWithoutId");
            plan.add(name(type), 86, "Unity attachments", "value io", (cf, m) -> {
                var found = new HashSet<String>();
                var result = cf.transformClass(m, (b, e) -> {
                    if (!(e instanceof MethodModel method) ||
                        !methods.contains(method.methodName().stringValue())) {
                        b.with(e);
                        return;
                    }
                    var read = method.methodName().stringValue().startsWith("load");
                    var arg = read ? INPUT : OUTPUT;
                    if (!method.methodTypeSymbol().equals(MethodTypeDesc.of(ConstantDescs.CD_void, arg))) {
                        b.with(e);
                        return;
                    }
                    b.transformMethod(method, MethodTransform.transformingCode((code, el) -> {
                        if (el instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                            code.aload(0).aload(1).invokestatic(
                                DATA, read ? "load" : "save",
                                MethodTypeDesc.of(ConstantDescs.CD_void, OWNER, arg));
                            found.add(method.methodName().stringValue());
                        }
                        code.with(el);
                    }));
                });
                if (!found.equals(methods))
                    throw new IOException("Missing attachment save/load hooks " + found);
                return result;
            });
        }
        var player = ClassDesc.of("net.minecraft.server.level.ServerPlayer");
        plan.add(name(player), 85, "Unity attachments", "restore player", (cf, m) -> {
            int[] hits = {0};
            var result =
                cf.transformClass(m, ClassTransform.transformingMethodBodies(
                                         method
                                         -> method.methodName().equalsString("restoreFrom") &&
                                                method.methodTypeSymbol().equals(MethodTypeDesc.of(
                                                    ConstantDescs.CD_void, player, ConstantDescs.CD_boolean)),
                                         (b, e) -> {
                                             if (e instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                                                 b.aload(0).aload(1).iload(2).invokestatic(
                                                     DATA, "respawn",
                                                     MethodTypeDesc.of(ConstantDescs.CD_void, player, player,
                                                                       ConstantDescs.CD_boolean));
                                                 hits[0]++;
                                             }
                                             b.with(e);
                                         }));
            if (hits[0] != 1)
                throw new IOException("Player respawn anchor");
            return result;
        });
        var imposter = ClassDesc.of("net.minecraft.world.level.chunk.ImposterProtoChunk");
        var levelChunk = ClassDesc.of("net.minecraft.world.level.chunk.LevelChunk");
        plan.add(
            name(imposter), 85, "Unity attachments", "wrapped chunk",
            (cf, m)
                -> cf.transformClass(
                    m, ClassTransform.endHandler(
                           b
                           -> b.withMethodBody(
                               "unity$attachments", MethodTypeDesc.of(DATA, ConstantDescs.CD_boolean),
                               ClassFile.ACC_PUBLIC,
                               code
                               -> code.aload(0)
                                      .invokevirtual(imposter, "getWrapped", MethodTypeDesc.of(levelChunk))
                                      .iload(1)
                                      .invokeinterface(OWNER, "unity$attachments",
                                                       MethodTypeDesc.of(DATA, ConstantDescs.CD_boolean))
                                      .areturn()))));
        plan.add(name(levelChunk), 85, "Unity attachments", "promote chunk", (cf, m) -> {
            int[] hits = {0};
            var bytes = cf.transformClass(
                m, ClassTransform.transformingMethodBodies(
                       method
                       -> method.methodName().equalsString("<init>") &&
                              method.methodTypeSymbol().parameterList().contains(PROTO),
                       (b, e) -> {
                           if (e instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                               b.aload(0).aload(2).invokestatic(
                                   DATA, "promote", MethodTypeDesc.of(ConstantDescs.CD_void, OWNER, PROTO));
                               hits[0]++;
                           }
                           b.with(e);
                       }));
            if (hits[0] != 1)
                throw new IOException("Missing chunk promotion anchor");
            return bytes;
        });
        var type = ClassDesc.of("net.minecraft.world.level.chunk.storage.SerializableChunkData");
        plan.add(name(type), 85, "Unity attachments", "chunk recording", (cf, m) -> {
            var found = new HashSet<String>();
            var bytes = cf.transformClass(m, (b, e) -> {
                if (!(e instanceof MethodModel method)) {
                    b.with(e);
                    return;
                }
                var name = method.methodName().stringValue();
                b.transformMethod(method, MethodTransform.transformingCode((code, el) -> {
                    if (el instanceof Instruction i && i.opcode() == Opcode.ARETURN) {
                        switch (name) {
                        case "parse" ->
                            code.dup().aload(2).invokestatic(
                                DATA, "parsed", MethodTypeDesc.of(ConstantDescs.CD_void, RECORD, TAG));
                        case "read" ->
                            code.dup().aload(0).aload(1).invokestatic(
                                DATA, "unpack",
                                MethodTypeDesc.of(ConstantDescs.CD_void, PROTO, RECORD, LEVEL));
                        case "copyOf" ->
                            code.dup().aload(0).aload(1).invokestatic(
                                DATA, "pack", MethodTypeDesc.of(ConstantDescs.CD_void, RECORD, LEVEL, CHUNK));
                        case "write" ->
                            code.dup().aload(0).invokestatic(
                                DATA, "written", MethodTypeDesc.of(ConstantDescs.CD_void, TAG, RECORD));
                        default -> {
                            code.with(el);
                            return;
                        }
                        }
                        found.add(name);
                    }
                    code.with(el);
                }));
            });
            if (found.size() != 4)
                throw new IOException("Missing chunk attachment IO hooks " + found);
            return cf.transformClass(cf.parse(bytes), ClassTransform.endHandler(b -> {
                var faces = new ArrayList<ClassDesc>();
                m.interfaces().forEach(f -> faces.add(f.asSymbol()));
                faces.add(RECORD);
                b.withInterfaceSymbols(faces)
                    .withField("unity$tag", TAG, ClassFile.ACC_PRIVATE)
                    .withMethodBody("unity$raw", MethodTypeDesc.of(TAG), ClassFile.ACC_PUBLIC,
                                    c -> c.aload(0).getfield(type, "unity$tag", TAG).areturn())
                    .withMethodBody("unity$raw", MethodTypeDesc.of(ConstantDescs.CD_void, TAG),
                                    ClassFile.ACC_PUBLIC,
                                    c -> c.aload(0).aload(1).putfield(type, "unity$tag", TAG).return_());
            }));
        });
    }
}
