package net.fabricmc.fabric.api.registry;
import net.minecraft.world.level.block.state.BlockState;
public final class StrippableBlockRegistry {
    public static BlockState getStrippedBlockState(BlockState state) {
        return net.minecraft.world.item.UnityStripping.get(state);
    }
}
