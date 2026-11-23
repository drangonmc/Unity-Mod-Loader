package net.neoforged.fml;
import java.util.Objects;
public final class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();
    private static final ThreadLocal<ModContainer> ACTIVE = new ThreadLocal<>();
    public static ModLoadingContext get() { return INSTANCE; }
    public ModContainer getActiveContainer() {
        return Objects.requireNonNull(ACTIVE.get(), "No active mod callback");
    }
    public static ModContainer enter(ModContainer current) {
        var previous = ACTIVE.get();
        if (current == null)
            ACTIVE.remove();
        else
            ACTIVE.set(current);
        return previous;
    }
}
