package net.neoforged.fml.event.lifecycle;
import java.util.concurrent.Executor;
import net.neoforged.fml.ModContainer;
public final class FMLCommonSetupEvent extends ParallelDispatchEvent {
    public FMLCommonSetupEvent(ModContainer container, Executor queue) { super(container, queue); }
}
