package net.minecraft.world.item;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
public final class UnityStripping {
    public static BlockState get(BlockState state) {
        var replacement = AxeItem.STRIPPABLES.get(state.getBlock());
        if (replacement == null)
            return null;
        return replacement.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                                                        state.getValue(RotatedPillarBlock.AXIS));
    }
}
