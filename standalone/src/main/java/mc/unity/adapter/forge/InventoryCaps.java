package mc.unity.adapter.forge;

import java.util.*;
import mc.unity.core.SidedItems;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

public final class InventoryCaps {
    public interface Owner extends ICapabilityProvider {
        InventoryCaps unity$items();
        default<T> LazyOptional<T> getCapability(Capability<T> type, Direction side) {
            if (!valid((BlockEntity)this))
                return LazyOptional.empty();
            return unity$items().get((BlockEntity)this, side, type);
        }
    }
    private boolean live = true;
    private record Side(Direction face, Capability<?> capability) {}
    private final Map<Side, LazyOptional<?>> handles = new HashMap<>();
    private <T> LazyOptional<T> get(BlockEntity be, Direction face, Capability<T> capability) {
        var key = new Side(face, capability);
        var previous = handles.get(key);
        if (previous != null && previous.isPresent())
            return previous.cast();
        LazyOptional<?> result;
        if (capability == ForgeCapabilities.ITEM_HANDLER && SidedItems.find(be, face) != null)
            result = LazyOptional.of(() -> new Handler(be, face, this));
        else if (capability == ForgeCapabilities.ENERGY && ResourceCaps.energy(be, face) != null)
            result = LazyOptional.of(()
                                         -> new ResourceCaps.Energy(
                                             () -> live && valid(be) ? ResourceCaps.energy(be, face) : null));
        else if (capability == ForgeCapabilities.FLUID_HANDLER) {
            var lookup = mc.unity.core.SidedFluids.lookup(be, face);
            if (lookup.get() == null)
                return LazyOptional.empty();
            result =
                LazyOptional.of(() -> new ResourceCaps.Fluids(() -> live && valid(be) ? lookup.get() : null));
        } else
            return LazyOptional.empty();
        handles.put(key, result);
        return result.cast();
    }
    private static boolean valid(BlockEntity be) {
        return !be.isRemoved() && be.getLevel() instanceof ServerLevel level &&
            level.getServer().isSameThread();
    }
    public void invalidate() {
        live = false;
        var old = List.copyOf(handles.values());
        handles.clear();
        for (var cap : old)
            cap.invalidate();
    }
    private record Handler(BlockEntity be, Direction face, InventoryCaps owner) implements IItemHandler {
        private SidedItems.Slots slots() { return owner.live ? SidedItems.find(be, face) : null; }
        public int getSlots() {
            var s = slots();
            return s == null ? 0 : s.size();
        }
        public int getSlotLimit(int i) {
            var s = slots();
            return s == null ? 0 : s.limit(i);
        }
        public boolean isItemValid(int i, ItemStack input) {
            var s = slots();
            return s != null && s.accepts(i, input);
        }
        public ItemStack getStackInSlot(int i) {
            var s = slots();
            return s == null ? ItemStack.EMPTY : s.stack(i);
        }
        public ItemStack insertItem(int i, ItemStack input, boolean simulate) {
            var s = slots();
            if (s == null || input.isEmpty())
                return input;
            int used = s.insert(i, input, simulate);
            return used == 0 ? input : input.copyWithCount(input.getCount() - used);
        }
        public ItemStack extractItem(int i, int amount, boolean simulate) {
            var s = slots();
            return s == null ? ItemStack.EMPTY : s.extract(i, amount, simulate);
        }
    }
}
