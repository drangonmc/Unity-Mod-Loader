package net.neoforged.neoforge.transfer.energy;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
public interface EnergyHandler {
    long getAmountAsLong();
    long getCapacityAsLong();
    default int getAmountAsInt() { return (int)Math.min(Integer.MAX_VALUE, getAmountAsLong()); }
    default int getCapacityAsInt() { return (int)Math.min(Integer.MAX_VALUE, getCapacityAsLong()); }
    int insert(int amount, TransactionContext context);
    int extract(int amount, TransactionContext context);
}
