package mc.unity.plugins;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;

/** Detached Bukkit metadata. Keep the native component patch, including mod components, across edits. */
final class StackMeta implements InvocationHandler {
    final ItemStack item;
    private StackMeta(ItemStack item) { this.item = item.copy(); }

    static ItemMeta of(ItemStack stack) {
        var type = stack.isDamageableItem() ? Damageable.class : ItemMeta.class;
        return (ItemMeta) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new StackMeta(stack));
    }

    static StackMeta from(ItemMeta meta) {
        return meta != null && Proxy.isProxyClass(meta.getClass()) && Proxy.getInvocationHandler(meta) instanceof StackMeta m ? m : null;
    }

    static net.minecraft.network.chat.Component fromLegacy(String text) {
        var rich = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text);
        var json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serializeToTree(rich);
        return net.minecraft.network.chat.ComponentSerialization.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
    }
    static String legacy(net.minecraft.network.chat.Component value) {
        var json = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,value).getOrThrow();
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(
            net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserializeFromTree(json));
    }
    public Object invoke(Object self, Method method, Object[] args) {
        var name = item.get(DataComponents.CUSTOM_NAME);
        var lore = item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        return switch (method.getName()) {
            case "getPersistentDataContainer" -> PersistentTags.item(item);
            case "clone" -> of(item);
            case "hasDisplayName", "hasCustomName" -> name != null;
            case "getDisplayName" -> name == null ? "" : legacy(name);
            case "setDisplayName" -> {
                if (args[0] == null) item.remove(DataComponents.CUSTOM_NAME);
                else item.set(DataComponents.CUSTOM_NAME, fromLegacy((String)args[0]));
                yield null;
            }
            case "hasLore" -> !lore.lines().isEmpty();
            case "getLore" -> lore.lines().isEmpty() ? null : lore.lines().stream().map(StackMeta::legacy).toList();
            case "setLore" -> {
                if(args[0]==null)item.remove(DataComponents.LORE);
                else item.set(DataComponents.LORE, new ItemLore(((java.util.List<?>)args[0]).stream().map(x->fromLegacy((String)x)).toList()));
                yield null;
            }
            case "lore" -> {
                if(args==null || args.length==0)yield lore.lines().isEmpty()?null:lore.lines().stream().map(Host.active()::adventure).toList();
                if(args[0]==null)item.remove(DataComponents.LORE);
                else item.set(DataComponents.LORE,new ItemLore(((List<net.kyori.adventure.text.Component>)args[0]).stream().map(Host.active()::nativeReason).toList()));
                yield null;
            }
            case "getEnchants" -> {
                var enchants=new LinkedHashMap<org.bukkit.enchantments.Enchantment,Integer>();
                for(var e:item.getEnchantments().entrySet())enchants.put(org.bukkit.Registry.ENCHANTMENT.getOrThrow(
                    org.bukkit.NamespacedKey.fromString(e.getKey().unwrapKey().orElseThrow().identifier().toString())),e.getIntValue());
                yield Collections.unmodifiableMap(enchants);
            }
            case "hasEnchant","getEnchantLevel" -> {
                int level=item.getEnchantments().getLevel(((BukkitEnchant)args[0]).holder);
                if(method.getName().equals("hasEnchant"))yield level>0;yield level;
            }
            case "addEnchant","removeEnchant" -> {
                var e=(BukkitEnchant)args[0];int level=args.length==1?0:(int)args[1];
                if(args.length>1 && !(boolean)args[2] && (level<e.getStartLevel() || level>e.getMaxLevel()))yield false;
                int old=item.getEnchantments().getLevel(e.holder);if(old==level)yield false;
                var copy=new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(item.getEnchantments());
                copy.set(e.holder,level);item.set(DataComponents.ENCHANTMENTS,copy.toImmutable());yield true;
            }
            case "removeEnchantments" -> {item.remove(DataComponents.ENCHANTMENTS);yield null;}
            case "hasConflictingEnchant" -> {
                var candidate=(BukkitEnchant)args[0];yield item.getEnchantments().keySet().stream().anyMatch(h->!h.equals(candidate.holder) && !net.minecraft.world.item.enchantment.Enchantment.areCompatible(h,candidate.holder));
            }
            case "hasEnchants" -> !item.getEnchantments().isEmpty();
            case "hasDamage", "hasDamageValue" -> item.getDamageValue() > 0;
            case "getDamage" -> item.getDamageValue();
            case "setDamage" -> { item.setDamageValue((int)args[0]); yield null; }
            case "hasCustomModelData" -> item.has(DataComponents.CUSTOM_MODEL_DATA);
            case "isUnbreakable" -> item.has(DataComponents.UNBREAKABLE);
            case "hasItemName" -> item.hasNonDefault(DataComponents.ITEM_NAME);
            case "equals" -> args[0] instanceof ItemMeta m && from(m) instanceof StackMeta other &&
                item.getComponentsPatch().equals(other.item.getComponentsPatch());
            case "hashCode" -> item.getComponentsPatch().hashCode();
            case "toString" -> item.getComponentsPatch().toString();
            default -> throw Views.unsupported("ItemMeta", method.getName());
        };
    }
}
