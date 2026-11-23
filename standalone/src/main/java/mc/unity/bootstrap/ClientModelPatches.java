package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.ArrayList;

final class ClientModelPatches {
    static void add(TransformPlan plan) {
        var mode = ClassDesc.of("net.minecraft.client.multiplayer.MultiPlayerGameMode");
        plan.add("net/minecraft/client/multiplayer/MultiPlayerGameMode", 196, "Fabric interaction",
                 "selected item synchronized", (cf, m) -> {
                     int[] hits = {0};
                     var bytes = cf.transformClass(m, (b, el) -> {
                         if (!(el instanceof MethodModel method) ||
                             !(method.methodName().equalsString("useItem") ||
                               method.methodName().equalsString("useItemOn"))) {
                             b.with(el);
                             return;
                         }
                         b.transformMethod(method, MethodTransform.transformingCode((c, e) -> {
                             c.with(e);
                             if (e instanceof InvokeInstruction i &&
                                 i.name().equalsString("ensureHasSentCarriedItem")) {
                                 hits[0]++;
                                 var next = c.newLabel();
                                 c.aload(0).aload(1).aload(2);
                                 if (method.methodName().equalsString("useItemOn"))
                                     c.aload(3);
                                 else
                                     c.aconst_null();
                                 c.invokestatic(
                                      ClassDesc.of("mc.unity.client.Interactions"), "use",
                                      MethodTypeDesc.ofDescriptor(
                                          "(Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;Lnet/" +
                                          "minecraft/world/entity/player/Player;Lnet/minecraft/world/" +
                                          "InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/" +
                                          "minecraft/world/InteractionResult;"))
                                     .dup()
                                     .ifnull(next)
                                     .areturn()
                                     .labelBinding(next)
                                     .pop();
                             }
                         }));
                     });
                     if (hits[0] != 2)
                         throw new IOException("Interaction anchors " + hits[0]);
                     return bytes;
                 });
        var layers=ClassDesc.of("mc.unity.client.ModelLayers");
        for(boolean baking:new boolean[]{true,false}) {
            String owner="net/minecraft/client/model/geom/"+(baking?"LayerDefinitions":"ModelLayers");
            String name=baking?"createRoots":"getKnownLocations";
            plan.add(owner,190,"Entity model layers",name,(cf,model)->{
                int[] hits={0};
                var patched=cf.transformClass(model,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString(name),(b,e)->{
                    if(baking && e instanceof InvokeInstruction i && i.owner().asInternalName().equals("com/google/common/collect/ImmutableMap$Builder") && i.name().equalsString("build")) {
                        b.dup().invokestatic(layers,"bake",MethodTypeDesc.ofDescriptor("(Lcom/google/common/collect/ImmutableMap$Builder;)V")); hits[0]++;
                    } else if(!baking && e instanceof Instruction i && i.opcode()==Opcode.ARETURN) {
                        b.invokestatic(layers,"known",MethodTypeDesc.ofDescriptor("(Ljava/util/stream/Stream;)Ljava/util/stream/Stream;")); hits[0]++;
                    }
                    b.with(e);
                }));
                if(hits[0]!=1) throw new IOException("Missing model layer anchor "+name);
                return patched;
            });
        }
        String model = "net/minecraft/client/renderer/block/dispatch/BlockStateModel";
        String baker = "net/minecraft/client/resources/model/ModelBaker";
        String impl = "net/minecraft/client/resources/model/ModelBakery$ModelBakerImpl";
        var callback =
            ClassDesc.of("net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier$AfterBakeBlock");
        var hooks = ClassDesc.of("mc.unity.client.Models");
        plan.add(impl, 190, "Model reload context", "per-baker modifiers",
                 (cf, m)
                     -> cf.transformClass(
                         m, ClassTransform
                                .transformingMethodBodies(method
                                                          -> method.methodName().equalsString("<init>"),
                                                          (b, e) -> {
                                                              if (e instanceof ReturnInstruction)
                                                                  b.aload(0)
                                                                      .invokestatic(hooks, "begin",
                                                                                    MethodTypeDesc.of(callback))
                                                                      .putfield(ClassDesc.ofInternalName(impl),
                                                                                "unity$modifier", callback);
                                                              b.with(e);
                                                          })
                                .andThen(ClassTransform.endHandler(b -> {
                                    var list = new ArrayList<ClassDesc>();
                                    m.interfaces().forEach(it -> list.add(it.asSymbol()));
                                    list.add(ClassDesc.of("mc.unity.client.Models$Owner"));
                                    b.withInterfaceSymbols(list)
                                        .withField("unity$modifier", callback,
                                                   ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL)
                                        .withMethodBody("unity$modifier", MethodTypeDesc.of(callback),
                                                        ClassFile.ACC_PUBLIC,
                                                        c
                                                        -> c.aload(0)
                                                               .getfield(ClassDesc.ofInternalName(impl),
                                                                         "unity$modifier", callback)
                                                               .areturn());
                                }))));
        plan.add("net/minecraft/client/resources/model/ModelBakery", 190, "Model baking", "bake block state",
                 (cf, m) -> {
                     int[] count = {0};
                     var bytes = cf.transformClass(m, ClassTransform.transformingMethodBodies((b, e) -> {
                         if (e instanceof InvokeInstruction i &&
                             i.owner().asInternalName().equals(model + "$UnbakedRoot") &&
                             i.name().equalsString("bake")) {
                             count[0]++;
                             b.invokestatic(hooks, "bake",
                                            i.typeSymbol().insertParameterTypes(0, i.owner().asSymbol()));
                         } else
                             b.with(e);
                     }));
                     if (count[0] != 1)
                         throw new IOException("Model bake anchors: " + count[0]);
                     return bytes;
                 });
    }
}
