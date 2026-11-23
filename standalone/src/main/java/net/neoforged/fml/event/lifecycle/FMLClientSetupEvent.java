package net.neoforged.fml.event.lifecycle;
import java.util.concurrent.Executor;
import net.neoforged.fml.ModContainer;
public final class FMLClientSetupEvent extends ParallelDispatchEvent {
    public FMLClientSetupEvent(ModContainer container, Executor queue) { super(container, queue); }
}
