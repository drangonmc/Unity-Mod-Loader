package mc.unity.plugins;
import java.util.*;
import java.util.stream.*;
import net.minecraft.core.*;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemType;
import io.papermc.paper.registry.*;
import io.papermc.paper.registry.set.*;

final class BukkitEnchant extends org.bukkit.enchantments.Enchantment {
    final Holder.Reference<Enchantment> holder;
    BukkitEnchant(Holder.Reference<Enchantment> holder){this.holder=holder;}
    public NamespacedKey getKey(){return NamespacedKey.fromString(holder.key().identifier().toString());}
    public String getName(){var k=getKey();return k.getNamespace().equals("minecraft")?k.getKey().toUpperCase(Locale.ROOT):k.toString();}
    public int getMaxLevel(){return holder.value().getMaxLevel();}
    public int getStartLevel(){return holder.value().getMinLevel();}
    public boolean isTreasure(){return holder.is(EnchantmentTags.TREASURE);}
    public boolean isCursed(){return holder.is(EnchantmentTags.CURSE);}
    public boolean isTradeable(){return holder.is(EnchantmentTags.TRADEABLE);}
    public boolean isDiscoverable(){return holder.tags().anyMatch(k->Set.of("in_enchanting_table","on_random_loot","on_mob_spawn_equipment","on_traded_equipment","tradeable").contains(k.location().getPath()));}
    public boolean conflictsWith(org.bukkit.enchantments.Enchantment other){return other instanceof BukkitEnchant e && !Enchantment.areCompatible(holder,e.holder);}
    public boolean canEnchantItem(org.bukkit.inventory.ItemStack b){return holder.value().isSupportedItem(BlockViews.Stack.nativeCopy(b));}
    public net.kyori.adventure.text.Component displayName(int n){return Host.active().adventure(Enchantment.getFullname(holder,n));}
    public net.kyori.adventure.text.Component description(){return Host.active().adventure(holder.value().description());}
    public String getTranslationKey(){return translationKey();}
    public String translationKey(){return holder.value().description().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t?t.getKey():"enchantment."+getKey().getNamespace()+"."+getKey().getKey();}
    public int getMinModifiedCost(int n){return holder.value().getMinCost(n);}
    public int getMaxModifiedCost(int n){return holder.value().getMaxCost(n);}
    public int getAnvilCost(){return holder.value().definition().anvilCost();}
    public int getWeight(){return holder.value().getWeight();}
    public Set<org.bukkit.inventory.EquipmentSlotGroup> getActiveSlotGroups(){
        return holder.value().definition().slots().stream().map(s->org.bukkit.inventory.EquipmentSlotGroup.getByName(s.getSerializedName())).collect(Collectors.toUnmodifiableSet());
    }
    private static <T extends org.bukkit.Keyed> RegistryKeySet<T> keys(RegistryKey<T> reg,HolderSet<?> holders){
        return RegistrySet.keySet(reg,holders.stream().map(h->TypedKey.create(reg,h.unwrapKey().orElseThrow().identifier().toString())).toList());
    }
    public RegistryKeySet<ItemType> getSupportedItems(){return keys(RegistryKey.ITEM,holder.value().definition().supportedItems());}
    public RegistryKeySet<ItemType> getPrimaryItems(){return keys(RegistryKey.ITEM,holder.value().definition().primaryItems().orElse(holder.value().definition().supportedItems()));}
    public RegistryKeySet<org.bukkit.enchantments.Enchantment> getExclusiveWith(){return keys(RegistryKey.ENCHANTMENT,holder.value().exclusiveSet());}
    public org.bukkit.enchantments.EnchantmentTarget getItemTarget(){throw new UnsupportedOperationException("Use enchantment item tags");}
    public io.papermc.paper.enchantments.EnchantmentRarity getRarity(){throw new UnsupportedOperationException("Rarity was removed in 1.20.5");}
    public float getDamageIncrease(int level,org.bukkit.entity.EntityCategory target){throw new UnsupportedOperationException("Damage depends on effect components");}
    public float getDamageIncrease(int level,org.bukkit.entity.EntityType target){throw new UnsupportedOperationException("Damage depends on effect components");}
}
