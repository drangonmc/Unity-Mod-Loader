package mc.unity.bootstrap;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
/** Capture the instantiated SAM at the lambda factory, so Java control flow can move the same object freely. */
final class TypedListeners {
    private static boolean possibleEvent(ClassDesc type) {
        String descriptor = type.descriptorString();
        return type.isClassOrInterface() && !descriptor.startsWith("Ljava/") &&
            !descriptor.startsWith("Lnet/minecraft/");
    }
    static byte[] adapt(byte[] bytes, ClassLoader domain) {
        var cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofResourceParsing(domain).cached()));
        var model = cf.parse(bytes);
        var events = new java.util.HashMap<Integer, ClassDesc>();
        for (var constant : model.constantPool()) {
            if (!(constant instanceof InvokeDynamicEntry site) ||
                !site.type().stringValue().endsWith(")Ljava/util/function/Consumer;"))
                continue;
            var bootstrap = site.bootstrap();
            if (!bootstrap.bootstrapMethod().reference().owner().asInternalName().equals(
                    "java/lang/invoke/LambdaMetafactory") ||
                bootstrap.arguments().size() < 3 ||
                !(bootstrap.arguments().get(2) instanceof MethodTypeEntry argument))
                continue;
            var signature = argument.asSymbol();
            if (signature.parameterCount() == 1 && possibleEvent(signature.parameterType(0)))
                events.put(site.index(), signature.parameterType(0));
        }
        if (events.isEmpty())
            return bytes;
        return cf.transformClass(model, ClassTransform.transformingMethodBodies((b, e) -> {
            b.with(e);
            if (e instanceof InvokeDynamicInstruction i && events.containsKey(i.invokedynamic().index())) {
                b.dup()
                    .ldc(events.get(i.invokedynamic().index()))
                    .invokestatic(
                        ClassDesc.of("mc.unity.adapter.neoforge.ListenerTypes"), "capture",
                        MethodTypeDesc.ofDescriptor("(Ljava/util/function/Consumer;Ljava/lang/Class;)V"));
            }
        }));
    }
}
