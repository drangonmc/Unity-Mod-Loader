package net.minecraftforge.common.capabilities;
import net.minecraftforge.items.IItemHandler;
public final class ForgeCapabilities {
    public static final Capability<net.minecraftforge.energy.IEnergyStorage> ENERGY =
        new Capability<>("net/minecraftforge/energy/IEnergyStorage");
    public static final Capability<net.minecraftforge.fluids.capability.IFluidHandler> FLUID_HANDLER =
        new Capability<>("net/minecraftforge/fluids/capability/IFluidHandler");
    public static final Capability<IItemHandler> ITEM_HANDLER =
        new Capability<>("net/minecraftforge/items/IItemHandler");
}
