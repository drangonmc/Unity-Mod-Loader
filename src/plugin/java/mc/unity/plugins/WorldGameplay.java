package mc.unity.plugins;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ServerExplosion;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.BlockExplodeEvent;
import static org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

final class WorldGameplay {
    private static final class Blast { final ServerLevel level; final double x,y,z; boolean cancelled;
        Blast(ServerLevel l,Location v){level=l;x=v.getX();y=v.getY();z=v.getZ();}
        boolean matches(ServerExplosion e){return level==e.level() && x==e.center().x && y==e.center().y && z==e.center().z;}
    }
    private final ThreadLocal<Blast> current=new ThreadLocal<>();
    private List<BlockPos> cancelled(ServerExplosion e){var b=current.get();if(b!=null && b.matches(e))b.cancelled=true;return new ArrayList<>();}
    boolean createExplosion(ServerLevel level,Object[] args){
        Location at;float power;boolean fire=false,blocks=true;org.bukkit.entity.Entity source=null;
        if(args[0] instanceof Number x){
            at=new Location(host.views.world(level),x.doubleValue(),((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue());power=(float)args[3];
            if(args.length>4)fire=(boolean)args[4];if(args.length>5)blocks=(boolean)args[5];if(args.length>6)source=(org.bukkit.entity.Entity)args[6];
        }else{
            boolean first=args[0] instanceof Location;int i=first?0:1;
            if(!first)source=(org.bukkit.entity.Entity)args[0];
            if(!first && !(args[i] instanceof Location)){at=Objects.requireNonNull(source).getLocation();i--;}else at=(Location)args[i];
            power=(float)args[i+1];if(args.length>i+2)fire=(boolean)args[i+2];if(args.length>i+3)blocks=(boolean)args[i+3];
            if(first && args.length>4)source=(org.bukkit.entity.Entity)args[4];
        }
        at.checkFinite();if(!Float.isFinite(power)||power<0)throw new IllegalArgumentException("Explosion power");
        var frame=new Blast(level,at);var prior=current.get();current.set(frame);
        try{level.explode(source==null?null:level.getEntity(source.getUniqueId()),at.getX(),at.getY(),at.getZ(),power,fire,
                blocks?net.minecraft.world.level.Level.ExplosionInteraction.BLOCK:net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            return !frame.cancelled;
        }finally{if(prior==null)current.remove();else current.set(prior);}
    }
    final Host host;
    WorldGameplay(Host host){this.host=host;}
    boolean creature(Entity entity,String raw){
        if(host.views==null || CreatureSpawnEvent.getHandlerList().getRegisteredListeners().length==0)return true;
        var candidate=host.views.entity(entity);
        if(!(candidate instanceof org.bukkit.entity.LivingEntity living))return true;
        SpawnReason reason=switch(raw){
            case "CHUNK_GENERATION"->SpawnReason.CHUNK_GEN;case "BREEDING"->SpawnReason.BREEDING;
            case "SPAWN_ITEM_USE"->SpawnReason.SPAWNER_EGG;case "EVENT"->SpawnReason.DEFAULT;
            case "MOB_SUMMONED"->SpawnReason.SPELL;
            default->{try{yield SpawnReason.valueOf(raw);}catch(IllegalArgumentException ex){yield SpawnReason.DEFAULT;}}
        };
        var event=new CreatureSpawnEvent(living,reason);host.fire(event);return !event.isCancelled();
    }
    List<BlockPos> explode(ServerExplosion exp,List<BlockPos> original){
        var cause=exp.getDirectSourceEntity();
        if(host.views==null || (cause==null?BlockExplodeEvent.getHandlerList():EntityExplodeEvent.getHandlerList()).getRegisteredListeners().length==0)return original;
        var level=exp.level();var at=exp.center();var world=host.views.world(level);
        var loc=new Location(world,at.x,at.y,at.z);var blocks=new ArrayList<Block>();
        for(var pos:original)if(!level.getBlockState(pos).isAir())blocks.add(host.views.blocks.block(level,pos));
        var result=org.bukkit.ExplosionResult.valueOf(exp.getBlockInteraction().name());
        float fraction=result==org.bukkit.ExplosionResult.DESTROY_WITH_DECAY?1f/exp.radius():1f;
        if(cause==null){
            var block=loc.getBlock();var pos=BlockPos.containing(at);
            var event=new BlockExplodeEvent(block,host.views.blocks.snapshot(level,pos,mc.unity.runtime.WorldActions.exploded(exp,pos)),blocks,fraction,result);
            host.fire(event);if(event.isCancelled())return cancelled(exp);fraction=event.getYield();
        }else{
            var event=new EntityExplodeEvent(host.views.entity(cause),loc,blocks,fraction,result);host.fire(event);
            if(event.isCancelled())return cancelled(exp);fraction=event.getYield();
        }
        if(result==org.bukkit.ExplosionResult.DESTROY || result==org.bukkit.ExplosionResult.DESTROY_WITH_DECAY)mc.unity.runtime.WorldActions.yield(exp,fraction);
        return new ArrayList<>(blocks.stream().filter(b->b.getWorld().equals(world)).map(b->new BlockPos(b.getX(),b.getY(),b.getZ())).distinct().toList());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    Object spawn(ServerLevel level,Object[] args){
        var at=(Location)args[0];at.checkFinite();
        if(args[1]==org.bukkit.entity.Entity.class || args[1]==org.bukkit.entity.LivingEntity.class)throw new IllegalArgumentException("A concrete entity type is required");
        var type=args[1] instanceof org.bukkit.entity.EntityType t?t:java.util.Arrays.stream(org.bukkit.entity.EntityType.values())
            .filter(t->t.getEntityClass()==args[1] && t.isSpawnable()).findFirst().orElseThrow(()->new IllegalArgumentException("No spawnable entity type for "+args[1]));
        var nativeType=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type.getKey().toString()));
        var entity=nativeType.create(level,EntitySpawnReason.TRIGGERED);
        if(entity==null)throw new IllegalArgumentException("Cannot create "+type);
        entity.snapTo(at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch());
        var reason=SpawnReason.CUSTOM;boolean random=true;
        for(var arg:args){if(arg instanceof SpawnReason s)reason=s;if(arg instanceof Boolean value)random=value;}
        if(random && entity instanceof Mob mob)mob.finalizeSpawn(level,level.getCurrentDifficultyAt(entity.blockPosition()),EntitySpawnReason.TRIGGERED,null);
        mc.unity.runtime.WorldActions.mark(entity,reason.name());
        var wrapped=host.views.entity(entity);
        for(var arg:args)if(arg instanceof java.util.function.Consumer callback)callback.accept(wrapped);
        level.addFreshEntity(entity);return wrapped;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    Object drop(ServerLevel level,Object[] args,boolean natural){
        var loc=(Location)args[0];loc.checkFinite();var stack=BlockViews.Stack.nativeCopy((org.bukkit.inventory.ItemStack)args[1]);
        if(stack.isEmpty())throw new IllegalArgumentException("Cannot drop an empty stack");
        var r=level.getRandom();var entity=new ItemEntity(level,loc.getX()+(natural?.25+r.nextDouble()*.5:0),
            loc.getY()+(natural?.25+r.nextDouble()*.5:0),loc.getZ()+(natural?.25+r.nextDouble()*.5:0),stack);
        entity.setDefaultPickUpDelay();var view=host.views.entity(entity);
        if(args.length>2 && args[2] instanceof java.util.function.Consumer callback)callback.accept(view);
        level.addFreshEntity(entity);return view;
    }
}
