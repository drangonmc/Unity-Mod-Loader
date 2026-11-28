package mc.unity.plugins;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;

/** Detached Bukkit metadata. Keep the native component patch, including mod components, across edits. */
public final class StackMeta implements InvocationHandler {
    final ItemStack item;
    private StackMeta(ItemStack item) { this.item = item.copy(); }

    static ItemMeta of(ItemStack stack) {
        var type=stack.has(DataComponents.POTION_CONTENTS) || stack.is(net.minecraft.world.item.Items.POTION) || stack.is(net.minecraft.world.item.Items.SPLASH_POTION) || stack.is(net.minecraft.world.item.Items.LINGERING_POTION) || stack.is(net.minecraft.world.item.Items.TIPPED_ARROW)?org.bukkit.inventory.meta.PotionMeta.class:
            stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)?org.bukkit.inventory.meta.EnchantmentStorageMeta.class:stack.is(net.minecraft.world.item.Items.PLAYER_HEAD)?org.bukkit.inventory.meta.SkullMeta.class:stack.isDamageableItem() ? Damageable.class : ItemMeta.class;
        return (ItemMeta) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new StackMeta(stack));
    }

    static StackMeta from(ItemMeta meta) {
        return meta != null && Proxy.isProxyClass(meta.getClass()) && Proxy.getInvocationHandler(meta) instanceof StackMeta m ? m : null;
    }

    public static net.minecraft.network.chat.Component fromLegacy(String text) {
        var rich = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text);
        var json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serializeToTree(rich);
        return net.minecraft.network.chat.ComponentSerialization.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
    }
    public static String legacy(net.minecraft.network.chat.Component value) {
        var json = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,value).getOrThrow();
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(
            net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserializeFromTree(json));
    }
    public Object invoke(Object self, Method method, Object[] args) {
        var name = item.get(DataComponents.CUSTOM_NAME);
        var lore = item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        String op=method.getName();boolean stored=op.contains("StoredEnchant");String key=op.replace("StoredEnchant","Enchant");
        var component=stored?DataComponents.STORED_ENCHANTMENTS:DataComponents.ENCHANTMENTS;
        var enchantments=item.getOrDefault(component,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        return switch (key) {
            case "hasOwner"->item.has(DataComponents.PROFILE);
            case "getOwner"->{var p=item.get(DataComponents.PROFILE);yield p==null?null:p.name().orElse(null);}
            case "setOwner"->{if(args[0]==null)item.remove(DataComponents.PROFILE);else item.set(DataComponents.PROFILE,net.minecraft.world.item.component.ResolvableProfile.createUnresolved((String)args[0]));yield true;}
            case "getOwningPlayer"->{var p=item.get(DataComponents.PROFILE);if(p==null)yield null;
                var profile=p.partialProfile();
                yield net.minecraft.util.Util.NIL_UUID.equals(profile.id())?(profile.name().isBlank()?null:org.bukkit.Bukkit.getOfflinePlayer(profile.name())):org.bukkit.Bukkit.getOfflinePlayer(profile.id());}
            case "setOwningPlayer"->{if(args[0]==null)item.remove(DataComponents.PROFILE);else item.set(DataComponents.PROFILE,net.minecraft.world.item.component.ResolvableProfile.createUnresolved(((org.bukkit.OfflinePlayer)args[0]).getUniqueId()));yield true;}
            case "getPlayerProfile","getOwnerProfile"->{var p=item.get(DataComponents.PROFILE);yield p==null?null:LoginProfiles.view(p.partialProfile());}
            case "setPlayerProfile","setOwnerProfile"->{if(args[0]==null)item.remove(DataComponents.PROFILE);else item.set(DataComponents.PROFILE,net.minecraft.world.item.component.ResolvableProfile.createResolved(LoginProfiles.nativeProfile((org.bukkit.profile.PlayerProfile)args[0])));yield null;}
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
            case "getLore" -> lore.lines().isEmpty() ? null : new ArrayList<>(lore.lines().stream().map(StackMeta::legacy).toList());
            case "setLore" -> {
                if(args[0]==null)item.remove(DataComponents.LORE);
                else item.set(DataComponents.LORE, new ItemLore(((java.util.List<?>)args[0]).stream().map(x->fromLegacy((String)x)).toList()));
                yield null;
            }
            case "lore" -> {
                if(args==null || args.length==0)yield lore.lines().isEmpty()?null:new ArrayList<>(lore.lines().stream().map(Host.active()::adventure).toList());
                if(args[0]==null)item.remove(DataComponents.LORE);
                else item.set(DataComponents.LORE,new ItemLore(((List<net.kyori.adventure.text.Component>)args[0]).stream().map(Host.active()::nativeReason).toList()));
                yield null;
            }
            case "getEnchants" -> {
                var enchants=new LinkedHashMap<org.bukkit.enchantments.Enchantment,Integer>();
                for(var e:enchantments.entrySet())enchants.put(org.bukkit.Registry.ENCHANTMENT.getOrThrow(
                    org.bukkit.NamespacedKey.fromString(e.getKey().unwrapKey().orElseThrow().identifier().toString())),e.getIntValue());
                yield Collections.unmodifiableMap(enchants);
            }
            case "hasEnchant","getEnchantLevel" -> {
                int level=args[0] instanceof BukkitEnchant e?enchantments.getLevel(e.holder):0;
                if(key.equals("hasEnchant"))yield level>0;yield level;
            }
            case "addEnchant","removeEnchant" -> {
                if(!(args[0] instanceof BukkitEnchant e))throw new IllegalArgumentException("Expected a registered enchantment");
                int level=args.length==1?0:(int)args[1];
                if(args.length>1 && level<=0)yield false;
                if(args.length>1 && !(boolean)args[2] && (level<e.getStartLevel() || level>e.getMaxLevel()))yield false;
                int old=enchantments.getLevel(e.holder);if(old==level)yield false;
                var copy=new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(enchantments);
                copy.set(e.holder,level);var updated=copy.toImmutable();
                if(updated.isEmpty())item.remove(component);else item.set(component,updated);yield true;
            }
            case "removeEnchantments" -> {item.remove(DataComponents.ENCHANTMENTS);yield null;}
            case "hasConflictingEnchant" -> {
                if(!(args[0] instanceof BukkitEnchant candidate))throw new IllegalArgumentException("Expected a registered enchantment");yield enchantments.keySet().stream().anyMatch(h->!h.equals(candidate.holder) && !net.minecraft.world.item.enchantment.Enchantment.areCompatible(h,candidate.holder));
            }
            case "hasEnchants" -> !enchantments.isEmpty();
            case "hasDamage", "hasDamageValue" -> item.getDamageValue() > 0;
            case "getDamage" -> item.getDamageValue();
            case "setDamage" -> { item.setDamageValue((int)args[0]); yield null; }
            case "getCustomModelDataComponent"->new ModelComponent(item.getOrDefault(DataComponents.CUSTOM_MODEL_DATA,net.minecraft.world.item.component.CustomModelData.EMPTY));
            case "setCustomModelDataComponent"->{if(args[0]==null)item.remove(DataComponents.CUSTOM_MODEL_DATA);else item.set(DataComponents.CUSTOM_MODEL_DATA,ModelComponent.nativeData((org.bukkit.inventory.meta.components.CustomModelDataComponent)args[0]));yield null;}
            case "getCustomModelData"->{var c=item.get(DataComponents.CUSTOM_MODEL_DATA);if(c==null || c.floats().isEmpty())throw new IllegalStateException("No custom model data");yield c.floats().getFirst().intValue();}
            case "setCustomModelData"->{if(args[0]==null)item.remove(DataComponents.CUSTOM_MODEL_DATA);else item.set(DataComponents.CUSTOM_MODEL_DATA,new net.minecraft.world.item.component.CustomModelData(List.of(((Integer)args[0]).floatValue()),List.of(),List.of(),List.of()));yield null;}
            case "hasCustomModelData" -> !item.getOrDefault(DataComponents.CUSTOM_MODEL_DATA,net.minecraft.world.item.component.CustomModelData.EMPTY).floats().isEmpty();
            case "hasCustomModelDataComponent" -> item.has(DataComponents.CUSTOM_MODEL_DATA);
            case "isUnbreakable" -> item.has(DataComponents.UNBREAKABLE);
            case "hasItemName" -> item.hasNonDefault(DataComponents.ITEM_NAME);
            case "equals" -> args[0] instanceof ItemMeta m && from(m) instanceof StackMeta other &&
                item.getComponentsPatch().equals(other.item.getComponentsPatch());
            case "hashCode" -> item.getComponentsPatch().hashCode();
            case "toString" -> item.getComponentsPatch().toString();
            default -> {if(self instanceof org.bukkit.inventory.meta.PotionMeta)yield PotionMetaView.call(item,op,args);throw Views.unsupported("ItemMeta",op);}
        };
    }
}
