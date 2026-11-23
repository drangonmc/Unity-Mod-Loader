package net.minecraft.world.item;
public abstract class UnityNeoTabAccess extends net.neoforged.bus.api.Event {
    protected abstract UnityTabContents entries();
    public void insertAfter(ItemStack existing, ItemStack value, CreativeModeTab.TabVisibility visibility) {
        entries().insertAfter(existing, value, visibility);
    }
    public void insertBefore(ItemStack existing, ItemStack value, CreativeModeTab.TabVisibility visibility) {
        entries().insertBefore(existing, value, visibility);
    }
}
