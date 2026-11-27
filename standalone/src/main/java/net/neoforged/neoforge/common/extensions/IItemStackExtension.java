package net.neoforged.neoforge.common.extensions;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.neoforged.neoforge.event.EventHooks;

public interface IItemStackExtension{
    default int getBurnTime(RecipeType<?> recipe, FuelValues fuels) {
        ItemStack stack = (ItemStack)(Object)this;
        if (stack.isEmpty()) return 0;
        int duration = ((IItemExtension)stack.getItem()).getBurnTime(stack, recipe, fuels);
        if (duration < 0) throw new IllegalStateException("Negative burn time for " + stack.getItem());
        return EventHooks.getItemBurnTime(stack, duration, recipe, fuels);
    }
    default <T,C>T getCapability(ItemCapability<T,C> cap,C context){return cap.getCapability((ItemStack)(Object)this,context);}
    default <T>T getCapability(ItemCapability<T,Void> cap){return getCapability(cap,null);}
}
