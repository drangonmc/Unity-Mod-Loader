package qa.unity32;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class LifeProbe extends JavaPlugin implements Listener {
    private final java.util.Set<java.util.UUID> firstStep=new java.util.HashSet<>();
    public void onEnable(){getServer().getPluginManager().registerEvents(this,this);var extras=new Extras(this);
        getCommand("u32world").setExecutor(extras);getCommand("u32world").setTabCompleter(extras);getServer().getPluginManager().registerEvents(extras,this);}
    void mark(String s){getLogger().info("U32 "+s);}
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender,org.bukkit.command.Command cmd,String label,String[] args){
        if(sender instanceof Player p){
            if(args.length>0 && args[0].equals("cooldown")){p.setPortalCooldown(0);mark("cooldown reset");}
            if(args.length>0 && args[0].equals("release"))firstStep.remove(p.getUniqueId());
            mark("permission "+p.getName()+" "+p.hasPermission("unity32.chest"));
        }return true;
    }
    @EventHandler(priority=EventPriority.LOWEST) public void deathLow(PlayerDeathEvent e){mark("death-low "+e.getEntity().getName()+" "+count(e));}
    int count(PlayerDeathEvent e){return e.getDrops().stream().filter(i->i!=null && i.getType()==Material.EMERALD).mapToInt(ItemStack::getAmount).sum();}
    @EventHandler(priority=EventPriority.HIGH) public void deathHigh(PlayerDeathEvent e){
        if(e.getEntity().getName().equals("VolumeB")){int n=count(e); if(n==7){e.getDrops().removeIf(i->i.getType()==Material.EMERALD);e.getDrops().add(new ItemStack(Material.DIAMOND,3));}}
        mark("death-high "+e.getEntity().getName()+" "+e.getKeepInventory());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void deathMonitor(PlayerDeathEvent e){mark("death-monitor "+e.getEntity().getName()+" keep="+e.getKeepInventory()+" emeralds="+count(e));}
    @EventHandler(priority=EventPriority.LOWEST) public void respawnLow(PlayerRespawnEvent e){mark("respawn-low "+e.getPlayer().getName()+" "+e.getRespawnReason());}
    @EventHandler(priority=EventPriority.MONITOR) public void respawnMonitor(PlayerRespawnEvent e){if(e.getPlayer().getName().equals("VolumeA"))firstStep.add(e.getPlayer().getUniqueId());mark("respawn-monitor "+e.getPlayer().getName()+" "+e.getRespawnLocation().getBlockX()+" "+e.getRespawnLocation().getBlockZ());}
    @EventHandler(priority=EventPriority.HIGH) public void firstMove(PlayerMoveEvent e){
        if(e instanceof PlayerTeleportEvent || !firstStep.contains(e.getPlayer().getUniqueId()) || !e.hasChangedPosition())return;
        mark("first-step "+e.getFrom().getBlockX()+" "+e.getFrom().getBlockZ());e.setCancelled(true);
    }
    @EventHandler public void cause(PlayerTeleportEvent e){
        if(e.getPlayer().getName().equals("VolumeA") && java.util.Set.of("ENDER_PEARL","CONSUMABLE_EFFECT","NETHER_PORTAL","END_PORTAL","END_GATEWAY","SPECTATE").contains(e.getCause().name())){
            mark("cause "+e.getCause());e.setCancelled(true);
        }
    }
    @EventHandler public void portal(PlayerPortalEvent e){
        if(e.getPlayer().getName().equals("VolumeA") && java.util.Set.of("NETHER_PORTAL","END_PORTAL","END_GATEWAY").contains(e.getCause().name())){
            mark("cause "+e.getCause());e.setCancelled(true);
        }
    }
    @EventHandler public void console(io.papermc.paper.event.player.AsyncChatEvent e){
        var text=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message());
        if(text.equals("u32-console-check"))mark("console-removed "+e.viewers().remove(Bukkit.getConsoleSender()));
    }
    private boolean box(InventoryEvent e){var at=e.getInventory().getLocation();return at!=null && at.getBlockX()==65 && at.getBlockY()==80 && at.getBlockZ()==23;}
    @EventHandler(priority=EventPriority.LOWEST) public void opening(InventoryOpenEvent e){if(box(e))mark("open-low "+e.getPlayer().getName()+" "+e.isCancelled());}
    @EventHandler(priority=EventPriority.HIGH) public void restrict(InventoryOpenEvent e){if(box(e) && e.getPlayer().getName().equals("VolumeB") && !e.getPlayer().hasPermission("unity32.chest"))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void allowed(InventoryOpenEvent e){if(box(e))mark("open-allowed "+e.getPlayer().getName());}
    @EventHandler(priority=EventPriority.MONITOR) public void openMonitor(InventoryOpenEvent e){if(box(e))mark("open-monitor "+e.getPlayer().getName()+" "+e.isCancelled());}
    @EventHandler(priority=EventPriority.MONITOR) public void closing(InventoryCloseEvent e){if(box(e))mark("close "+e.getPlayer().getName()+" "+e.getReason());}
}
