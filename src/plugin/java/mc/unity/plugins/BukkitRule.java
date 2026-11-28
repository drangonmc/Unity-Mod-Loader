package mc.unity.plugins;

final class BukkitRule<T> extends org.bukkit.GameRule<T> {
    final net.minecraft.world.level.gamerules.GameRule<T> rule;
    BukkitRule(net.minecraft.world.level.gamerules.GameRule<T> rule){this.rule=rule;}
    public String getName(){return rule.id();}
    public org.bukkit.NamespacedKey getKey(){return org.bukkit.NamespacedKey.fromString(rule.getIdentifier().toString());}
    public Class<T> getType(){return rule.valueClass();}
    public T getDefaultValue(){return rule.defaultValue();}
    public String translationKey(){return rule.getDescriptionId();}
}
