package net.neoforged.neoforge.transfer.transaction;
import java.util.IdentityHashMap;
public abstract class SnapshotJournal<T> {
    private final IdentityHashMap<Transaction, T> snapshots = new IdentityHashMap<>();
    protected abstract T createSnapshot();
    protected abstract void revertToSnapshot(T snapshot);
    protected void releaseSnapshot(T snapshot) {}
    protected void onRootCommit(T original) {}
    public final boolean isInTransaction() { return !snapshots.isEmpty(); }
    public void updateSnapshots(TransactionContext context) {
        if (!(context instanceof Transaction tx))
            throw new IllegalArgumentException("Expected an open transaction");
        tx.requireOpen();
        if (!snapshots.containsKey(tx)) {
            snapshots.put(tx, createSnapshot());
            tx.track(this);
        }
    }
    void finish(Transaction tx, Transaction parent, boolean commit) {
        var state = snapshots.remove(tx);
        if (commit && parent != null && !snapshots.containsKey(parent)) {
            snapshots.put(parent, state);
            parent.track(this);
            return;
        }
        try {
            if (!commit)
                revertToSnapshot(state);
            else if (parent == null)
                onRootCommit(state);
        } finally {
            releaseSnapshot(state);
        }
    }
}
