package mc.unity.plugins;

import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

/** A Bukkit enum value per registered mod entity, preserving its actual namespaced ID for region rules. */
public final class ModEntityTypes {
    private static Map<String,EntityType> names=Map.of();
    public static EntityType lookup(String key){return names.get(key);}
    public static EntityType[] append(EntityType[] base){
        var ids=BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(k->!k.getNamespace().equals("minecraft")).sorted().toList();
        if(ids.isEmpty())return base;
        var result=Arrays.copyOf(base,base.length+ids.size());
        var lookup=new HashMap<String,EntityType>();
        try{
            var create=MethodHandles.privateLookupIn(EntityType.class,MethodHandles.lookup()).findConstructor(
                EntityType.class,MethodType.methodType(void.class,String.class,int.class,NamespacedKey.class,Class.class));
            int index=base.length;
            for(var id:ids){
                var game=BuiltInRegistries.ENTITY_TYPE.getValue(id);
                @SuppressWarnings({"rawtypes","unchecked"}) boolean living=DefaultAttributes.hasSupplier((net.minecraft.world.entity.EntityType)game);
                Class<?> api=living?org.bukkit.entity.LivingEntity.class:org.bukkit.entity.Entity.class;
                String enumName="UNITY_ENTITY_"+HexFormat.of().withUpperCase().formatHex(id.toString().getBytes(StandardCharsets.UTF_8));
                var key=new NamespacedKey(id.getNamespace(),id.getPath());
                EntityType value=(EntityType)create.invokeExact(enumName,index,key,api);
                result[index++]=value;lookup.put(id.toString(),value);
            }
        }catch(RuntimeException|Error fatal){throw fatal;}catch(Throwable failure){throw new ExceptionInInitializerError(failure);}
        names=Map.copyOf(lookup);return result;
    }
}
