package mc.unity.bootstrap;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.io.IOException;
final class CreativePagesPatch {
    static void add(TransformPlan plan) {
        String screen = "net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen",
               tab = "net/minecraft/world/item/CreativeModeTab", key = "net/minecraft/client/input/KeyEvent";
        plan.add("net/minecraft/world/item/ItemStack", 156, "Item tooltip", "getTooltipLines", (cf, model) -> {
            String item = "Lnet/minecraft/world/item/";
            String signature = "(" + item + "Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;" + item + "TooltipFlag;)Ljava/util/List;";
            int[] hits = {0};
            var bytes = cf.transformClass(model, ClassTransform.transformingMethodBodies(
                m -> m.methodName().equalsString("getTooltipLines") && m.methodType().equalsString(signature),
                (b,e) -> {
                    if (e instanceof java.lang.classfile.instruction.ReturnInstruction r && r.opcode() == Opcode.ARETURN) {
                        hits[0]++;
                        b.aload(0).aload(1).aload(3).invokestatic(ClassDesc.of("mc.unity.core.Events"), "tooltip",
                            MethodTypeDesc.ofDescriptor("(Ljava/util/List;" + item + "ItemStack;" + item + "Item$TooltipContext;" + item + "TooltipFlag;)Ljava/util/List;"));
                    }
                    b.with(e);
                }));
            if (hits[0] == 0) throw new IOException("Missing item tooltip return");
            return bytes;
        });
        var pages = ClassDesc.of("mc.unity.core.TabPages");
        var ct = ClassDesc.ofInternalName(tab);
        var cs = ClassDesc.ofInternalName(screen);
        plan.add(screen, 155, "Unity creative tabs", "visible pages", (cf, model) -> {
            int[] matches = new int[4];
            var bytes = cf.transformClass(model, (builder, el) -> {
                if (!(el instanceof MethodModel method)) {
                    builder.with(el);
                    return;
                }
                boolean[] first = {true};
                builder.transformMethod(method, MethodTransform.transformingCode((b, e) -> {
                    if (first[0] && e instanceof Instruction) {
                        first[0] = false;
                        if (method.methodName().equalsString("keyPressed")
                            && method.methodType().equalsString("(L" + key + ";)Z")) {
                            var next = b.newLabel();
                            b.aload(1)
                                .invokevirtual(
                                    ClassDesc.ofInternalName(key), "key", MethodTypeDesc.ofDescriptor("()I"))
                                .invokestatic(pages, "turn", MethodTypeDesc.ofDescriptor("(I)L" + tab + ";"))
                                .dup()
                                .ifnull(next)
                                .aload(0)
                                .swap()
                                .invokevirtual(cs, "selectTab", MethodTypeDesc.ofDescriptor("(L" + tab + ";)V"))
                                .iconst_1()
                                .ireturn()
                                .labelBinding(next)
                                .pop();
                            matches[0]++;
                        } else if (method.methodName().equalsString("selectTab"))
                            b.aload(1).invokestatic(
                                pages, "selected", MethodTypeDesc.ofDescriptor("(L" + tab + ";)V"));
                    }
                    if (e instanceof InvokeInstruction i) {
                        if (i.owner().asInternalName().equals(tab + "s") && i.name().equalsString("tabs")) {
                            b.invokestatic(pages, "visible", i.typeSymbol());
                            matches[1]++;
                            return;
                        }
                        if (i.owner().asInternalName().equals(tab)
                            && (i.name().equalsString("column") || i.name().equalsString("row"))) {
                            b.invokestatic(
                                pages, i.name().stringValue(), i.typeSymbol().insertParameterTypes(0, ct));
                            matches[i.name().equalsString("column") ? 2 : 3]++;
                            return;
                        }
                    }
                    b.with(e);
                }));
            });
            if (matches[0] != 1 || matches[1] < 1 || matches[2] < 1 || matches[3] < 1)
                throw new IOException("Unexpected creative screen");
            return bytes;
        });
    }
}
