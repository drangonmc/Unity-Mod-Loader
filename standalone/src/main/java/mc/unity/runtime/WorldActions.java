package mc.unity.runtime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;

public final class WorldActions {
    private static final Map<Entity,String> reasons=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<net.minecraft.world.damagesource.DamageSource,net.minecraft.world.level.block.state.BlockState> exploding=Collections.synchronizedMap(new WeakHashMap<>());
    public static net.minecraft.world.damagesource.DamageSource blockSource(net.minecraft.world.damagesource.DamageSource source,net.minecraft.world.level.block.state.BlockState state){
        exploding.put(source,state);return source;
    }
    public static net.minecraft.world.level.block.state.BlockState exploded(ServerExplosion explosion,BlockPos pos){
        var state=exploding.remove(explosion.getDamageSource());return state==null?explosion.level().getBlockState(pos):state;
    }
    private static final Map<Explosion,Float> yields=Collections.synchronizedMap(new WeakHashMap<>());
    public static void remember(Entity e,EntitySpawnReason reason){
        if(e instanceof Mob && !e.level().isClientSide() && ServerHooks.pluginHost()!=null)reasons.putIfAbsent(e,reason.name());
    }
    public static void mark(Entity e,String reason){reasons.put(e,reason);}
    public static boolean allowed(Entity entity,boolean loaded){
        if(loaded || entity.level().isClientSide() || !(entity instanceof LivingEntity) || entity instanceof net.minecraft.world.entity.player.Player)return true;
        var reason=reasons.remove(entity);var h=ServerHooks.pluginHost();
        return h==null || h.creature(entity,reason==null?"DEFAULT":reason);
    }
    public static List<BlockPos> explosions(ServerExplosion e,List<BlockPos> positions){
        var h=ServerHooks.pluginHost();return h==null || e.getBlockInteraction()==Explosion.BlockInteraction.KEEP?positions:h.explode(e,positions);
    }
    public static void yield(ServerExplosion e,float value){if(Float.isFinite(value))yields.put(e,Math.clamp(value,0,1));}
    public static Explosion.BlockInteraction decay(Explosion e){
        Float v=yields.get(e);return v==null?e.getBlockInteraction():v<1?Explosion.BlockInteraction.DESTROY_WITH_DECAY:Explosion.BlockInteraction.DESTROY;
    }
    public static boolean drop(net.minecraft.world.level.block.Block block,Explosion explosion){
        Float value=yields.get(explosion);return (value==null || value!=0f) && block.dropFromExplosion(explosion);
    }
    public static float radius(Explosion e){Float v=yields.get(e);return v==null?e.radius():v<=1f/Float.MAX_VALUE?Float.MAX_VALUE:1f/v;}
}
