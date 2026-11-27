package net.neoforged.neoforge.event.furnace;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.neoforged.bus.api.*;

public class FurnaceFuelBurnTimeEvent extends Event implements ICancellableEvent {
    private final ItemStack stack;
    private final RecipeType<?> recipe;
    private final FuelValues fuels;
    private int duration;

    public FurnaceFuelBurnTimeEvent(ItemStack stack, int duration, RecipeType<?> recipe, FuelValues fuels) {
        this.stack = stack;
        this.duration = duration;
        this.recipe = recipe;
        this.fuels = fuels;
    }
    public ItemStack getItemStack() { return stack; }
    public int getBurnTime() { return duration; }
    public RecipeType<?> getRecipeType() { return recipe; }
    public FuelValues getFuelValues() { return fuels; }
    public void setBurnTime(int ticks) {
        if (ticks >= 0) {
            duration = ticks;
            setCanceled(true);
        }
    }
}
