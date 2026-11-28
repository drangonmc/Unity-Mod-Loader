package qa;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.*;
import org.bukkit.command.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import net.milkbowl.vault.permission.Permission;
import net.milkbowl.vault.economy.Economy;
import java.util.*;

public class PluginRoundTrip extends JavaPlugin {
    public void onEnable(){
        try {
            var f=Class.forName("org.apache.commons.text.StringSubstitutor",true,getClassLoader());
            var o=f.getConstructor(Map.class).newInstance(Map.of("name","unity"));
            if(!"unity".equals(f.getMethod("replace",String.class).invoke(o,"${name}")))throw new AssertionError("transitive library resolution");
        } catch(ReflectiveOperationException ex){throw new IllegalStateException("plugin.yml libraries",ex);}
    }
    public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        try {return test(sender,args);}
        catch(RuntimeException|Error e){getLogger().log(java.util.logging.Level.SEVERE,"UNITY29_ACTUAL_FAILURE",e);throw e;}
    }
    private boolean test(CommandSender sender,String[] args) {
        if(!(sender instanceof Player p) || args.length!=1)return false;
        var services=getServer().getServicesManager();
        var permission=Objects.requireNonNull(services.load(Permission.class),"Vault permission provider");
        var economy=Objects.requireNonNull(services.load(Economy.class),"Vault economy provider");
        boolean leader=p.getName().equals("VolumeA");
        boolean allowed=p.hasPermission("unity29.confirm");
        if(allowed!=leader || permission.playerHas(p,"unity29.confirm")!=leader)
            throw new AssertionError("permission mismatch "+p.getName()+" actual="+allowed+" via "+permission.getName());
        if(!permission.getName().equalsIgnoreCase("LuckPerms") || !economy.getName().toLowerCase(Locale.ROOT).contains("essentials"))
            throw new AssertionError("Unexpected service provider");
        var key=new NamespacedKey(this,"token");
        int value=leader?291:292;
        if(args[0].equals("write")){
            var attachment=p.addAttachment(this,"unity29.temporary",true);
            if(!p.hasPermission("unity29.temporary"))throw new AssertionError("attachment add");
            p.removeAttachment(attachment);
            if(p.hasPermission("unity29.temporary"))throw new AssertionError("attachment removal");
            p.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,value);
            try{
                var bytes=p.getPersistentDataContainer().serializeToBytes();
                if(bytes.length<4 || bytes[0]!=10)throw new AssertionError("Unexpected PDC NBT framing");
                var copied=p.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
                copied.readFromBytes(bytes);
                if(!Objects.equals(value,copied.get(key,PersistentDataType.INTEGER)))throw new AssertionError("PDC bytes");
                var fixture=copied.getAdapterContext().newPersistentDataContainer();
                var nested=PersistentDataType.LIST.listTypeFrom(PersistentDataType.LIST.integers());
                var nestedKey=new NamespacedKey(this,"cross_host_fixture");
                var contents=List.of(List.of(17,29),List.of(3));
                fixture.set(nestedKey,nested,contents);
                var wire=fixture.serializeToBytes();
                copied.readFromBytes(wire,true);
                if (!contents.equals(copied.get(nestedKey,nested))) throw new AssertionError("cross-host list encoding");
                java.nio.file.Files.createDirectories(getDataFolder().toPath());
                java.nio.file.Files.write(getDataFolder().toPath().resolve("pdc-"+p.getName()+".nbt"),wire);
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
            var stack=new ItemStack(Material.PAPER,leader?3:5);
            var meta=stack.getItemMeta();meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,"tag-"+value);stack.setItemMeta(meta);
            p.getInventory().setItem(4,stack);
            p.setHealth(leader?17:15);p.setFoodLevel(leader?13:14);
            p.setAllowFlight(true);p.setFlying(true);
            if(!p.getAllowFlight() || !p.isFlying())throw new AssertionError("flight");
            p.setFlying(false);p.setAllowFlight(false);
            p.playSound(p.getLocation(),"minecraft:block.note_block.pling",org.bukkit.SoundCategory.PLAYERS,0.5f,1f,29L);
            var inv=getServer().createInventory(p,9,"Unity transfer audit");
            inv.setItem(2,new ItemStack(Material.GOLD_INGOT,2));
            var view=p.openInventory(inv);
            if(view==null || view.getTopInventory().getItem(2).getAmount()!=2)throw new AssertionError("custom chest contents");
            if(!economy.depositPlayer(p,value).transactionSuccess())throw new AssertionError("deposit");
        }
        var stack=p.getInventory().getItem(4);
        if(!Objects.equals(p.getPersistentDataContainer().get(key,PersistentDataType.INTEGER),value) ||
            stack==null || !Objects.equals(stack.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING),"tag-"+value))
            throw new AssertionError("PDC roundtrip");
        if(Double.compare(economy.getBalance(p),value)!=0 || p.getHealth()!=(leader?17.0:15.0) || p.getFoodLevel()!=(leader?13:14))
            throw new AssertionError("Player state "+p.getName()+" balance="+economy.getBalance(p)+" health="+p.getHealth()+" food="+p.getFoodLevel());
        getLogger().info("UNITY29 "+args[0]+" "+p.getName()+" permission="+permission.getName()+" economy="+economy.getName()+" balance="+economy.getBalance(p)+" pdc="+value);
        return true;
    }
}
