package mc.unity.core;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.entity.BlockEntity;
public final class SidedFluids {
    public record Variant(Fluid fluid, CompoundTag tag) {}
    public interface Tanks {
        int size();
        Variant variant(int tank);
        int amount(int tank);
        int capacity(int tank);
        boolean accepts(int tank, Variant resource);
        int move(Variant resource, int millibuckets, boolean insert, boolean simulate);
    }
    public interface Provider {
        Tanks find(BlockEntity entity, Direction side);
        default Tanks find(BlockEntity entity, Direction side, Tanks previous) { return find(entity, side); }
    }
    @FunctionalInterface
    public interface ReusableProvider extends Provider {
        Tanks find(BlockEntity entity, Direction side, Tanks previous);
        default Tanks find(BlockEntity entity, Direction side) { return find(entity, side, null); }
    }
    private static Provider provider;
    public static void bind(Provider p) { provider = p; }
    public static Tanks find(BlockEntity e, Direction side) { return find(e, side, null); }
    private static Tanks find(BlockEntity e, Direction side, Tanks previous) {
        if (e.isRemoved() || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel world) ||
            !world.getServer().isSameThread())
            return null;
        return provider == null ? null : provider.find(e, side, previous);
    }
    public static java.util.function.Supplier<Tanks> lookup(BlockEntity e, Direction side) {
        return new java.util.function.Supplier<>() {
            private Tanks previous;
            public Tanks get() { return previous = find(e, side, previous); }
        };
    }
}
