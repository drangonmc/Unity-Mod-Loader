package net.neoforged.neoforge.capabilities;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
public final class ItemCapability<T,C> extends BaseCapability<T,C>{
    private static final Map<Identifier,ItemCapability<?,?>> types=new LinkedHashMap<>();
    private final Map<Item,List<ICapabilityProvider<ItemStack,C,T>>> bindings=new IdentityHashMap<>();
    private ItemCapability(Identifier id,Class<T> type,Class<C> context){super(id,type,context);}
    @SuppressWarnings("unchecked") public static synchronized <T,C> ItemCapability<T,C> create(Identifier id,Class<T> type,Class<C> context){
        var cap=types.computeIfAbsent(id,k->new ItemCapability<>(k,type,context));
        if(cap.typeClass()!=type || cap.contextClass()!=context)throw new IllegalArgumentException("Incompatible capability "+id);
        return (ItemCapability<T,C>)cap;
    }
    public static <T> ItemCapability<T,Void> createVoid(Identifier id,Class<T> type){return create(id,type,Void.class);}
    
    public static synchronized List<ItemCapability<?,?>> getAll(){return List.copyOf(types.values());}
    void add(Item key, ICapabilityProvider<ItemStack,C,T> provider){bindings.computeIfAbsent(key,k->new ArrayList<>()).add(provider);}
    boolean has(Item key){return bindings.containsKey(key);}
    public T getCapability(ItemStack owner,C context){
        var providers=bindings.get(owner.getItem());if(providers==null)return null;
        for(var fn:providers){var value=fn.getCapability(owner,context);if(value!=null)return value;}return null;
    }
    public static void resetProviders(){for(var cap:getAll())cap.bindings.clear();}
}
