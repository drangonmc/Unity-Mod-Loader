package mc.unity.core;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One sided, slot-addressable inventory. Amounts are item counts; callers retain ownership of input stacks.
 */
public final class SidedItems {
    public interface Slots {
        int size();
        ItemStack stack(int slot);
        int limit(int slot);
        boolean accepts(int slot, ItemStack stack);
        int insert(int slot, ItemStack stack, boolean simulate);
        ItemStack extract(int slot, int amount, boolean simulate);
    }
    public interface Provider {
        Slots find(BlockEntity entity, Direction face);
    }
    private static Provider provider;
    public static void bind(Provider next) { provider = next; }
    public static Slots find(BlockEntity entity, Direction face) {
        if (entity.isRemoved() || !(entity.getLevel() instanceof ServerLevel world) ||
            !world.getServer().isSameThread())
            return null;
        if (provider != null)
            return provider.find(entity, face);
        return entity instanceof Container inventory ? new Vanilla(inventory, face) : null;
    }
    private record Vanilla(Container inventory, Direction face) implements Slots {
        private WorldlyContainer sided() {
            return face != null && inventory instanceof WorldlyContainer s ? s : null;
        }
        private int index(int slot) { return sided() == null ? slot : sided().getSlotsForFace(face)[slot]; }
        public int size() {
            return sided() == null ? inventory.getContainerSize() : sided().getSlotsForFace(face).length;
        }
        public ItemStack stack(int slot) { return inventory.getItem(index(slot)).copy(); }
        public int limit(int slot) {
            index(slot);
            return inventory.getMaxStackSize();
        }
        public boolean accepts(int slot, ItemStack value) {
            int index = index(slot);
            return !value.isEmpty() && inventory.canPlaceItem(index, value) &&
                (sided() == null || sided().canPlaceItemThroughFace(index, value, face));
        }
        public int insert(int slot, ItemStack input, boolean simulate) {
            if (!accepts(slot, input))
                return 0;
            int index = index(slot);
            var old = inventory.getItem(index);
            if (!old.isEmpty() && !ItemStack.isSameItemSameComponents(old, input))
                return 0;
            int count =
                Math.min(input.getCount(), Math.max(0, inventory.getMaxStackSize(input) - old.getCount()));
            if (!simulate && count > 0) {
                inventory.setItem(index, input.copyWithCount(old.getCount() + count));
                inventory.setChanged();
            }
            return count;
        }
        public ItemStack extract(int slot, int amount, boolean simulate) {
            int index = index(slot);
            var old = inventory.getItem(index);
            if (amount <= 0 || old.isEmpty() ||
                sided() != null && !sided().canTakeItemThroughFace(index, old, face))
                return ItemStack.EMPTY;
            int count = Math.min(amount, Math.min(old.getCount(), old.getMaxStackSize()));
            if (simulate)
                return old.copyWithCount(count);
            var result = inventory.removeItem(index, count);
            inventory.setChanged();
            return result;
        }
    }
}
