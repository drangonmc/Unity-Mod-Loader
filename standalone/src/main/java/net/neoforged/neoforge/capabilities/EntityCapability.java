package net.neoforged.neoforge.capabilities;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.*;
public final class EntityCapability<T,C> extends BaseCapability<T,C>{
    private static final Map<Identifier,EntityCapability<?,?>> types=new LinkedHashMap<>();
    private final Map<EntityType<?>,List<ICapabilityProvider<Entity,C,T>>> bindings=new IdentityHashMap<>();
    private EntityCapability(Identifier id,Class<T> type,Class<C> context){super(id,type,context);}
    @SuppressWarnings("unchecked") public static synchronized <T,C> EntityCapability<T,C> create(Identifier id,Class<T> type,Class<C> context){
        var cap=types.computeIfAbsent(id,k->new EntityCapability<>(k,type,context));
        if(cap.typeClass()!=type || cap.contextClass()!=context)throw new IllegalArgumentException("Incompatible capability "+id);
        return (EntityCapability<T,C>)cap;
    }
    public static <T> EntityCapability<T,Void> createVoid(Identifier id,Class<T> type){return create(id,type,Void.class);}
    public static <T> EntityCapability<T,Direction> createSided(Identifier id,Class<T> type){return create(id,type,Direction.class);}
    public static synchronized List<EntityCapability<?,?>> getAll(){return List.copyOf(types.values());}
    void add(EntityType<?> key, ICapabilityProvider<Entity,C,T> provider){bindings.computeIfAbsent(key,k->new ArrayList<>()).add(provider);}
    boolean has(EntityType<?> key){return bindings.containsKey(key);}
    public T getCapability(Entity owner,C context){
        var providers=bindings.get(owner.getType());if(providers==null)return null;
        for(var fn:providers){var value=fn.getCapability(owner,context);if(value!=null)return value;}return null;
    }
    public static void resetProviders(){for(var cap:getAll())cap.bindings.clear();}
}
