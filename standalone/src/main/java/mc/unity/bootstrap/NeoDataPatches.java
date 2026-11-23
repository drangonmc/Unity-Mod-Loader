package mc.unity.bootstrap;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.lang.reflect.AccessFlag;
final class NeoDataPatches {
    private static final String TYPE = "Lnet/neoforged/neoforge/registries/datamaps/DataMapType;";
    private static final ClassDesc MAPS = ClassDesc.of("mc.unity.adapter.neoforge.DataMaps");
    static void add(TransformPlan plan) {
        extend(plan, "net/minecraft/core/Holder", "getData", "(" + TYPE + ")Ljava/lang/Object;", "holder",
               "(Lnet/minecraft/core/Holder;" + TYPE + ")Ljava/lang/Object;", 0, 1);
        extend(plan, "net/minecraft/core/HolderLookup$RegistryLookup", "getData",
               "(" + TYPE + "Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;", "lookup",
               "(" + TYPE + "Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;", 1, 2);
        extend(plan, "net/minecraft/core/Registry", "getDataMap", "(" + TYPE + ")Ljava/util/Map;", "all",
               "(" + TYPE + ")Ljava/util/Map;", 1);
    }
    private static void extend(TransformPlan plan, String target, String method, String desc, String hook,
                               String sig, int... arguments) {
        plan.add(target, 146, "Registry data maps", method,
                 (cf, model)
                     -> cf.transformClass(
                         model,
                         ClassTransform.endHandler(
                             b
                             -> b.withMethodBody(
                                 method, MethodTypeDesc.ofDescriptor(desc), AccessFlag.PUBLIC.mask(), code -> {
                                     for (int arg : arguments)
                                         code.aload(arg);
                                     code.invokestatic(MAPS, hook, MethodTypeDesc.ofDescriptor(sig)).areturn();
                                 }))));
    }
}
