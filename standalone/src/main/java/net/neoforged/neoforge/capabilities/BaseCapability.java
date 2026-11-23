package net.neoforged.neoforge.capabilities;
import net.minecraft.resources.Identifier;
public abstract class BaseCapability<T, C> {
    private final Identifier name;
    private final Class<T> type;
    private final Class<C> context;
    protected BaseCapability(Identifier name, Class<T> type, Class<C> context) {
        this.name = name;
        this.type = type;
        this.context = context;
    }
    public final Identifier name() { return name; }
    public final Class<T> typeClass() { return type; }
    public final Class<C> contextClass() { return context; }
}
