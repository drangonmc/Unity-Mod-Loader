package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.*;
import net.minecraft.world.item.*;
import mc.unity.runtime.ServerHooks;

/** Preserve the original inventory on cancellation or on substitution with a different dispenser behavior. */
public final class UnityDispenser {
    private static final ThreadLocal<Boolean> substituted=new ThreadLocal<>();
    public static boolean proceed(DispenseItemBehavior original,BlockSource source,ItemStack stack) {
        var host=ServerHooks.pluginHost();
        if(host==null || substituted.get()!=null)return true;
        BlockPos target=source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        ItemStack selected=host.dispense(source,target,stack);
        if(selected==null || selected.isEmpty())return false;
        if(selected==stack)return true;
        var block=(DispenserBlock)source.state().getBlock();
        var replacement=block.getDispenseMethod(source.level(),selected);
        if(replacement!=original && replacement!=DispenseItemBehavior.NOOP) {
            substituted.set(true);
            try {replacement.dispense(source,selected);} finally {substituted.remove();}
        }
        return false;
    }
}
