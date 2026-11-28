package org.bukkit.craftbukkit.util;
import net.minecraft.network.chat.Component;
import mc.unity.plugins.StackMeta;
import java.util.Arrays;

public final class CraftChatMessage {
    private CraftChatMessage() {}
    public static String fromComponent(Component value) { return value==null?"":StackMeta.legacy(value); }
    public static Component fromStringOrNull(String value) { return value==null||value.isEmpty()?null:StackMeta.fromLegacy(value); }
    public static Component[] fromString(String value) { return fromString(value,false); }
    public static Component[] fromString(String value,boolean keepNewlines) {
        if(value==null)return new Component[]{Component.empty()};
        if(keepNewlines)return new Component[]{StackMeta.fromLegacy(value)};
        return Arrays.stream(value.split("\\n",-1)).map(StackMeta::fromLegacy).toArray(Component[]::new);
    }
}
