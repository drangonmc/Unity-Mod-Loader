package mc.unity.bootstrap;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import static java.lang.constant.ConstantDescs.*;

final class NeoFuelPatches {
    static void add(TransformPlan plan) {
        String target="net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity";
        var owner=ClassDesc.ofInternalName(target);
        var stack=ClassDesc.of("net.minecraft.world.item.ItemStack");
        var recipe=ClassDesc.of("net.minecraft.world.item.crafting.RecipeType");
        var fuels=ClassDesc.of("net.minecraft.world.level.block.entity.FuelValues");
        plan.add(target,152,"NeoForge","stack-aware fuel checks",(cf,model)->{
            int[] hits={0,0,0};
            ClassTransform calls=(b,e)->{
                if (e instanceof MethodModel ctor && ctor.methodName().equalsString("<init>")) {
                    b.transformMethod(ctor,MethodTransform.transformingCode((c,ins)->{
                        if (ins instanceof InvokeInstruction call && call.name().equalsString("createCheck")
                                && call.owner().asInternalName().equals("net/minecraft/world/item/crafting/RecipeManager")
                                && call.typeSymbol().parameterList().equals(java.util.List.of(recipe))) {
                            hits[2]++;
                            c.dup2().putfield(owner,"recipeType",recipe);
                        }
                        c.with(ins);
                    }));
                    return;
                }
                if (!(e instanceof MethodModel m) || !(m.methodName().equalsString("getBurnDuration") ||
                        m.methodName().equalsString("canPlaceItem"))) {b.with(e);return;}
                boolean duration=m.methodName().equalsString("getBurnDuration");
                b.transformMethod(m,MethodTransform.transformingCode((c,ins)->{
                    if (ins instanceof InvokeInstruction call && call.owner().asSymbol().equals(fuels)
                            && call.name().equalsString(duration ? "burnDuration":"isFuel")) {
                        hits[duration?0:1]++;
                        // [fuels, stack] -> [stack, recipe, fuels]
                        c.swap().aload(0).getfield(owner,"recipeType",recipe).swap()
                            .invokevirtual(stack,"getBurnTime",MethodTypeDesc.of(CD_int,recipe,fuels));
                        if (!duration) {
                            var no=c.newLabel(); var done=c.newLabel();
                            c.ifle(no).iconst_1().goto_(done).labelBinding(no).iconst_0().labelBinding(done);
                        }
                    } else c.with(ins);
                }));
            };
            var bytes=cf.transformClass(model,calls.andThen(ClassTransform.endHandler(
                b->b.withField("recipeType",recipe,ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL))));
            if (hits[0]!=1 || hits[1]!=1 || hits[2]!=1) throw new IllegalStateException("Unexpected 26.2 furnace fuel calls");
            return bytes;
        });
    }
}
