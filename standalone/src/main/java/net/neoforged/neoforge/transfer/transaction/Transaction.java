package net.neoforged.neoforge.transfer.transaction;

import java.util.*;

public final class Transaction implements TransactionContext, AutoCloseable {
    public enum Lifecycle { NONE, OPEN, CLOSING, ROOT_CLOSING }
    private static final ThreadLocal<Transaction> current = new ThreadLocal<>();
    private final Transaction parent;
    private final int depth;
    private final Thread thread = Thread.currentThread();
    private final List<SnapshotJournal<?>> journals = new ArrayList<>();
    private boolean closed, closing;
    private Transaction(Transaction parent) {
        this.parent = parent;
        depth = parent == null ? 0 : parent.depth + 1;
        current.set(this);
    }
    public static Transaction openRoot() { return open(null); }
    public static Transaction open(TransactionContext parent) {
        if (parent != current.get() || parent != null && getLifecycle() != Lifecycle.OPEN)
            throw new IllegalStateException("Transaction nesting does not match current thread");
        return new Transaction((Transaction)parent);
    }
    public static TransactionContext getCurrentOpenedTransaction() {
        return getLifecycle() == Lifecycle.OPEN ? current.get() : null;
    }
    public static Lifecycle getLifecycle() {
        var t = current.get();
        return t == null   ? Lifecycle.NONE
        : !t.closing       ? Lifecycle.OPEN
        : t.parent == null ? Lifecycle.ROOT_CLOSING
                           : Lifecycle.CLOSING;
    }
    public int depth() { return depth; }
    void requireOpen() {
        if (thread != Thread.currentThread() || current.get() != this || closed || closing)
            throw new IllegalStateException("Transaction is closed or not innermost");
    }
    void track(SnapshotJournal<?> journal) { journals.add(journal); }
    public void commit() { finish(true); }
    public void close() {
        if (!closed)
            finish(false);
    }
    private void finish(boolean commit) {
        requireOpen();
        closing = true;
        Throwable failure = null;
        try {
            for (int i = journals.size() - 1; i >= 0; i--)
                try {
                    journals.get(i).finish(this, parent, commit);
                } catch (RuntimeException | Error ex) {
                    if (failure == null)
                        failure = ex;
                    else
                        failure.addSuppressed(ex);
                }
        } finally {
            closed = true;
            if (parent == null)
                current.remove();
            else
                current.set(parent);
            journals.clear();
        }
        if (failure instanceof Error e)
            throw e;
        if (failure instanceof RuntimeException e)
            throw e;
    }
}
