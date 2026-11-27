package qa.review28;
import org.bukkit.Material;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
public final class BukkitProbe extends JavaPlugin implements Listener {
    public void onEnable() {
        if(org.yaml.snakeyaml.Yaml.class.getClassLoader()!=org.bukkit.Bukkit.class.getClassLoader())
            throw new AssertionError("Wrong SnakeYAML domain");
        var item=new ItemStack(Material.IRON_INGOT);
        var meta=item.getItemMeta();meta.setDisplayName("§c红§l字");
        if(!item.setItemMeta(meta) || !item.getItemMeta().getDisplayName().equals("§c红§l字"))throw new AssertionError("legacy name");
        getServer().getPluginManager().registerEvents(this,this);
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=false)
    public void inspect(BlockBreakEvent e) {
        var b=e.getBlock();
        if(b.getY()!=80 || b.getZ()!=8 || (b.getX()!=35 && b.getX()!=37))return;
        if(!e.isCancelled())throw new AssertionError("Lost Forge cancellation");
        if(b.getX()==37){e.setCancelled(false);e.setDropItems(false);}
        getLogger().info("UNITY28_BUKKIT_BREAK "+b.getX()+" "+e.isCancelled());
    }
}
