package net.minecraftforge.common.capabilities;
import net.minecraftforge.common.util.LazyOptional;
public final class Capability<T> {
    private final String name;
    Capability(String name) { this.name = name; }
    public String getName() { return name; }
    public boolean isRegistered() { return true; }
    public <R> LazyOptional<R> orEmpty(Capability<R> requested, LazyOptional<T> supplied) {
        return this == requested ? supplied.cast() : LazyOptional.empty();
    }
}
