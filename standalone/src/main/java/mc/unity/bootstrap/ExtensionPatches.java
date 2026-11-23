package mc.unity.bootstrap;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.ArrayList;

final class ExtensionPatches {
    static void add(TransformPlan plan) {
        for(var entry:java.util.Map.of("net/minecraft/world/level/Level","ILevelExtension","net/minecraft/world/item/ItemStack","IItemStackExtension").entrySet()){
            plan.add(entry.getKey(),45,"NeoForge capabilities","type interfaces",(cf,model)->cf.transformClass(model,ClassTransform.endHandler(b->{
                var faces=new ArrayList<ClassDesc>();model.interfaces().forEach(f->faces.add(f.asSymbol()));
                faces.add(ClassDesc.of("net.neoforged.neoforge.common.extensions."+entry.getValue()));b.withInterfaceSymbols(faces);
            })));
        }
        for (boolean block : new boolean[] {false, true}) {
            var target = block ? "net/minecraft/world/level/block/Block" : "net/minecraft/world/item/Item";
            plan.add(target, 45, "Platform extensions", "type interfaces",
                     (cf, model) -> cf.transformClass(model, ClassTransform.endHandler(b -> {
                         var interfaces = new ArrayList<ClassDesc>();
                         model.interfaces().forEach(i -> interfaces.add(i.asSymbol()));
                         interfaces.add(ClassDesc.of("net.minecraftforge.common.extensions." +
                                                     (block ? "IForgeBlock" : "IForgeItem")));
                         interfaces.add(ClassDesc.of("net.neoforged.neoforge.common.extensions." +
                                                     (block ? "IBlockExtension" : "IItemExtension")));
                         b.withInterfaceSymbols(interfaces);
                     })));
        }
        plan.add(
            "net/minecraft/world/level/ExplosionDamageCalculator", 70, "Block resistance",
            "getBlockExplosionResistance", (cf, model) -> {
                int[] hits = {0};
                var bytes = cf.transformClass(
                    model,
                    ClassTransform.transformingMethodBodies(
                        method -> method.methodName().equalsString("getBlockExplosionResistance"), (b, e) -> {
                            if (e instanceof InvokeInstruction call &&
                                call.owner().asInternalName().equals(
                                    "net/minecraft/world/level/block/Block") &&
                                call.name().equalsString("getExplosionResistance") &&
                                call.type().equalsString("()F")) {
                                b.aload(4).aload(2).aload(3).aload(1).invokeinterface(
                                    ClassDesc.of("mc.unity.core.Extensions$BlockBehavior"),
                                    "getExplosionResistance",
                                    MethodTypeDesc.ofDescriptor(
                                        "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/" +
                                        "world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/" +
                                        "minecraft/world/level/Explosion;)F"));
                                hits[0]++;
                            } else
                                b.with(e);
                        }));
                if (hits[0] != 1)
                    throw new IOException("Block resistance anchor missing");
                return bytes;
            });
        plan.add("net/minecraft/world/entity/monster/piglin/PiglinAi", 70, "Item barter currency",
                 "isBarterCurrency", (cf, model) -> {
                     int[] hits = {0};
                     var result = cf.transformClass(model, (b, e) -> {
                         if (e instanceof MethodModel m && m.methodName().equalsString("isBarterCurrency") &&
                             m.methodType().equalsString("(Lnet/minecraft/world/item/ItemStack;)Z")) {
                             b.withMethodBody(m.methodName(), m.methodType(), m.flags().flagsMask(),
                                              c
                                              -> c.aload(0)
                                                     .invokestatic(ClassDesc.of("mc.unity.core.Extensions"),
                                                                   "currency", m.methodTypeSymbol())
                                                     .ireturn());
                             hits[0]++;
                         } else
                             b.with(e);
                     });
                     if (hits[0] != 1)
                         throw new IOException("Barter currency anchor missing");
                     return result;
                 });
    }
}
