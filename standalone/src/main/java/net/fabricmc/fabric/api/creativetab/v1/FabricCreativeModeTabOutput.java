package net.fabricmc.fabric.api.creativetab.v1;
import net.minecraft.world.item.*;
import java.util.List;
public class FabricCreativeModeTabOutput extends UnityTabContents {
    public FabricCreativeModeTabOutput(UnityTabContents shared) {
        super(shared);
    }
    public FabricCreativeModeTabOutput(
        CreativeModeTab.ItemDisplayParameters ctx, List<ItemStack> display, List<ItemStack> search) {
        super(ctx, display, search);
    }
}
