package qa.protection;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectionProbe extends JavaPlugin implements Listener {
    private String phase="join";
    private void record(String key,String detail) {
        getLogger().info("PROTECTOBS "+phase+" "+key+" "+detail);
        try {Files.writeString(getDataFolder().toPath().resolve("observed.tsv"),phase+"\t"+key+"\t"+detail+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    public void onEnable() {
        getDataFolder().mkdirs();
        getServer().getPluginManager().registerEvents(this,this);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(args.length==1 && args[0].equals("census")) {
            try {
                for(var registration:PlayerJoinEvent.getHandlerList().getRegisteredListeners()) {
                    if(!registration.getPlugin().getName().equals("LetMeDespawn"))continue;
                    var listener=registration.getListener();
                    var trackerField=listener.getClass().getDeclaredField("positions");
                    trackerField.setAccessible(true);
                    var tracker=trackerField.get(listener);
                    var map=tracker.getClass().getDeclaredField("positions");
                    map.setAccessible(true);
                    var cached=(Map<?,?>)map.get(tracker);
                    long found=Bukkit.getOnlinePlayers().stream().filter(p->cached.containsKey(p.getUniqueId())).count();
                    sender.sendMessage("PROTECT census "+found);
                    if(found==2)record("letmedespawn.players","2");
                    return true;
                }
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            throw new IllegalStateException("No LetMeDespawn join listener");
        }
        if(args.length==1) {phase=args[0];record("phase",phase);sender.sendMessage("PROTECT "+phase);}
        return true;
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){
        var p=e.getPlayer();
        record("join",p.getName()+" "+p.equals(Bukkit.getPlayer(p.getUniqueId()))+" "+Bukkit.getOnlinePlayers().contains(p));
    }
    private static String pos(Block b){return b.getX()+","+b.getY()+","+b.getZ();}
    private void bucket(String step,PlayerBucketEvent e) {
        record(step,e.getClass().getSimpleName()+" "+e.getPlayer().getName()+" "+pos(e.getBlock())+" "+
               pos(e.getBlockClicked())+" "+e.getBlockFace()+" "+e.getHand()+" "+e.getBucket()+" "+e.getItemStack().getType()+" "+e.isCancelled());
    }
    @EventHandler(priority=EventPriority.LOWEST) public void emptyLow(PlayerBucketEmptyEvent e){bucket("low",e);}
    @EventHandler(priority=EventPriority.MONITOR) public void emptyEnd(PlayerBucketEmptyEvent e){bucket("monitor",e);}
    @EventHandler(priority=EventPriority.LOWEST) public void fillLow(PlayerBucketFillEvent e){bucket("low",e);}
    @EventHandler(priority=EventPriority.HIGH) public void fillPolicy(PlayerBucketFillEvent e){if(phase.equals("fill-deny") || phase.equals("lava-fill-deny")) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fillActive(PlayerBucketFillEvent e){bucket("active",e);}
    @EventHandler(priority=EventPriority.MONITOR) public void fillEnd(PlayerBucketFillEvent e){bucket("monitor",e);}
    private static String owner(Projectile p){return p.getShooter() instanceof Player player ? player.getName() : "other";}
    private void shot(String step,EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getProjectile() instanceof Projectile p)) return;
        record("shoot."+step,owner(p)+" "+e.getBow().getType()+" "+e.getConsumable().getType()+" "+e.getHand()+" "+Math.round(e.getForce()*100)+" "+e.isCancelled());
    }
    @EventHandler(priority=EventPriority.LOWEST) public void shootLow(EntityShootBowEvent e){shot("low",e);}
    @EventHandler(priority=EventPriority.HIGH) public void shootPolicy(EntityShootBowEvent e){if(phase.equals("shoot-deny")) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void shootEnd(EntityShootBowEvent e){shot("monitor",e);}
    @EventHandler(priority=EventPriority.LOWEST) public void launchLow(ProjectileLaunchEvent e){
        if(e.getEntity().getShooter() instanceof Player)record("launch.low",owner(e.getEntity())+" "+e.getEntity().getType()+" "+e.isCancelled());
    }
    @EventHandler(priority=EventPriority.HIGH) public void launchPolicy(ProjectileLaunchEvent e){if(phase.equals("launch-deny"))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void launchEnd(ProjectileLaunchEvent e) throws ReflectiveOperationException {
        if(!(e.getEntity().getShooter() instanceof Player))return;
        record("launch.monitor",owner(e.getEntity())+" "+e.getEntity().getType()+" "+e.isCancelled());
        if(e.getEntity() instanceof EnderPearl p) {
            // Read the author's own tracking state after its listener runs. This never changes the projectile.
            for(var reg:e.getHandlers().getRegisteredListeners())if(reg.getPlugin().getName().equals("SkippyPearls")) {
                var listener=reg.getListener();var f=listener.getClass().getDeclaredField("activePearls");f.setAccessible(true);
                record("skippy.tracked",Boolean.toString(((Map<?,?>)f.get(listener)).containsKey(p.getUniqueId())));break;
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGH) public void hitPolicy(ProjectileHitEvent e) {
        if(phase.equals("hit-deny") && e.getHitEntity() instanceof Player && e.getEntity().getShooter() instanceof Player)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void hit(ProjectileHitEvent e) {
        if(e.getEntity().getShooter() instanceof Player) record("hit",owner(e.getEntity())+" "+e.getEntity().getType()+" "+(e.getHitEntity() instanceof Player p?p.getName():"block")+" "+e.isCancelled());
    }
    @EventHandler(priority=EventPriority.LOWEST) public void damageLow(EntityDamageEvent e) {
        if(!(e.getEntity() instanceof Player player))return;
        record("damage.low",player.getName()+" "+e.getCause()+" "+(e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Projectile p?owner(p):"-")+" "+e.isCancelled());
    }
    @EventHandler(priority=EventPriority.HIGH) public void damagePolicy(EntityDamageEvent e) {
        if(e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Projectile p && p.getShooter() instanceof Player) {
            if(phase.equals("damage-deny"))e.setCancelled(true);
            if(phase.equals("damage-ok"))e.setDamage(4);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void damageEnd(EntityDamageEvent e){
        if(e.getEntity() instanceof Player p)record("damage.monitor",p.getName()+" "+e.getCause()+" "+e.isCancelled());
    }
}
