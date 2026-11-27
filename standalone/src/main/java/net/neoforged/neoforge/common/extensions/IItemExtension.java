package net.neoforged.neoforge.common.extensions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;

public interface IItemExtension extends mc.unity.core.Extensions.ItemBehavior {
    default int getBurnTime(ItemStack stack, RecipeType<?> recipe, FuelValues fuels) {
        return fuels.burnDuration(stack);
    }
}
