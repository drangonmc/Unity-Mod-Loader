package net.neoforged.fml.event.lifecycle;
import java.util.concurrent.Executor;
import net.neoforged.fml.ModContainer;
public final class FMLLoadCompleteEvent extends ParallelDispatchEvent {
    public FMLLoadCompleteEvent(ModContainer container, Executor queue) { super(container, queue); }
}
