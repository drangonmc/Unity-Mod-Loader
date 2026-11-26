package qa.beta25;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.help.HelpTopic;
import org.bukkit.plugin.java.JavaPlugin;

public final class Beta25Probe extends JavaPlugin implements Listener {
    private String phase="login";
    private final Map<String,Player> joined=new HashMap<>();
    private void row(String key,String value){
        try{Files.writeString(getDataFolder().toPath().resolve("events.tsv"),phase+"\t"+key+"\t"+value+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
        getLogger().info("BETA25 "+phase+" "+key+" "+value);
    }
    @Override public void onEnable(){
        getDataFolder().mkdirs();
        var custom=new HelpTopic(){
            {name="UnityProbeTopic";shortText="Probe";fullText="Probe";}
            public boolean canSee(CommandSender who){return true;}
        };
        var help=getServer().getHelpMap();help.addTopic(custom);
        if(help.getHelpTopic(custom.getName())!=custom || !help.getHelpTopics().contains(custom))throw new AssertionError("Help topic registration");
        getServer().getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void join(PlayerJoinEvent e){var p=e.getPlayer();joined.put(p.getName(),p);row("join",p.getName()+" "+Bukkit.getOnlinePlayers().contains(p));}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(args.length!=1)return false;
        if(args[0].equals("view")){
            Player old=joined.get("VolumeA"),fresh=Bukkit.getPlayerExact("VolumeA");
            var item=old.getInventory().getItem(1);
            row("view",old.getUniqueId().equals(fresh.getUniqueId())+" "+old.equals(fresh)+" "+old.isOnline()+" "+old.getWorld().getUID().equals(fresh.getWorld().getUID())+
                " "+old.getLocation().distanceSquared(fresh.getLocation())+" "+old.getHealth()+" "+old.getLevel()+" "+old.isOp()+" "+old.hasPermission("unityprobe.op")+" "+(item==null?"AIR":item.getType()+"/"+item.getAmount()));
        }else if(args[0].equals("write")){
            var retained=joined.get("VolumeA");
            retained.getInventory().getItem(1).setAmount(4);
            row("write",""+Bukkit.getPlayerExact("VolumeA").getInventory().getItem(1).getAmount());
        }else{phase=args[0];row("phase",phase);}
        return true;
    }
    private void bucket(String step,PlayerBucketEvent e) {
        var b=e.getBlock();var clicked=e.getBlockClicked();
        row(step,e.getClass().getSimpleName()+" "+b.getX()+","+b.getY()+","+b.getZ()+" "+clicked.getX()+","+clicked.getY()+","+clicked.getZ()+" "+e.getBlockFace()+" "+e.getBucket()+" "+e.getHand()+" "+e.isCancelled());
    }
    @EventHandler(priority=EventPriority.LOWEST) public void emptyLow(PlayerBucketEmptyEvent e){bucket("bucket.low",e);}
    @EventHandler(priority=EventPriority.LOWEST) public void fillLow(PlayerBucketFillEvent e){bucket("bucket.low",e);}
    @EventHandler(priority=EventPriority.HIGH) public void emptyPolicy(PlayerBucketEmptyEvent e){if(phase.endsWith("bucket-deny"))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH) public void fillPolicy(PlayerBucketFillEvent e){if(phase.endsWith("bucket-deny"))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void emptyEnd(PlayerBucketEmptyEvent e){bucket("bucket.end",e);}
    @EventHandler(priority=EventPriority.MONITOR) public void fillEnd(PlayerBucketFillEvent e){bucket("bucket.end",e);}
    @EventHandler(priority=EventPriority.LOWEST) public void levelLow(CauldronLevelChangeEvent e){level("cauldron.low",e);}
    @EventHandler(priority=EventPriority.HIGH) public void levelPolicy(CauldronLevelChangeEvent e){
        if(phase.endsWith("level-deny"))e.setCancelled(true);
        if(phase.equals("alter-level")) {
            var proposed=e.getNewState();
            var data=(org.bukkit.block.data.Levelled)proposed.getBlockData();
            data.setLevel(2);proposed.setBlockData(data);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void levelEnd(CauldronLevelChangeEvent e){level("cauldron.end",e);}
    private void level(String key,CauldronLevelChangeEvent e){row(key,e.getReason()+" "+e.getOldLevel()+" "+e.getNewLevel()+" "+e.isCancelled());}
    @EventHandler(priority=EventPriority.LOWEST) public void dispenseLow(BlockDispenseEvent e){dispense("dispense.low",e);}
    @EventHandler(priority=EventPriority.HIGH) public void dispensePolicy(BlockDispenseEvent e){if(phase.endsWith("dispense-deny"))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void dispenseEnd(BlockDispenseEvent e){dispense("dispense.end",e);}
    private void dispense(String key,BlockDispenseEvent e){var v=e.getVelocity();row(key,e.getItem().getType()+" "+e.getItem().getAmount()+" "+v.getBlockX()+","+v.getBlockY()+","+v.getBlockZ()+" "+e.isCancelled());}
    @EventHandler(priority=EventPriority.MONITOR) public void shot(EntityShootBowEvent e) {
        if(e.getEntity() instanceof Player p && e.getProjectile() instanceof AbstractArrow a)
            row("shoot",p.getName()+" "+a.getPierceLevel()+" "+e.isCancelled());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void launch(ProjectileLaunchEvent e) {
        if(e.getEntity() instanceof AbstractArrow a && a.getShooter() instanceof Player p){
            row("launch",p.getName()+" "+a.getPierceLevel()+" "+e.isCancelled());
            getLogger().info("Arrow motion "+a.getVelocity());
        }
    }
    @EventHandler(priority=EventPriority.HIGH) public void hitPolicy(ProjectileHitEvent e){
        if(phase.equals("pierce-deny") && e.getHitEntity() instanceof Player && e.getEntity() instanceof AbstractArrow)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void hit(ProjectileHitEvent e){
        if(e.getHitEntity()!=null && e.getEntity() instanceof AbstractArrow a && a.getShooter() instanceof Player p)
            row("hit",p.getName()+" "+a.getPierceLevel()+" "+e.getHitEntity().getType()+" "+e.isCancelled());
    }
    @EventHandler(priority=EventPriority.HIGH) public void arrowDamage(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof AbstractArrow a && a.getShooter() instanceof Player && !e.isCancelled())e.setDamage(3);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void damage(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof AbstractArrow a && a.getShooter() instanceof Player p)row("damage",p.getName()+" "+e.getEntity().getType()+" "+e.isCancelled());
    }
}
