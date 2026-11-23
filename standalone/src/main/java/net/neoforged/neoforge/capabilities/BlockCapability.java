package net.neoforged.neoforge.capabilities;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
public final class BlockCapability<T, C> extends BaseCapability<T, C> {
    private static final Map<Identifier, BlockCapability<?, ?>> types = new LinkedHashMap<>();
    private final Map<Block, List<IBlockCapabilityProvider<T, C>>> providers = new IdentityHashMap<>();
    private BlockCapability(Identifier id, Class<T> type, Class<C> context) { super(id, type, context); }
    @SuppressWarnings("unchecked")
    public static synchronized<T, C> BlockCapability<T, C> create(Identifier id, Class<T> type,
                                                                  Class<C> context) {
        var cap = types.computeIfAbsent(id, k -> new BlockCapability<>(k, type, context));
        if (cap.typeClass() != type || cap.contextClass() != context)
            throw new IllegalArgumentException("Incompatible capability " + id);
        return (BlockCapability<T, C>)cap;
    }
    public static <T> BlockCapability<T, Direction> createSided(Identifier id, Class<T> t) {
        return create(id, t, Direction.class);
    }
    public static <T> BlockCapability<T, Void> createVoid(Identifier id, Class<T> t) {
        return create(id, t, Void.class);
    }
    public static synchronized List<BlockCapability<?, ?>> getAll() { return List.copyOf(types.values()); }
    void add(Block b, IBlockCapabilityProvider<T, C> provider) {
        providers.computeIfAbsent(b, k -> new ArrayList<>()).add(provider);
    }
    boolean has(Block b) { return providers.containsKey(b); }
    public T getCapability(Level level, BlockPos pos, BlockState state, BlockEntity be, C context) {
        if (state == null)
            state = level.getBlockState(pos);
        var handlers = providers.get(state.getBlock());
        if (handlers == null)
            return null;
        if (be == null && state.hasBlockEntity())
            be = level.getBlockEntity(pos);
        for (var fn : handlers) {
            T result = fn.getCapability(level, pos, state, be, context);
            if (result != null)
                return result;
        }
        return null;
    }
    public static void resetProviders() {
        for (var cap : getAll())
            cap.providers.clear();
    }
}
