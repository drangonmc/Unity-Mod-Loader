package net.neoforged.neoforge.event;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

public final class EventHooks {
    private EventHooks() {}
    public static int getItemBurnTime(ItemStack stack, int original, RecipeType<?> recipe, FuelValues fuels) {
        return NeoForge.EVENT_BUS.post(new FurnaceFuelBurnTimeEvent(stack, original, recipe, fuels)).getBurnTime();
    }
}
