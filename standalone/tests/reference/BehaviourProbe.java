package qa.reference;

import java.io.IOException;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Shared binary compiled only against the published Paper API. */
public final class BehaviourProbe extends JavaPlugin implements Listener {
    private void observe(String key, Object... values) {
        var row = new StringBuilder(key);
        for (var value : values) row.append('\t').append(value);
        try {
            Files.writeString(getDataFolder().toPath().resolve("observed.tsv"), row.append('\n'),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public void onEnable() {
        getDataFolder().mkdirs();
        getServer().getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void joined(PlayerJoinEvent e) {
        observe("join", Bukkit.getPlayer(e.getPlayer().getUniqueId()) != null,
                Bukkit.getOnlinePlayers().contains(e.getPlayer()));
    }
    @EventHandler public void interact(PlayerInteractEvent e) {
        if (e.getAction()==Action.RIGHT_CLICK_BLOCK && e.getItem()!=null && e.getItem().getType()==Material.OAK_PLANKS)
            observe("interact",e.getAction(),e.getBlockFace(),e.useInteractedBlock(),e.useItemInHand());
    }
    @EventHandler(priority=EventPriority.LOWEST) public void click(InventoryClickEvent e) {
        if (e.getRawSlot()!=0) return;
        var item=e.getCurrentItem();
        observe("click.lowest",e.getClick(),e.getAction(),item==null ? "empty" : item.getType(),e.isCancelled());
        e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void skipped(InventoryClickEvent e) {
        if (e.getRawSlot()==0) observe("click.highest",e.isCancelled());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void monitor(InventoryClickEvent e) {
        if (e.getRawSlot()==0) observe("click.monitor",e.isCancelled());
    }
    @EventHandler public void damage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) observe("damage",e.getCause(),e.getDamage(),p.getHealth());
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if (!(sender instanceof Player p) || args.length!=1) return false;
        if (args[0].equals("stack")) {
            var inv=p.getInventory();
            var held=inv.getItemInMainHand();
            var clone=held.clone();
            int before=held.getAmount();
            clone.setAmount(1);
            int afterClone=inv.getItemInMainHand().getAmount();
            held.setAmount(4);
            observe("hand",before,afterClone,inv.getItemInMainHand().getAmount());
            inv.getItem(1).setAmount(3);
            observe("slot",inv.getItem(1).getAmount());
        } else if (args[0].equals("world")) {
            var world=p.getWorld();
            int count=0;
            for (var face : java.util.List.of(org.bukkit.block.BlockFace.UP,org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.WEST,org.bukkit.block.BlockFace.EAST)) {
                if (world.getBlockAt(8,80,8).getRelative(face).getType()==Material.OAK_PLANKS) count++;
            }
            observe("world",count,p.getInventory().getItem(2).getAmount());
        } else return false;
        return true;
    }
}
