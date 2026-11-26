package qa.reference;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

public final class CapabilityProbe extends JavaPlugin implements Listener {
    public static final class Status extends Event implements Cancellable {
        private static final HandlerList HANDLERS=new HandlerList();
        private boolean cancelled;
        public HandlerList getHandlers(){return HANDLERS;}
        public static HandlerList getHandlerList(){return HANDLERS;}
        public boolean isCancelled(){return cancelled;}
        public void setCancelled(boolean value){cancelled=value;}
    }
    public void onEnable(){
        var manager=Bukkit.getPluginManager();
        for(int i=0;i<2;i++) {
            try { manager.registerEvent(NotePlayEvent.class,this,EventPriority.NORMAL,(l,e)->{},this,false); }
            catch(IllegalPluginAccessException rejected){getLogger().info("REFERENCE registration rejected");}
        }
        manager.registerEvent(Status.class,this,EventPriority.NORMAL,(l,e)->Bukkit.getServer().getViewDistance(),this,false);
        var status=new Status();
        manager.callEvent(status);
        if(!status.isCancelled())throw new AssertionError("Missing capability was not reported in result");
        getLogger().info("REFERENCE synthetic status cancelled");
    }
}
