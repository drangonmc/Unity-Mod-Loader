package net.minecraftforge.fluids;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.*;
public class FluidStack {
    public static final FluidStack EMPTY = new FluidStack(Fluids.EMPTY, 0);
    private final Fluid fluid;
    private int amount;
    private CompoundTag tag;
    public FluidStack(Fluid fluid, int amount) {
        this.fluid = Objects.requireNonNull(fluid);
        this.amount = amount;
    }
    public FluidStack(Fluid fluid, int amount, CompoundTag tag) {
        this(fluid, amount);
        this.tag = tag == null ? null : tag.copy();
    }
    public FluidStack(FluidStack stack, int amount) { this(stack.fluid, amount, stack.tag); }
    public final Fluid getRawFluid() { return fluid; }
    public final Fluid getFluid() { return isEmpty() ? Fluids.EMPTY : fluid; }
    public boolean isEmpty() { return fluid == Fluids.EMPTY || amount <= 0; }
    public int getAmount() { return isEmpty() ? 0 : amount; }
    public void setAmount(int n) {
        if (this == EMPTY)
            throw new IllegalStateException("Empty fluid");
        amount = n;
    }
    public void grow(int n) { setAmount(Math.addExact(amount, n)); }
    public void shrink(int n) { setAmount(amount - n); }
    public boolean hasTag() { return tag != null; }
    public CompoundTag getTag() { return tag; }
    public void setTag(CompoundTag value) {
        if (this == EMPTY)
            throw new IllegalStateException("Empty fluid");
        tag = value;
    }
    public CompoundTag getOrCreateTag() {
        if (tag == null)
            setTag(new CompoundTag());
        return tag;
    }
    public FluidStack copy() { return isEmpty() ? EMPTY : new FluidStack(this, amount); }
    public boolean isFluidEqual(FluidStack other) {
        return other != null && getFluid() == other.getFluid() && areFluidStackTagsEqual(this, other);
    }
    public static boolean areFluidStackTagsEqual(FluidStack a, FluidStack b) {
        return Objects.equals(a.tag, b.tag);
    }
    public boolean containsFluid(FluidStack other) {
        return isFluidEqual(other) && getAmount() >= other.getAmount();
    }
    public boolean isFluidStackIdentical(FluidStack other) {
        return isFluidEqual(other) && getAmount() == other.getAmount();
    }
    public final boolean equals(Object other) { return other instanceof FluidStack s && isFluidEqual(s); }
    public final int hashCode() { return Objects.hash(getFluid(), tag); }
}
