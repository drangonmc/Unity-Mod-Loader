package mc.unity.adapter.neoforge;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class TransferSlots {
    public static void write(Container container,NonNullList<ItemStack> slots,int slot,ItemStack value,boolean transaction){
        if(!transaction){container.setItem(slot,value);return;}
        slots.set(slot,value);
        value.limitSize(container.getMaxStackSize(value));
    }
}
