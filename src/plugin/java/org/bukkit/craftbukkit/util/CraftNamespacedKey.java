package org.bukkit.craftbukkit.util;
import org.bukkit.NamespacedKey;
import net.minecraft.resources.Identifier;

public final class CraftNamespacedKey {
    private CraftNamespacedKey() {}
    public static Identifier toMinecraft(NamespacedKey key) { return Identifier.fromNamespaceAndPath(key.getNamespace(),key.getKey()); }
    public static NamespacedKey fromMinecraft(Identifier id) { return new NamespacedKey(id.getNamespace(),id.getPath()); }
    public static NamespacedKey fromString(String value) { return NamespacedKey.fromString(value); }
}
