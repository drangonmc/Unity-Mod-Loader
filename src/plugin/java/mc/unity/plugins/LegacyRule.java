package mc.unity.plugins;
import java.util.function.Function;
final class LegacyRule<M,T> extends org.bukkit.GameRule<T> {
    final org.bukkit.GameRule<M> modern;
    final Function<M,T> read;
    final Function<T,M> write;
    final Class<T> type;
    LegacyRule(org.bukkit.GameRule<M> modern,Function<T,M> write,Function<M,T> read,Class<T> type){this.modern=modern;this.write=write;this.read=read;this.type=type;}
    public T current(net.minecraft.server.level.ServerLevel level){return read.apply(level.getGameRules().get(((BukkitRule<M>)modern).rule));}
    public String getName(){return modern.getName();}
    public org.bukkit.NamespacedKey getKey(){return modern.getKey();}
    public Class<T> getType(){return type;}
    public T getDefaultValue(){return read.apply(modern.getDefaultValue());}
    public String translationKey(){return modern.translationKey();}
}
