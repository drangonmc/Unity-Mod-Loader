package qa.unity30;

import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginProbe extends JavaPlugin implements Listener {
    public void onEnable() { getServer().getPluginManager().registerEvents(this,this); }
    @Override public boolean onCommand(org.bukkit.command.CommandSender from,org.bukkit.command.Command cmd,String label,String[] args) {
        if (!(from instanceof org.bukkit.command.ConsoleCommandSender)) return false;
        var p=java.util.Objects.requireNonNull(getServer().getPlayerExact("VolumeB"));
        p.setHealth(15);
        var ctx=p.getPersistentDataContainer().getAdapterContext();
        var c=ctx.newPersistentDataContainer();
        var t=org.bukkit.persistence.PersistentDataType.LIST;
        var k=new org.bukkit.NamespacedKey(this,"nested");
        c.set(k,t.listTypeFrom(t.integers()),java.util.List.of(java.util.List.of(4,7)));
        if (!c.has(k,t.listTypeFrom(t.integers())) || c.has(k,t.listTypeFrom(t.strings()))) throw new AssertionError("PDC nested element types");
        try {
            var raw=c.serializeToBytes(); if(raw[0]!=10)throw new AssertionError("Paper raw NBT");
            var zipped=new java.io.ByteArrayOutputStream();
            try(var gzip=new java.util.zip.GZIPOutputStream(zipped)){gzip.write(raw);}
            for(byte[] bytes:new byte[][]{raw,zipped.toByteArray()}) {
                var copy=ctx.newPersistentDataContainer();copy.readFromBytes(bytes);
                if (!java.util.List.of(java.util.List.of(4,7)).equals(copy.get(k,t.listTypeFrom(t.integers())))) throw new AssertionError("PDC read");
            }
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
        getLogger().info("UNITY30 restored-and-pdc");return true;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
    public void broken(BlockBreakEvent event) {
        var pos=event.getBlock();
        if (pos.getZ()==20 && pos.getY()==80 && pos.getX()>=46 && pos.getX()<=52)
            getLogger().info("UNITY30 bukkit-break-"+pos.getX()+" "+event.isCancelled());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
    public void placed(BlockPlaceEvent event) {
        var pos=event.getBlockPlaced();
        if (pos.getZ()==20 && pos.getY()==80 && pos.getX()>=46 && pos.getX()<=52)
            getLogger().info("UNITY30 bukkit-place-"+pos.getX()+" "+event.isCancelled());
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void hurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player p && p.getName().equals("VolumeB") &&
            event.getDamage()==7 && p.getLocation().getBlockZ()>=17) {
            event.setCancelled(true); getLogger().info("UNITY30 bukkit-damage-deny true");
        }
    }
}
