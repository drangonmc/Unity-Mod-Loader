package qa.unity33;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.FlagPermissions.FlagState;
import com.bekvon.bukkit.residence.listeners.ResidenceBlockListener;

public final class RuleProbe extends JavaPlugin implements org.bukkit.event.Listener {
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this,this);
        var type=org.bukkit.Registry.ENTITY_TYPE.get(new org.bukkit.NamespacedKey("logisticsdrones","stone_drone"));
        if(type!=null){
            var edit=BukkitAdapter.adapt(type);
            getLogger().info("U33 ENTITY REGISTRY "+type.getKey()+" worldedit="+(edit==null?"null":edit.id()));
        }
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
    public void spawning(org.bukkit.event.entity.CreatureSpawnEvent event){
        var kind=event.getEntityType();
        if(kind!=org.bukkit.entity.EntityType.UNKNOWN && kind.getKey().getNamespace().equals("logisticsdrones")){
            var edit=BukkitAdapter.adapt(kind);
            getLogger().info("U33 DRONE TYPE "+kind.getKey()+" worldedit="+(edit==null?"null":edit.id())+" cancelled="+event.isCancelled());
        }
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.HIGH)
    public void portal(org.bukkit.event.player.PlayerPortalEvent event) {
        var from=event.getFrom();
        if(event.getPlayer().getName().equals("VolumeA") && from.getBlockX()==56 && from.getBlockZ()==21){
            event.setCancelled(true);getLogger().info("U33 PORTAL cancelled "+event.getCause());
        }
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
    public void interaction(org.bukkit.event.player.PlayerInteractEvent event) {
        var block=event.getClickedBlock();
        if(block!=null && block.getType()==Material.CHEST && event.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)
            getLogger().info("U33 CHEST "+event.getPlayer().getName()+" "+block.getX()+" "+event.useInteractedBlock());
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String name,String[] args){
        try {return run(sender,args);}
        catch(RuntimeException | LinkageError failure) { getLogger().log(java.util.logging.Level.SEVERE,"U33 region setup or query failed",failure);throw failure; }
    }
    private boolean run(CommandSender sender,String[] args){
        if(!(sender instanceof ConsoleCommandSender))return false;
        var a=Objects.requireNonNull(Bukkit.getPlayerExact("VolumeA"));var b=Objects.requireNonNull(Bukkit.getPlayerExact("VolumeB"));var world=a.getWorld();
        if(args[0].equals("audit")){
            var stack=new org.bukkit.inventory.ItemStack(Material.DIAMOND_PICKAXE);
            var meta=stack.getItemMeta();meta.setLore(List.of("one","two"));
            var text=meta.getLore();text.add("three");
            var rich=meta.lore();rich.clear();
            if(!meta.getLore().equals(List.of("one","two")))throw new IllegalStateException("Lore must be a detached mutable copy");
            var enchant=org.bukkit.enchantments.Enchantment.UNBREAKING;
            if(!meta.addEnchant(enchant,2,false))throw new IllegalStateException("Valid enchantment rejected");
            boolean exceptional=meta.addEnchant(enchant,0,true);int remaining=meta.getEnchantLevel(enchant);
            var typed=world.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY);
            String legacy;
            try {legacy=world.getGameRuleValue("keepInventory");} catch(IllegalArgumentException unknown){legacy="unrecognized";}
            String current=world.getGameRuleValue("minecraft:keep_inventory");
            if(!String.valueOf(typed).equals(current) || !legacy.equals("unrecognized") && !legacy.equals(current))throw new IllegalStateException("Legacy rule '"+legacy+"' versus "+typed);
            getLogger().info("U33 AUDIT lore=ok rule="+current+" legacy="+legacy+" nonpositive="+exceptional+" remaining="+remaining+" uuid="+world.getUID());
            return true;
        }
        var api=WorldGuard.getInstance().getPlatform().getRegionContainer();var manager=Objects.requireNonNull(api.get(BukkitAdapter.adapt(world)));
        var res=Residence.getInstance();
        if(args[0].equals("setup")){
            for(int x:new int[]{66,68,70}){
                var region=new ProtectedCuboidRegion("u33wg"+x,BlockVector3.at(x,72,14),BlockVector3.at(x,88,42));
                region.getOwners().addPlayer(x==66?b.getUniqueId():a.getUniqueId());manager.addRegion(region);
            }
            try{manager.save();}catch(Exception ex){throw new IllegalStateException(ex);}
            var from=new Location(world,67,72,14);var to=new Location(world,68,88,42);
            if(res.getResidenceManager().getByLoc(from)==null && !res.getResidenceManager().addResidence(b,"Unity33",from,to,true))throw new IllegalStateException("Residence creation failed");
            var claim=Objects.requireNonNull(res.getResidenceManager().getByLoc(from));
            for(var flag:List.of("build","destroy","container")){
                claim.getPermissions().setFlag(flag,FlagState.FALSE);
                claim.getPermissions().setPlayerFlag(b.getUniqueId(),flag,FlagState.TRUE);
            }
            getLogger().info("U33 REGIONS SAVED");
        }
        for(Player player:List.of(a,b)){
            var subject=WorldGuardPlugin.inst().wrapPlayer(player);
            for(int x=65;x<=70;x++){
                var at=new Location(world,x,80,18);
                boolean w=api.createQuery().testBuild(BukkitAdapter.adapt(at),subject,Flags.BLOCK_BREAK);
                boolean wc=api.createQuery().testBuild(BukkitAdapter.adapt(at),subject,Flags.CHEST_ACCESS);
                boolean r=ResidenceBlockListener.canBreakBlock(player,at.getBlock(),false);
                var perms=res.getPermsByLocForPlayer(at,player);var container=com.bekvon.bukkit.residence.containers.Flags.container;
                boolean rc=perms.playerHas(player,container,perms.playerHas(player,com.bekvon.bukkit.residence.containers.Flags.use,true));
                getLogger().info("U33 RULE "+player.getName()+" "+x+" "+w+" "+r+" "+wc+" "+rc);
            }
        }
        getLogger().info("U33 RULES END");return true;
    }
}
