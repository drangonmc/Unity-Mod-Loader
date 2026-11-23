package mc.unity.bootstrap;
import java.io.*;
import java.util.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
final class EcologyPatches {
    static void add(TransformPlan plan) {
        String table = "net/minecraft/world/level/storage/loot/LootTable";
        plan.add(table, 65, "Global loot modifiers", "final rolls", (cf, model) -> {
            int[] hits = {0, 0};
            var bytes = cf.transformClass(
                model, ClassTransform.transformingMethodBodies(
                           method -> method.methodName().equalsString("getRandomItems"), (code, e) -> {
                               if (e instanceof InvokeInstruction call &&
                                   call.owner().asInternalName().equals(table) &&
                                   call.name().equalsString("getRandomItemsRaw")) {
                                   boolean params = call.type().stringValue().contains("LootParams;");
                                   var signature = call.typeSymbol().insertParameterTypes(
                                       0, ClassDesc.ofInternalName(table));
                                   if (params) {
                                       var optional = ClassDesc.of("java.util.Optional");
                                       code.aload(0).getfield(ClassDesc.ofInternalName(table),
                                                              "randomSequence", optional);
                                       signature = signature.insertParameterTypes(signature.parameterCount(),
                                                                                  optional);
                                   }
                                   code.invokestatic(ClassDesc.of("mc.unity.core.Drops"), "roll", signature);
                                   hits[params ? 1 : 0]++;
                               } else
                                   code.with(e);
                           }));
            if (hits[0] != 2 || hits[1] != 1)
                throw new IOException("Global loot roll anchors " + Arrays.toString(hits));
            return bytes;
        });

        plan.add("net/minecraft/world/entity/ai/attributes/DefaultAttributes", 70, "Unity entity attributes",
                 "registry reads", (file, model) -> {
                     int[] hits = {0, 0};
                     var api = ClassDesc.of("mc.unity.core.EntityAttributes");
                     var result = file.transformClass(
                         model,
                         ClassTransform.transformingMethodBodies(
                             method
                             -> method.methodName().equalsString("getSupplier") ||
                                    method.methodName().equalsString("hasSupplier"),
                             (b, e) -> {
                                 if (e instanceof java.lang.classfile.instruction.InvokeInstruction i &&
                                     i.owner().asInternalName().equals("java/util/Map")) {
                                     if (i.name().equalsString("get")) {
                                         hits[0]++;
                                         b.invokestatic(
                                             api, "lookup",
                                             MethodTypeDesc.ofDescriptor(
                                                 "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;"));
                                         return;
                                     }
                                     if (i.name().equalsString("containsKey")) {
                                         hits[1]++;
                                         b.invokestatic(api, "contains",
                                                        MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/util/Map;Ljava/lang/Object;)Z"));
                                         return;
                                     }
                                 }
                                 b.with(e);
                             }));
                     if (hits[0] != 1 || hits[1] != 1)
                         throw new IOException("Missing default attribute registry anchors");
                     return result;
                 });
        plan.add(
            "net/minecraft/core/MappedRegistry", 70, "Attribute holders", "register attributes",
            (file, model) -> {
                var key = ClassDesc.of("net.minecraft.resources.ResourceKey");
                int[] hits = {0};
                var bytes = file.transformClass(
                    model,
                    ClassTransform.transformingMethodBodies(
                        m
                        -> m.methodName().equalsString("register") &&
                               m.methodType().equalsString(
                                   "(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/" +
                                   "core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;"),
                        (b, e) -> {
                            if (e instanceof Instruction i && i.opcode() == Opcode.ARETURN) {
                                var done = b.newLabel();
                                var bind = b.newLabel();
                                b.aload(1)
                                    .getstatic(ClassDesc.of("net.minecraft.core.registries.Registries"),
                                               "ATTRIBUTE", key)
                                    .invokevirtual(key, "isFor",
                                                   MethodTypeDesc.of(ConstantDescs.CD_boolean, key))
                                    .ifne(bind)
                                    .aload(1)
                                    .getstatic(ClassDesc.of("net.minecraft.core.registries.Registries"),
                                               "LOOT_TABLE", key)
                                    .invokevirtual(key, "isFor",
                                                   MethodTypeDesc.of(ConstantDescs.CD_boolean, key))
                                    .ifeq(done)
                                    .labelBinding(bind)
                                    .dup()
                                    .aload(2)
                                    .invokevirtual(
                                        ClassDesc.of("net.minecraft.core.Holder$Reference"), "bindValue",
                                        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object))
                                    .labelBinding(done);
                                hits[0]++;
                            }
                            b.with(e);
                        }));
                if (hits[0] != 1)
                    throw new IOException("Attribute holder registration anchor");
                return bytes;
            });
        String resources = "net/minecraft/server/ReloadableServerResources",
               result = "net/minecraft/server/ReloadableServerRegistries$LoadResult";
        plan.add(resources, 70, "Server data reload", "listeners", (file, model) -> {
            int[] hits = {0};
            var bytes = file.transformClass(
                model,
                ClassTransform.transformingMethodBodies(
                    method
                    -> method.flags().has(java.lang.reflect.AccessFlag.STATIC) &&
                           method.methodType().stringValue().startsWith("(L" + result + ";"),
                    (b, e) -> {
                        if (e instanceof java.lang.classfile.instruction.InvokeInstruction i &&
                            i.owner().asInternalName().equals(resources) &&
                            i.name().equalsString("listeners")) {
                            b.aload(0)
                                .invokevirtual(ClassDesc.ofInternalName(result), "layers",
                                               MethodTypeDesc.ofDescriptor(
                                                   "()Lnet/minecraft/core/LayeredRegistryAccess;"))
                                .invokevirtual(ClassDesc.of("net.minecraft.core.LayeredRegistryAccess"),
                                               "compositeAccess",
                                               MethodTypeDesc.ofDescriptor(
                                                   "()Lnet/minecraft/core/RegistryAccess$Frozen;"))
                                .invokestatic(ClassDesc.of("mc.unity.core.Reloads"), "collect",
                                              MethodTypeDesc.ofDescriptor(
                                                  "(L" + resources +
                                                  ";Lnet/minecraft/core/RegistryAccess;)Ljava/util/List;"));
                            hits[0]++;
                        } else
                            b.with(e);
                    }));
            if (hits[0] != 1)
                throw new IOException("Missing server data reload anchor");
            return bytes;
        });
        plan.add("net/minecraft/server/packs/resources/Resource", 70, "Fabric resource source",
                 "FabricResource",
                 (file, model) -> file.transformClass(model, ClassTransform.endHandler(builder -> {
                     var interfaces = new ArrayList<ClassDesc>();
                     model.interfaces().forEach(it -> interfaces.add(it.asSymbol()));
                     interfaces.add(ClassDesc.of("net.fabricmc.fabric.api.resource.v1.FabricResource"));
                     builder.withInterfaceSymbols(interfaces);
                 })));
        String be = "net/minecraft/world/level/block/entity/BlockEntity";
        plan.add("net/minecraft/world/level/chunk/LevelChunk", 70, "Block entity lifecycle",
                 "load and remove", (file, model) -> {
                     int[] hits = {0, 0, 0};
                     var signal = ClassDesc.of("mc.unity.core.Events");
                     var bytes = file.transformClass(model, (builder, element) -> {
                         if (!(element instanceof MethodModel method)) {
                             builder.with(element);
                             return;
                         }
                         String name = method.methodName().stringValue();
                         builder.transformMethod(method, MethodTransform.transformingCode((b, e) -> {
                             if (e instanceof InvokeInstruction i) {
                                 if (name.equals("setBlockEntity") &&
                                     i.owner().asInternalName().equals("java/util/Map") &&
                                     i.name().equalsString("put")) {
                                     b.with(e);
                                     b.dup().aload(1).invokestatic(
                                         signal, "loadBlockEntity",
                                         MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;L" + be + ";)V"));
                                     hits[0]++;
                                     return;
                                 }
                                 if ((name.equals("setBlockEntity") || name.equals("removeBlockEntity")) &&
                                     i.owner().asInternalName().equals(be) &&
                                     i.name().equalsString("setRemoved")) {
                                     b.dup();
                                     b.with(e);
                                     b.invokestatic(signal, "unloadBlockEntity",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));
                                     hits[1]++;
                                     return;
                                 }
                                 if (name.equals("getBlockEntity") &&
                                     i.owner().asInternalName().equals("java/util/Map") &&
                                     i.name().equalsString("remove")) {
                                     b.with(e);
                                     b.dup().invokestatic(
                                         signal, "unloadBlockEntity",
                                         MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));
                                     hits[2]++;
                                     return;
                                 }
                             }
                             b.with(e);
                         }));
                     });
                     if (hits[0] != 1 || hits[1] != 2 || hits[2] == 0)
                         throw new IOException("Block entity lifecycle anchors " + Arrays.toString(hits));
                     return bytes;
                 });
    }
}
