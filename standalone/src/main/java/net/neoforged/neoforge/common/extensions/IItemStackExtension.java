package net.neoforged.neoforge.common.extensions;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.minecraft.world.item.ItemStack;
public interface IItemStackExtension{
    default <T,C>T getCapability(ItemCapability<T,C> cap,C context){return cap.getCapability((ItemStack)(Object)this,context);}
    default <T>T getCapability(ItemCapability<T,Void> cap){return getCapability(cap,null);}
}
