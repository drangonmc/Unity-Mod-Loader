package net.neoforged.neoforge.transfer.energy;
import net.neoforged.neoforge.transfer.transaction.*;
import net.minecraft.world.level.storage.*;
public class SimpleEnergyHandler
    implements EnergyHandler, net.neoforged.neoforge.common.util.ValueIOSerializable {
    protected int energy, capacity, maxInsert, maxExtract;
    private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {
        protected Integer createSnapshot() { return energy; }
        protected void revertToSnapshot(Integer old) { energy = old; }
        protected void onRootCommit(Integer old) { onEnergyChanged(old); }
    };
    public SimpleEnergyHandler(int capacity) { this(capacity, capacity); }
    public SimpleEnergyHandler(int capacity, int limit) { this(capacity, limit, limit); }
    public SimpleEnergyHandler(int capacity, int insert, int extract) { this(capacity, insert, extract, 0); }
    public SimpleEnergyHandler(int capacity, int insert, int extract, int energy) {
        if (capacity < 0 || insert < 0 || extract < 0 || energy < 0)
            throw new IllegalArgumentException("Negative energy");
        this.capacity = capacity;
        maxInsert = insert;
        maxExtract = extract;
        this.energy = Math.min(energy, capacity);
    }
    public void serialize(ValueOutput out) { out.putInt("energy", energy); }
    public void deserialize(ValueInput in) { energy = Math.max(0, in.getIntOr("energy", 0)); }
    public void set(int n) {
        if (n < 0 || journal.isInTransaction())
            throw new IllegalStateException("Invalid external energy write");
        int old = energy;
        energy = n;
        if (old != n)
            onEnergyChanged(old);
    }
    protected void onEnergyChanged(int old) {}
    public long getAmountAsLong() { return energy; }
    public long getCapacityAsLong() { return capacity; }
    public int insert(int n, TransactionContext tx) {
        if (n < 0)
            throw new IllegalArgumentException("Negative insertion");
        int used = Math.min(n, Math.min(maxInsert, Math.max(0, capacity - energy)));
        if (used > 0) {
            journal.updateSnapshots(tx);
            energy += used;
        }
        return used;
    }
    public int extract(int n, TransactionContext tx) {
        if (n < 0)
            throw new IllegalArgumentException("Negative extraction");
        int used = Math.min(n, Math.min(maxExtract, energy));
        if (used > 0) {
            journal.updateSnapshots(tx);
            energy -= used;
        }
        return used;
    }
}
