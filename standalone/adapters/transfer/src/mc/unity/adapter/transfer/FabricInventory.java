package mc.unity.adapter.transfer;

import mc.unity.core.SidedItems;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Preserve the Fabric provider's sided lookup, component variants and ambient transaction. */
public record FabricInventory(SlottedStorage<ItemVariant> storage) implements SidedItems.Slots {
    public static void install() {
        SidedItems.bind(FabricInventory::find);
        mc.unity.core.SidedFluids.bind((mc.unity.core.SidedFluids.ReusableProvider)FabricFluids::find);
    }
    private static SidedItems.Slots find(BlockEntity be, Direction side) {
        var storage = ItemStorage.SIDED.find(be.getLevel(), be.getBlockPos(), be.getBlockState(), be, side);
        return storage instanceof SlottedStorage<ItemVariant> slots ? new FabricInventory(slots) : null;
    }
    public int size() { return storage.getSlotCount(); }
    public ItemStack stack(int i) {
        var slot = storage.getSlot(i);
        var item = slot.getResource();
        return item.isBlank() ? ItemStack.EMPTY
                              : item.toStack(Math.toIntExact(Math.min(Integer.MAX_VALUE, slot.getAmount())));
    }
    public int limit(int i) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, storage.getSlot(i).getCapacity()));
    }
    public boolean accepts(int i, ItemStack item) { return !item.isEmpty() && insert(i, item, true) > 0; }
    public int insert(int i, ItemStack item, boolean simulate) {
        if (item.isEmpty())
            return 0;
        try (var tx = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            int n = Math.toIntExact(storage.getSlot(i).insert(ItemVariant.of(item), item.getCount(), tx));
            if (n < 0 || n > item.getCount())
                throw new IllegalStateException("Invalid Fabric insertion count " + n);
            if (!simulate)
                tx.commit();
            return n;
        }
    }
    public ItemStack extract(int i, int amount, boolean simulate) {
        if (amount <= 0)
            return ItemStack.EMPTY;
        var slot = storage.getSlot(i);
        var item = slot.getResource();
        if (item.isBlank())
            return ItemStack.EMPTY;
        try (var tx = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            int max = Math.min(amount, item.toStack().getMaxStackSize());
            int n = Math.toIntExact(slot.extract(item, max, tx));
            if (n < 0 || n > max)
                throw new IllegalStateException("Invalid Fabric extraction count " + n);
            if (!simulate)
                tx.commit();
            return n == 0 ? ItemStack.EMPTY : item.toStack(n);
        }
    }
}
