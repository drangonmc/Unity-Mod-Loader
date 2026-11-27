package mc.unity.plugins;

import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/** One real Material per namespaced item/block in this server's plugin class domain. */
public final class ModMaterials {
    private static Map<String,Material> byKey = Map.of();

    public static Material[] append(Material[] vanilla) {
        var ids = new TreeSet<String>();
        for (var registry : List.of(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM))
            for (var id : registry.keySet())
                if (!id.getNamespace().equals("minecraft")) ids.add(id.toString());
        if (ids.isEmpty()) return vanilla;
        var result = Arrays.copyOf(vanilla, vanilla.length + ids.size());
        var keys = new HashMap<String, Material>();
        var names = new HashSet<String>();
        for (var value : vanilla) names.add(value.name());
        try {
            var create = MethodHandles.privateLookupIn(Material.class, MethodHandles.lookup()).findConstructor(
                Material.class, MethodType.methodType(void.class,String.class,int.class,NamespacedKey.class));
            int index = vanilla.length;
            for (String id : ids) {
                String name = id.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]","_");
                if (!names.add(name)) {
                    name = "UNITY_" + HexFormat.of().withUpperCase().formatHex(id.getBytes(StandardCharsets.UTF_8));
                    while (!names.add(name)) name = "_" + name;
                }
                Material value = (Material)create.invokeExact(name,index,Objects.requireNonNull(NamespacedKey.fromString(id)));
                result[index++] = value;
                keys.put(id,value);
            }
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new ExceptionInInitializerError(e); }
        byKey = Map.copyOf(keys);
        return result;
    }

    public static Material namespaced(String id) {
        return id != null && id.indexOf(':')>=0 ? byKey.get(id.toLowerCase(Locale.ROOT)) : null;
    }
}
