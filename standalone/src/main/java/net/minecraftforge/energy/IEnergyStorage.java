package net.minecraftforge.energy;
public interface IEnergyStorage {
    int receiveEnergy(int max, boolean simulate);
    int extractEnergy(int max, boolean simulate);
    int getEnergyStored();
    int getMaxEnergyStored();
    boolean canExtract();
    boolean canReceive();
}
