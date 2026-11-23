package mc.unity.adapter.forge.transform;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.util.*;
import mc.unity.bootstrap.TransformPlan;
public final class ForgeGamePatches {
    private static final String TAB = "net/minecraft/world/item/CreativeModeTab", BUILDER = TAB + "$Builder";
    private static final ClassDesc CONTENT = ClassDesc.of("mc.unity.core.Content");
    private static MethodTypeDesc mt(String s) {
        return MethodTypeDesc.ofDescriptor(s);
    }
    public static void add(TransformPlan plan, boolean client) {
        for (var name : List.of(TAB, BUILDER, "net/minecraft/world/item/CreativeModeTabs")) {
            if (name.equals("net/minecraft/world/item/CreativeModeTabs")) {
                plan.add(name, 200, "Unity Forge", "creative tab keys",
                    (cf, model) -> cf.transformClass(model, (b, e) -> {
                        if (e instanceof FieldModel field
                            && field.flags().has(java.lang.reflect.AccessFlag.STATIC)
                            && field.fieldType().equalsString("Lnet/minecraft/resources/ResourceKey;")) {
                            b.transformField(field, (fb, fe) -> {
                                if (fe instanceof AccessFlags flags)
                                    fb.withFlags(
                                        (flags.flagsMask() & ~(ClassFile.ACC_PRIVATE | ClassFile.ACC_PROTECTED))
                                        | ClassFile.ACC_PUBLIC);
                                else
                                    fb.with(fe);
                            });
                        } else
                            b.with(e);
                    }));
            }
            if (name.equals(TAB) || name.equals(BUILDER)) {
                plan.add(name, 200, "Unity Forge", "builder methods",
                    (cf, model) -> cf.transformClass(model, ClassTransform.endHandler(b -> {
                        if (name.equals(TAB))
                            b.withMethodBody("builder", mt("()L" + BUILDER + ";"),
                                ClassFile.ACC_STATIC | ClassFile.ACC_PUBLIC,
                                c
                                -> c.invokestatic(CONTENT, "tabBuilder", mt("()L" + BUILDER + ";")).areturn());
                        else
                            b.withMethodBody("withTabsBefore",
                                mt("([Lnet/minecraft/resources/ResourceKey;)L" + BUILDER + ";"),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_VARARGS,
                                c
                                -> c.aload(0)
                                    .aload(1)
                                    .invokestatic(CONTENT, "tabsBefore",
                                        mt("(L" + BUILDER + ";[Lnet/minecraft/resources/ResourceKey;)L"
                                            + BUILDER + ";"))
                                    .areturn());
                    })));
            }
        }
        publicType(plan, "net/minecraft/world/inventory/MenuType$MenuSupplier");
        publicType(plan, "net/minecraft/world/level/block/entity/BlockEntityType$BlockEntitySupplier");
        if (client)
            publicType(plan, "net/minecraft/client/gui/screens/MenuScreens$ScreenConstructor");
        expose(plan, "net/minecraft/world/inventory/MenuType", "<init>",
            "(Lnet/minecraft/world/inventory/MenuType$MenuSupplier;Lnet/minecraft/world/flag/"
                + "FeatureFlagSet;)V");
        if (client)
            expose(plan, "net/minecraft/client/gui/screens/MenuScreens", "register",
                "(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/gui/screens/"
                    + "MenuScreens$ScreenConstructor;)V");
    }
    private static void publicType(TransformPlan plan, String type) {
        plan.add(type, 220, "Forge API", "class visibility", (cf, m) -> cf.transformClass(m, (b, e) -> {
            if (e instanceof AccessFlags flags)
                b.withFlags((flags.flagsMask() & ~(ClassFile.ACC_PRIVATE | ClassFile.ACC_PROTECTED))
                    | ClassFile.ACC_PUBLIC);
            else
                b.with(e);
        }));
    }
    private static void expose(TransformPlan p, String owner, String name, String sig) {
        p.add(owner, 220, "Forge API", name + sig, (cf, m) -> {
            int[] hits = {0};
            var bytes = cf.transformClass(m, (b, e) -> {
                if (e instanceof MethodModel method && method.methodName().equalsString(name)
                    && method.methodType().equalsString(sig)) {
                    hits[0]++;
                    b.transformMethod(method, (mb, me) -> {
                        if (me instanceof AccessFlags flags)
                            mb.withFlags(
                                (flags.flagsMask() & ~(ClassFile.ACC_PRIVATE | ClassFile.ACC_PROTECTED))
                                | ClassFile.ACC_PUBLIC);
                        else
                            mb.with(me);
                    });
                } else
                    b.with(e);
            });
            if (hits[0] != 1)
                throw new IllegalStateException(owner + "." + name);
            return bytes;
        });
    }
}
