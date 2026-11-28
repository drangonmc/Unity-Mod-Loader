package mc.unity.plugins;
import java.util.function.Function;
final class LegacyRule<M,T> extends org.bukkit.GameRule<T> {
    final org.bukkit.GameRule<M> modern;
    final Function<M,T> read;
    final Function<T,M> write;
    final Class<T> type;
    static org.bukkit.GameRule<?> find(String name){
        var rule=org.bukkit.GameRule.getByName(name);
        if(rule!=null || !name.matches("[a-z][A-Za-z0-9]*"))return rule;
        String constant=name.replaceAll("([a-z0-9])([A-Z])","$1_$2").toUpperCase(java.util.Locale.ROOT);
        try{
            var field=org.bukkit.GameRule.class.getField(constant);
            return org.bukkit.GameRule.class.isAssignableFrom(field.getType())?(org.bukkit.GameRule<?>)field.get(null):null;
        }catch(NoSuchFieldException missing){return null;}
        catch(IllegalAccessException failure){throw new IllegalStateException("Reading public game rule "+constant,failure);}
    }
    LegacyRule(org.bukkit.GameRule<M> modern,Function<T,M> write,Function<M,T> read,Class<T> type){this.modern=modern;this.write=write;this.read=read;this.type=type;}
    public T current(net.minecraft.server.level.ServerLevel level){return read.apply(level.getGameRules().get(((BukkitRule<M>)modern).rule));}
    public String getName(){return modern.getName();}
    public org.bukkit.NamespacedKey getKey(){return modern.getKey();}
    public Class<T> getType(){return type;}
    public T getDefaultValue(){return read.apply(modern.getDefaultValue());}
    public String translationKey(){return modern.translationKey();}
}
