package qa.unity31;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod("unity31review")
public final class NeoReview {
    private static final Set<String> seen=ConcurrentHashMap.newKeySet();
    public NeoReview(){NeoForge.EVENT_BUS.register(this);}
    private static void log(String s){System.out.println("NEO31 "+s);}
    private static void once(String s){if(seen.add(s))log(s);}
    @SubscribeEvent public void client(LevelTickEvent.Pre e){if(e.getLevel().isClientSide())once("client-tick-pre");}
    @SubscribeEvent public void client(LevelTickEvent.Post e){if(e.getLevel().isClientSide())once("client-tick-post");}
    @SubscribeEvent public void join(EntityJoinLevelEvent e){if(e.getLevel().isClientSide())once("client-join");}
    @SubscribeEvent public void load(ChunkEvent.Load e){if(e.getLevel().isClientSide())once("client-load");}
    @SubscribeEvent public void unload(ChunkEvent.Unload e){if(e.getLevel().isClientSide())once("client-unload");}
    @SubscribeEvent(receiveCanceled=true) public void breakBlock(BreakBlockEvent e){
        if(e.getPos().getX()==62 && e.getPos().getZ()==24){log("break-"+(e.getLevel().isClientSide()?"client":"server")+" "+e.isCanceled());e.setCanceled(false);}
    }
    @SubscribeEvent public void place(EntityPlaceEvent e){
        if(e.getPos().getX()==63 && e.getPos().getZ()==24)log("against "+e.getPlacedAgainst().getBlock());
    }
    @SubscribeEvent public void pre(LivingDamageEvent.Pre e){
        for(String tag:e.getEntity().entityTags())if(tag.startsWith("rev31")){log(tag+" pre");if(tag.equals("rev31zero"))e.setNewDamage(0);}
    }
    @SubscribeEvent public void post(LivingDamageEvent.Post e){
        for(String tag:e.getEntity().entityTags())if(tag.startsWith("rev31"))log(tag+" post "+e.getHealthDamage());
    }
    @SubscribeEvent public void shield(LivingShieldBlockEvent e){
        if(e.getEntity().entityTags().contains("rev31shield")){log("shield "+e.getOriginalBlockedDamage());e.setShieldDamage(2);}
    }
    @SubscribeEvent public void death(LivingDeathEvent e){
        for(String tag:e.getEntity().entityTags())if(tag.startsWith("rev31"))log(tag+" death");
    }
    @SubscribeEvent public void drops(LivingDropsEvent e){
        var p=e.getEntity();
        if(p.entityTags().contains("rev31lethal"))log("rev31lethal drops");
        if(p.entityTags().contains("rev31player")){
            int count=e.getDrops().stream().filter(d->d.getItem().is(Items.AMETHYST_SHARD)).mapToInt(d->d.getItem().getCount()).sum();
            if(count!=11)throw new AssertionError("Missing player death inventory "+count);
            e.getDrops().clear();
            e.getDrops().add(new ItemEntity(p.level(),p.getX(),p.getY(),p.getZ(),new ItemStack(Items.DIAMOND,3)));
            log("player contained 11; replaced with 3");
        }
    }
}
