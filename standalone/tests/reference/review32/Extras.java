package qa.unity32;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import java.util.List;

final class Extras implements Listener,TabExecutor {
    private final LifeProbe plugin;
    Extras(LifeProbe plugin){this.plugin=plugin;}
    public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] args){return List.of("unity32-completion");}
    public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!(sender instanceof Player p))return false;var w=p.getWorld();
        var block=w.getBlockAt(71,80,23);block.setType(Material.OAK_STAIRS,false);
        block.setBlockData(Bukkit.createBlockData("minecraft:oak_stairs[facing=east]"),false);
        if(!block.getBlockData().getAsString().contains("facing=east"))throw new AssertionError("block data");
        var reward=new ItemStack(Material.IRON_PICKAXE);
        var meta=reward.getItemMeta();
        meta.lore(List.of(Component.text("Unity32")));
        if(!meta.addEnchant(Enchantment.UNBREAKING,3,false))throw new AssertionError("add enchant");
        reward.setItemMeta(meta);
        var item=w.dropItem(new Location(w,72.5,80,25.5),reward);
        var back=item.getItemStack().getItemMeta();
        if(back.getEnchantLevel(Enchantment.UNBREAKING)!=3 || !back.lore().getFirst().equals(Component.text("Unity32")))throw new AssertionError("item metadata");
        var spawn=w.spawnEntity(new Location(w,73.5,80,25.5),EntityType.COW);
        if(spawn.isValid())throw new AssertionError("cancelled cow joined world");
        var allowed=w.spawnEntity(new Location(w,72.5,80,28.5),EntityType.COW);
        if(!allowed.isValid())throw new AssertionError("allowed cow missing");
        w.getBlockAt(74,80,23).setType(Material.DIRT,false);
        w.createExplosion(new Location(w,74.5,80.5,23.5),2f,false,true);
        if(w.getBlockAt(74,80,23).getType()!=Material.DIRT)throw new AssertionError("cancelled block explosion");
        plugin.mark("world-meta ok");return true;
    }
    @EventHandler public void spawn(CreatureSpawnEvent e){
        if(e.getEntityType()!=EntityType.COW || e.getLocation().getBlockZ()!=25 || e.getLocation().getBlockX()!=73)return;
        e.setCancelled(true);plugin.mark("cow denied "+e.getSpawnReason());
    }
    @EventHandler public void blockBlast(BlockExplodeEvent e){if(e.getBlock().getX()==74){e.setCancelled(true);plugin.mark("block-blast denied");}}
    @EventHandler public void entityBlast(EntityExplodeEvent e){if(e.getLocation().getBlockX()==74){e.blockList().clear();e.setYield(0);plugin.mark("entity-blast filtered");}}
}
