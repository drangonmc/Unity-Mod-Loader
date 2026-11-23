package net.minecraft.world.item;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
/** Access to vanilla creative-tab output and its protected visibility enum. */
public class UnityTabAccess implements CreativeModeTab.Output {
    private final CreativeModeTab.Output output;
    protected UnityTabAccess(Object output) {
        this.output = (CreativeModeTab.Output) output;
    }
    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        output.accept(stack, visibility);
    }
    public void accept(Supplier<? extends ItemLike> item) {
        accept(item.get());
    }
    public void accept(Supplier<? extends ItemLike> item, CreativeModeTab.TabVisibility visibility) {
        accept(item.get(), visibility);
    }
}
