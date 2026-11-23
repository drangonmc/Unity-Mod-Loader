package mc.unity.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class Extensions {
    public interface BlockBehavior {
        default float getExplosionResistance(BlockState state, BlockGetter level, BlockPos position,
                                             Explosion explosion) {
            return ((Block)this).getExplosionResistance();
        }
    }
    public interface ItemBehavior {
        default boolean isPiglinCurrency(ItemStack stack) { return stack.is(PiglinAi.BARTERING_ITEM); }
    }
    public static boolean currency(ItemStack stack) {
        return ((ItemBehavior)stack.getItem()).isPiglinCurrency(stack);
    }
}
