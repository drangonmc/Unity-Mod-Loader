package qa.unity34;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;
public class PluginCheck extends JavaPlugin implements org.bukkit.event.Listener {
    private String mode="";
    @Override public void onEnable(){getServer().getPluginManager().registerEvents(this,this);}
    @org.bukkit.event.EventHandler public void portal(org.bukkit.event.player.PlayerPortalEvent event){
        if(mode.isEmpty() || event.getCause()!=org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL)return;
        if(mode.equals("cancel"))event.setCancelled(true);
        if(mode.equals("forbid"))event.setCanCreatePortal(false);
        if(mode.equals("redirect"))event.setTo(new Location(event.getFrom().getWorld(),28.5,80,8.5));
        getLogger().info("U34 PORTAL "+mode);
    }
    private static <T extends Enum<T>> int ordinal(T t){return t.ordinal();}
    private static boolean legacy(Material m){return switch(m){case LEGACY_STONE -> true; default -> false;};}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        try{
            if(args.length==1){
                mode=args[0];Bukkit.getPlayerExact("VolumeA").setPortalCooldown(0);
                getLogger().info("U34 PORTAL MODE "+mode);return true;
            }
            var mod=Arrays.stream(Material.values()).filter(m->!m.getKey().getNamespace().equals("minecraft")).findFirst().orElseThrow();
            if(mod.ordinal()!=ordinal(mod)||Material.values()[ordinal(mod)]!=mod || Material.class.getEnumConstants()[mod.ordinal()]!=mod || legacy(mod) || !legacy(Material.LEGACY_STONE))throw new AssertionError("Material ordinal");
            var hive=Material.BEEHIVE.createBlockData();if(!hive.equals(hive.clone())||hive.hashCode()!=hive.clone().hashCode())throw new AssertionError("Beehive equality");
            var stack=new ItemStack(Material.STONE);var meta=stack.getItemMeta();var model=meta.getCustomModelDataComponent();
            model.setStrings(List.of("marker"));meta.setCustomModelDataComponent(model);
            if(meta.hasCustomModelData() || !meta.hasCustomModelDataComponent())throw new AssertionError("Custom model data");
            var profile=Bukkit.createProfile(UUID.randomUUID(),"TextureTest");
            var url=java.net.URI.create("https://textures.minecraft.net/texture/1234").toURL();
            profile.getTextures().setSkin(url);
            if(!url.toString().equals(profile.getTextures().getSkin().toString()))throw new AssertionError("Profile skin");
            var a=Bukkit.getPlayerExact("VolumeA");var b=Bukkit.getPlayerExact("VolumeB");
            a.hideEntity(this,b);if(a.canSee(b))throw new AssertionError("hide");a.showEntity(this,b);if(!a.canSee(b))throw new AssertionError("show");
            getLogger().info("U34 BUKKIT PASS "+mod.getKey());return true;
        }catch(Throwable failure){getLogger().log(java.util.logging.Level.SEVERE,"U34 check failed",failure);throw new IllegalStateException(failure);}
    }
}
