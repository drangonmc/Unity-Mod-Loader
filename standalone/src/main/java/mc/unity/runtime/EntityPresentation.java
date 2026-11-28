package mc.unity.runtime;

import java.lang.invoke.*;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;

/** Server-thread state for plugin visibility overrides and temporary entities. */
public final class EntityPresentation {
    private static final class State {
        volatile boolean persistent=true, visible=true;
        final Map<UUID,Map<Object,Boolean>> overrides=new HashMap<>();
    }
    private static final Map<Entity,State> states=Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean used;
    private static final VarHandle TRACKED;
    private static final MethodHandle UPDATE;
    static {
        try{
            var type=Class.forName("net.minecraft.server.level.ChunkMap$TrackedEntity");
            TRACKED=MethodHandles.privateLookupIn(ChunkMap.class,MethodHandles.lookup()).findVarHandle(ChunkMap.class,"entityMap",it.unimi.dsi.fastutil.ints.Int2ObjectMap.class);
            UPDATE=MethodHandles.privateLookupIn(type,MethodHandles.lookup()).findVirtual(type,"updatePlayer",MethodType.methodType(void.class,ServerPlayer.class));
        }catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }
    private static State edit(Entity e){used=true;return states.computeIfAbsent(e,k->new State());}
    public static boolean canSave(Entity e){if(!used)return true;var s=states.get(e);return s==null || s.persistent;}
    public static void persistent(Entity e,boolean value){edit(e).persistent=value;}
    public static boolean defaultVisible(Entity e){var s=states.get(e);return s==null || s.visible;}
    public static boolean visible(Entity e,ServerPlayer p){
        if(!used)return true;
        var s=states.get(e);if(s==null)return true;
        synchronized(s){
            var requests=s.overrides.get(p.getUUID());
            if(requests==null || requests.isEmpty())return s.visible;
            return !requests.containsValue(false);
        }
    }
    public static void defaultVisible(Entity e,boolean value){
        var state=edit(e);if(state.visible==value)return;
        state.visible=value;
        if(e.level() instanceof ServerLevel level)for(var player:level.players())refresh(level,e,player);
    }
    public static void visible(Entity e,ServerPlayer player,Object plugin,boolean value){
        var state=edit(e);
        synchronized(state){state.overrides.computeIfAbsent(player.getUUID(),k->new IdentityHashMap<>()).put(plugin,value);}
        if(e.level()==player.level())refresh(player.level(),e,player);
    }
    public static void release(Object plugin){
        Map<Entity,State> snapshot;
        synchronized(states){snapshot=new IdentityHashMap<>(states);}
        for(var entry:snapshot.entrySet()){
            var state=entry.getValue();var changed=new ArrayList<UUID>();
            synchronized(state){
                state.overrides.forEach((id,map)->{if(map.remove(plugin)!=null)changed.add(id);});
                state.overrides.values().removeIf(Map::isEmpty);
            }
            var entity=entry.getKey();
            if(entity.level() instanceof ServerLevel level)for(var id:changed){
                var viewer=level.getServer().getPlayerList().getPlayer(id);
                if(viewer!=null && viewer.level()==level)refresh(level,entity,viewer);
            }
        }
    }
    private static void refresh(ServerLevel level,Entity e,ServerPlayer p){
        var tracked=((it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>)TRACKED.get(level.getChunkSource().chunkMap)).get(e.getId());
        if(tracked!=null)try{UPDATE.invoke(tracked,p);}catch(RuntimeException|Error ex){throw ex;}catch(Throwable ex){throw new IllegalStateException(ex);}
    }
    public static void clear(){states.clear();used=false;}
}
