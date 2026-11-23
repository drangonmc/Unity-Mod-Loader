package qa.forgeconfig;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.items.IItemHandler;

public final class ForgeInventoryProbe {
    public static IItemHandler handler(BlockEntity entity) {
        var provider = (ICapabilityProvider)(Object)entity;
        if (provider.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).isPresent())
            throw new AssertionError("Fabric-only port unexpectedly exposed a different face");
        return provider.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow();
    }
    public static void checkInsert(IItemHandler handler) {
        var input = new ItemStack(Items.EMERALD, 7);
        if (!handler.insertItem(0, input, true).isEmpty() || !handler.getStackInSlot(0).isEmpty())
            throw new AssertionError("simulate insert");
        if (!handler.insertItem(0, input, false).isEmpty() || input.getCount() != 7)
            throw new AssertionError("commit insert");
    }
    public static boolean resize(BlockEntity e,Runnable shrink){
        var t=((ICapabilityProvider)(Object)e).getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.DOWN).resolve().orElseThrow();
        shrink.run();return t.getTanks()==1 && t.getTankCapacity(31)==0 && t.getFluidInTank(31).isEmpty();
    }
    public static void energyAndFluid(BlockEntity entity) {
        var port = (ICapabilityProvider)(Object)entity;
        var energy = port.getCapability(ForgeCapabilities.ENERGY, Direction.UP).resolve().orElseThrow();
        if (energy.receiveEnergy(38, true) != 38 || energy.getEnergyStored() != 0)
            throw new AssertionError("energy simulation");
        try (var outer = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            if (energy.receiveEnergy(38, false) != 38)
                throw new AssertionError("energy nested insertion");
        }
        if (energy.getEnergyStored() != 0 || energy.receiveEnergy(38, false) != 38 ||
            energy.extractEnergy(15, false) != 15 || energy.getEnergyStored() != 23)
            throw new AssertionError("energy rollback and commit");
        var down=port.getCapability(ForgeCapabilities.FLUID_HANDLER,Direction.DOWN).resolve().orElseThrow();
        var water=new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000);
        var lava=new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA,1000);
        if(down.getTanks()!=32 || down.isFluidValid(0,water) || !down.isFluidValid(0,lava) || down.isFluidValid(1,lava))throw new AssertionError("Tank filters");
        for(int i=0;i<down.getTanks();i++)if(down.getTankCapacity(i)!=1000 || !down.getFluidInTank(i).isEmpty() || i>0 && !down.isFluidValid(i,water))throw new AssertionError("Tank enumeration");
        var tank = port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).resolve().orElseThrow();
        var input =
            new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1001);
        var simulate = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        var execute = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        if (tank.fill(input, simulate) != 1000 || !tank.getFluidInTank(0).isEmpty())
            throw new AssertionError("fluid simulation");
        if (tank.fill(input, execute) != 1000 || tank.getFluidInTank(0).getAmount() != 1000)
            throw new AssertionError("fractional fluid units");
        if (tank.drain(250, simulate).getAmount() != 250 || tank.getFluidInTank(0).getAmount() != 1000 ||
            tank.drain(250, execute).getAmount() != 250 || tank.getFluidInTank(0).getAmount() != 750)
            throw new AssertionError("drain");
    }
    public static void take(IItemHandler handler, int n, boolean simulate) {
        var out = handler.extractItem(0, n, simulate);
        if (!out.is(Items.EMERALD) || out.getCount() != n)
            throw new AssertionError("extract");
    }
}
