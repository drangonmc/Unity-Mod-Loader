package net.minecraftforge.common.capabilities;
import net.minecraft.core.Direction;
import net.minecraftforge.common.util.LazyOptional;
public interface ICapabilityProvider {
    <T> LazyOptional<T> getCapability(Capability<T> type, Direction side);
    default<T> LazyOptional<T> getCapability(Capability<T> type) { return getCapability(type, null); }
}
