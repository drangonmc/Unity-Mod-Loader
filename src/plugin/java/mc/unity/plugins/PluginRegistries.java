package mc.unity.plugins;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.registry.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.MenuType;
import org.bukkit.damage.DamageType;

/** The SDK initializes its static registry handles together, but resolves their contents lazily. */
public final class PluginRegistries implements RegistryAccess {
    private final Map<Object,Registry<?>> registries = new ConcurrentHashMap<>();
    private final Map<NamespacedKey,BukkitEnchant> enchantments=new ConcurrentHashMap<>();
    private final Map<NamespacedKey,org.bukkit.GameRule<?>> rules=new ConcurrentHashMap<>();
    private final Map<NamespacedKey,MenuType> menus = new ConcurrentHashMap<>();
    private final Map<NamespacedKey,DamageType> damage = new ConcurrentHashMap<>();
    private final Map<NamespacedKey,Keyed> blocks=new ConcurrentHashMap<>(), items=new ConcurrentHashMap<>();
    private final Map<NamespacedKey,Keyed> fluids=new ConcurrentHashMap<>(), gameEvents=new ConcurrentHashMap<>();
    private final Map<NamespacedKey,Keyed> effects=new ConcurrentHashMap<>();
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        if (type==org.bukkit.potion.PotionEffectType.class)return registry(RegistryKey.MOB_EFFECT);
        if(type==org.bukkit.enchantments.Enchantment.class)return registry(RegistryKey.ENCHANTMENT);
        if(type==org.bukkit.GameRule.class)return registry(RegistryKey.GAME_RULE);
        if (type==org.bukkit.Fluid.class)return registry(RegistryKey.FLUID);
        if (type==org.bukkit.GameEvent.class)return registry(RegistryKey.GAME_EVENT);
        if (type==org.bukkit.entity.EntityType.class) return registry(RegistryKey.ENTITY_TYPE);
        if (type==org.bukkit.block.BlockType.class) return registry(RegistryKey.BLOCK);
        if (type==org.bukkit.inventory.ItemType.class) return registry(RegistryKey.ITEM);
        if (type == MenuType.class) return registry(RegistryKey.MENU);
        if (type == DamageType.class) return registry(RegistryKey.DAMAGE_TYPE);
        return registry(type);
    }
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) { return registry(key); }
    @SuppressWarnings("unchecked")
    private <T extends Keyed> Registry<T> registry(Object kind) {
        return (Registry<T>)registries.computeIfAbsent(kind, key -> Views.of(Registry.class,key.toString(),(m,a)->{
            net.minecraft.core.Registry<?> source;
            if(key==RegistryKey.ENCHANTMENT)source=Host.active().game.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            else if(key==RegistryKey.GAME_RULE)source=BuiltInRegistries.GAME_RULE;
            else if(key==RegistryKey.MENU)source=BuiltInRegistries.MENU;
            else if(key==RegistryKey.MOB_EFFECT)source=BuiltInRegistries.MOB_EFFECT;
            else if(key==RegistryKey.FLUID)source=BuiltInRegistries.FLUID;
            else if(key==RegistryKey.GAME_EVENT)source=BuiltInRegistries.GAME_EVENT;
            else if(key==RegistryKey.ENTITY_TYPE)source=BuiltInRegistries.ENTITY_TYPE;
            else if(key==RegistryKey.BLOCK)source=BuiltInRegistries.BLOCK;
            else if(key==RegistryKey.ITEM)source=BuiltInRegistries.ITEM;
            else if(key==RegistryKey.DAMAGE_TYPE)source=Host.active().game.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
            else throw Views.unsupported("Registry "+key,m);
            return switch(m) {
                case "get" -> {
                    if (!(a[0] instanceof net.kyori.adventure.key.Key id)) throw Views.unsupported("Registry",m);
                    var name = new NamespacedKey(id.namespace(),id.value());
                    yield source.containsKey(Identifier.parse(name.toString())) ? value(key,name) : null;
                }
                case "getKey" -> ((Keyed)a[0]).getKey();
                case "size" -> key==RegistryKey.ENTITY_TYPE ? (int)source.keySet().stream().filter(id->entityType(id.toString())!=null).count() : source.size();
                case "keyStream" -> source.keySet().stream().filter(id->key!=RegistryKey.ENTITY_TYPE || entityType(id.toString())!=null).map(id->NamespacedKey.fromString(id.toString()));
                case "stream","iterator" -> {
                    var entries=source.keySet().stream().map(id->value(key,NamespacedKey.fromString(id.toString()))).filter(Objects::nonNull);
                    yield m.equals("stream") ? entries : entries.iterator();
                }
                default -> throw Views.unsupported("Registry",m);
            };
        }));
    }
    private static final class Entities {
        static final Map<String,org.bukkit.entity.EntityType> byName=new HashMap<>();
        static {
            for(var type:org.bukkit.entity.EntityType.values())if(type!=org.bukkit.entity.EntityType.UNKNOWN)byName.put(type.getKey().toString(),type);
        }
    }
    private static org.bukkit.entity.EntityType entityType(String name) { return Entities.byName.get(name); }
    private Keyed value(Object key, NamespacedKey name) {
        if(key==RegistryKey.MOB_EFFECT){
            var found=effects.get(name);if(found!=null)return found;
            var value=new EffectView(name,BuiltInRegistries.MOB_EFFECT.getOptional(Identifier.parse(name.toString())).orElseThrow());
            var old=effects.putIfAbsent(name,value);return old==null?value:old;
        }
        if(key==RegistryKey.ENCHANTMENT){
            var entry=Host.active().game.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(name.toString())).orElseThrow();
            var old=enchantments.get(name);if(old!=null && old.holder==entry)return old;
            var wrapped=new BukkitEnchant(entry);enchantments.put(name,wrapped);return wrapped;
        }
        if(key==RegistryKey.GAME_RULE){
            var old=rules.get(name);if(old!=null)return old;
            var rule=BuiltInRegistries.GAME_RULE.getValue(Identifier.parse(name.toString()));
            var wrapped=new BukkitRule(rule);old=rules.putIfAbsent(name,wrapped);return old==null?wrapped:old;
        }
        if(key==RegistryKey.ENTITY_TYPE)return entityType(name.toString());
        if(key==RegistryKey.GAME_EVENT){
            var cached=gameEvents.get(name);if(cached!=null)return cached;
            var holder=BuiltInRegistries.GAME_EVENT.get(Identifier.parse(name.toString())).orElseThrow();
            var entry=new org.bukkit.GameEvent() {
                public NamespacedKey getKey(){return name;}
                public int getRange(){return holder.value().notificationRadius();}
                public int getVibrationLevel(){return net.minecraft.world.level.gameevent.vibrations.VibrationSystem.getGameEventFrequency(holder);}
            };
            var old=gameEvents.putIfAbsent(name,entry);return old==null?entry:old;
        }
        if(key==RegistryKey.FLUID){
            var cached=fluids.get(name);if(cached!=null)return cached;
            var view=Views.of(org.bukkit.Fluid.class,name.toString(),name,(method,args)->switch(method){
                case "key","getKey" -> name;
                case "name" -> name.getKey().toUpperCase(Locale.ROOT);
                default -> throw Views.unsupported("Fluid",method);
            });
            var old=fluids.putIfAbsent(name,view);return old==null?view:old;
        }
        if(key==RegistryKey.BLOCK || key==RegistryKey.ITEM) {
            var cache=key==RegistryKey.BLOCK ? blocks : items;
            var cached=cache.get(name);
            if(cached!=null)return cached;
            Class<? extends Keyed> type=key==RegistryKey.BLOCK ? org.bukkit.block.BlockType.Typed.class : org.bukkit.inventory.ItemType.Typed.class;
            Keyed value=Views.of(type,name.toString(),name,(m,a)->switch(m) {
                case "key","getKey" -> name;
                case "createItemStack" -> {
                    var item=BuiltInRegistries.ITEM.getOptional(Identifier.parse(name.toString())).orElseThrow();
                    var stack=new BlockViews.Stack(new net.minecraft.world.item.ItemStack(item,a.length==0?1:(Integer)a[0]));
                    if(a.length>1)throw Views.unsupported("ItemType","typed metadata factory");
                    yield stack;
                }
                case "getMaxDurability" -> (short)BuiltInRegistries.ITEM.getValue(Identifier.parse(name.toString())).components().getOrDefault(net.minecraft.core.component.DataComponents.MAX_DAMAGE,0).intValue();
                case "getMaxStackSize" -> BuiltInRegistries.ITEM.getValue(Identifier.parse(name.toString())).components().getOrDefault(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1);
                case "hasBlockType" -> BuiltInRegistries.ITEM.getValue(Identifier.parse(name.toString())) instanceof net.minecraft.world.item.BlockItem;
                case "isEdible" -> BuiltInRegistries.ITEM.getValue(Identifier.parse(name.toString())).components().has(net.minecraft.core.component.DataComponents.FOOD);
                case "asMaterial" -> BlockViews.material(Identifier.parse(name.toString()));
                case "createBlockData" -> {
                    var state=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(name.toString())).orElseThrow().defaultBlockState();
                    if(a.length==1 && a[0] instanceof String suffix)
                        state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,name+suffix,false).blockState();
                    var data=BlockViews.data(state);
                    if(a.length==1 && a[0] instanceof java.util.function.Consumer<?> configure)
                        ((java.util.function.Consumer<org.bukkit.block.data.BlockData>)configure).accept(data);
                    yield data;
                }
                case "typed" -> cache.get(name);
                default -> throw Views.unsupported(type.getSimpleName(),m);
            });
            var existing=cache.putIfAbsent(name,value);
            return existing==null ? value : existing;
        }
        if (key==RegistryKey.MENU) return menus.computeIfAbsent(name,k->Views.of(MenuType.Typed.class,k.toString(),k,(m,a)->switch(m) {
            case "getKey","key" -> k;
            case "typed" -> menus.get(k);
            default -> throw Views.unsupported("MenuType",m);
        }));
        return damage.computeIfAbsent(name,k->Views.of(DamageType.class,k.toString(),k,(m,a)->switch(m) {
            case "getKey","key" -> k;
            case "getTranslationKey" -> "death.attack."+Host.active().game.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOptional(Identifier.parse(k.toString())).orElseThrow().msgId();
            default -> throw Views.unsupported("DamageType",m);
        }));
    }
}
