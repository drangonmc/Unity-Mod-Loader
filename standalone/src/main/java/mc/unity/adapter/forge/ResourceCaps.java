package mc.unity.adapter.forge;

import java.util.function.*;
import mc.unity.core.SidedFluids;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

final class ResourceCaps {
    static EnergyHandler energy(BlockEntity e, Direction side) {
        return Capabilities.Energy.BLOCK.getCapability(e.getLevel(), e.getBlockPos(), e.getBlockState(), e,
                                                       side);
    }
    record Energy(Supplier<EnergyHandler> provider) implements IEnergyStorage {
        private int move(int n, boolean simulate, boolean insert) {
            var handler = provider.get();
            if (handler == null || n <= 0)
                return 0;
            try (var tx = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
                int done = insert ? handler.insert(n, tx) : handler.extract(n, tx);
                if (done < 0 || done > n)
                    throw new IllegalStateException("Invalid energy transfer count");
                if (!simulate)
                    tx.commit();
                return done;
            }
        }
        public int receiveEnergy(int n, boolean simulate) { return move(n, simulate, true); }
        public int extractEnergy(int n, boolean simulate) { return move(n, simulate, false); }
        public int getEnergyStored() {
            var h = provider.get();
            return h == null ? 0 : h.getAmountAsInt();
        }
        public int getMaxEnergyStored() {
            var h = provider.get();
            return h == null ? 0 : h.getCapacityAsInt();
        }
        public boolean canExtract() { return extractEnergy(1, true) > 0; }
        public boolean canReceive() { return receiveEnergy(1, true) > 0; }
    }
    record Fluids(Supplier<SidedFluids.Tanks> provider) implements IFluidHandler {
        private static SidedFluids.Variant variant(FluidStack s) {
            return new SidedFluids.Variant(s.getFluid(), s.getTag());
        }
        private static FluidStack stack(SidedFluids.Variant v, int count) {
            return v == null || count == 0 ? FluidStack.EMPTY : new FluidStack(v.fluid(), count, v.tag());
        }
        public int getTanks() {
            var s = provider.get();
            return s == null ? 0 : s.size();
        }
        public FluidStack getFluidInTank(int i) {
            var s = provider.get();
            return s == null ? FluidStack.EMPTY : stack(s.variant(i), s.amount(i));
        }
        public int getTankCapacity(int i) {
            var s = provider.get();
            return s == null ? 0 : s.capacity(i);
        }
        public boolean isFluidValid(int i, FluidStack fluid) {
            var s = provider.get();
            return s != null && !fluid.isEmpty() && s.accepts(i, variant(fluid));
        }
        public int fill(FluidStack fluid, FluidAction action) {
            var s = provider.get();
            return s == null || fluid.isEmpty()
                ? 0
                : s.move(variant(fluid), fluid.getAmount(), true, action.simulate());
        }
        public FluidStack drain(FluidStack fluid, FluidAction action) {
            var s = provider.get();
            if (s == null || fluid.isEmpty())
                return FluidStack.EMPTY;
            var v = variant(fluid);
            return stack(v, s.move(v, fluid.getAmount(), false, action.simulate()));
        }
        public FluidStack drain(int max, FluidAction action) {
            var s = provider.get();
            if (s == null || max <= 0)
                return FluidStack.EMPTY;
            for (int i = 0; i < s.size(); i++) {
                var v = s.variant(i);
                if (v != null) {
                    var result = stack(v, s.move(v, max, false, action.simulate()));
                    if (!result.isEmpty())
                        return result;
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
