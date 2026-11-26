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

    public Object invoke(Object self, Method method, Object[] args) {
        var name = item.get(DataComponents.CUSTOM_NAME);
        var lore = item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        return switch (method.getName()) {
            case "clone" -> of(item);
            case "hasDisplayName", "hasCustomName" -> name != null;
            case "getDisplayName" -> name == null ? "" : name.getString();
            case "setDisplayName" -> {
                if (args[0] == null) item.remove(DataComponents.CUSTOM_NAME);
                else item.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal((String)args[0]));
                yield null;
            }
            case "hasLore" -> !lore.lines().isEmpty();
            case "getLore" -> lore.lines().isEmpty() ? null : lore.lines().stream().map(c -> c.getString()).toList();
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
