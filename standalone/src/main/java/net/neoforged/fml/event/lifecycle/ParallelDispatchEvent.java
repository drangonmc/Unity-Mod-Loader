package net.neoforged.fml.event.lifecycle;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.IModBusEvent;
public abstract class ParallelDispatchEvent extends Event implements IModBusEvent {
    private final ModContainer container;
    private final Executor queue;
    protected ParallelDispatchEvent(ModContainer container, Executor queue) {
        this.container = container;
        this.queue = queue;
    }
    public ModContainer getContainer() { return container; }
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        return CompletableFuture.runAsync(work, queue);
    }
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, queue);
    }
}
