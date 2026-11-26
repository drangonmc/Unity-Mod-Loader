package qa.inventory;

import java.util.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryAudit extends JavaPlugin implements Listener {
    private final Map<Event,List<String>> order = new IdentityHashMap<>();
    public void onEnable() {
        if (InventoryType.CHEST.getMenuType() == null)
            throw new AssertionError("Chest menu registry not initialized");
        var manager = getServer().getPluginManager();
        if (manager.getPlugin("Inventory.Alias") != manager.getPlugin("Unity.Types"))
            throw new AssertionError("provider/alias lookup");
        for (var event : List.of(InventoryClickEvent.class,InventoryDragEvent.class,EntityDamageEvent.class)) {
            manager.registerEvent(event,this,EventPriority.LOWEST,(l,e)->{
                if (e instanceof InventoryClickEvent c && c.getCurrentItem()!=null && !c.getCurrentItem().isEmpty()) {
                    var item=c.getCurrentItem();
                    if (item.getType()==Material.DRAGON_EGG && (item.hasItemMeta() || item.getItemMeta()==null || item.getItemMeta().hasDisplayName()))
                        throw new AssertionError("plain item metadata");
                }
                if (e instanceof EntityDamageEvent damage && damage.getDamageSource().getDamageType().getKey()==null)
                    throw new AssertionError("Missing damage registry key");
                order.put(e,new ArrayList<>(List.of("LOWEST="+((Cancellable)e).isCancelled())));
            },this,false);
            manager.registerEvent(event,this,EventPriority.HIGHEST,(l,e)->order.get(e).add("HIGHEST"),this,true);
            manager.registerEvent(event,this,EventPriority.MONITOR,(l,e)->{
                var steps=order.remove(e);
                boolean denied=((Cancellable)e).isCancelled();
                steps.add("MONITOR="+denied);
                if (!steps.equals(denied ? List.of("LOWEST=false","MONITOR=true") : List.of("LOWEST=false","HIGHEST","MONITOR=false")))
                    throw new AssertionError(steps);
                String detail=e instanceof InventoryClickEvent c ? c.getRawSlot()+"/"+c.getClick() :
                    e instanceof InventoryDragEvent d ? new TreeSet<>(d.getRawSlots())+"/"+d.getOldCursor().getType() :
                    ((EntityDamageEvent)e).getCause().name();
                getLogger().info("INVENTORY24 "+e.getClass().getSimpleName()+" "+detail+" "+String.join(" ",steps));
            },this,false);
        }
        manager.registerEvents(this,this);
    }
    @EventHandler public void level(PlayerLevelChangeEvent e) {
        getLogger().info("INVENTORY24 LEVEL "+e.getPlayer().getName()+" "+e.getOldLevel()+" -> "+e.getNewLevel());
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if (args.length == 1) {
            var cursor=Bukkit.getPlayerExact(args[0]).getOpenInventory().getCursor();
            getLogger().info("INVENTORY24 CURSOR "+args[0]+" "+cursor.getType()+" "+cursor.getAmount());
            return true;
        }
        var world=Bukkit.getWorlds().getFirst();
        if (world.isChunkLoaded(85,85) || !world.loadChunk(85,85,false) ||
            world.getBlockAt(1360,201,1360).getType()!=Material.EMERALD_BLOCK ||
            world.loadChunk(8180,-8180,false) || world.isChunkLoaded(8180,-8180))
            throw new AssertionError("Read existing chunk without generating absent chunk");
        getLogger().info("INVENTORY24 DISK LOAD PASS"); return true;
    }
}
