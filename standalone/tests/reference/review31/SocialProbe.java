package qa.unity31;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.atomic.AtomicInteger;

public final class SocialProbe extends JavaPlugin implements Listener {
    private volatile boolean hold;
    private final AtomicInteger marks=new AtomicInteger();
    public void onEnable(){getServer().getPluginManager().registerEvents(this,this);}
    void mark(String event,Object value){getLogger().info("U31 "+event+" "+value);}
    private static String text(AsyncChatEvent e){return PlainTextComponentSerializer.plainText().serialize(e.message());}
    @EventHandler(priority=EventPriority.LOWEST)
    public void legacy(AsyncPlayerChatEvent e){if(e.getMessage().startsWith("u31"))mark("legacy-"+e.getMessage(),e.isAsynchronous());}
    @EventHandler(priority=EventPriority.LOWEST)
    public void early(AsyncChatEvent e){if(text(e).startsWith("u31"))mark("early-"+text(e),e.isCancelled());}
    @EventHandler(priority=EventPriority.HIGH)
    public void chat(AsyncChatEvent e){if(text(e).equals("u31cancelchat")){e.setCancelled(true);mark("cancel-chat",e.isAsynchronous());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void skip(AsyncChatEvent e){if(text(e).startsWith("u31"))mark("uncancelled-"+text(e),true);}
    @EventHandler(priority=EventPriority.MONITOR)
    public void watch(AsyncChatEvent e){if(text(e).startsWith("u31"))mark("monitor-"+text(e),e.isCancelled());}
    @EventHandler(priority=EventPriority.LOWEST)
    public void command(PlayerCommandPreprocessEvent e){
        if(e.getMessage().equals("/u31alias"))e.setMessage("/u31permit");
        if(e.getMessage().startsWith("/u31"))mark("cmd-low",e.getMessage());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void commandMonitor(PlayerCommandPreprocessEvent e){if(e.getMessage().startsWith("/u31"))mark("cmd-"+e.getMessage().substring(1),e.isCancelled());}
    @EventHandler(priority=EventPriority.HIGH)
    public void move(PlayerMoveEvent e){if(hold && !(e instanceof PlayerTeleportEvent) && e.getPlayer().getName().equals("VolumeA") && e.hasChangedPosition()){e.setCancelled(true);mark("move-cancel",true);}}
    @EventHandler(priority=EventPriority.HIGH)
    public void teleport(PlayerTeleportEvent e){
        if(e.getPlayer().getName().equals("VolumeA") && e.getTo().getBlockX()==70){mark("tp-cause",e.getCause());e.setCancelled(true);}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void teleportMonitor(PlayerTeleportEvent e){if(e.getTo()!=null && e.getTo().getBlockX()==70)mark("tp-monitor",e.isCancelled());
        if(e.getTo()!=null && e.getTo().getBlockX()==74)mark("region-tp",e.isCancelled());}
    @Override public boolean onCommand(CommandSender s,Command cmd,String name,String[] args){
        if(name.equals("u31hold") && s instanceof ConsoleCommandSender){hold=Boolean.parseBoolean(args[0]);mark("hold",hold);}
        else if(name.equals("u31status"))mark("marks",marks.get());
        else if(s instanceof Player p && name.equals("u31tp")){
            var at=p.getLocation();at.setX(70.5);mark("plugin-teleport",p.teleport(at));
        }else if(name.equals("u31permit") || name.equals("u31forbid"))mark("executed-"+name,marks.incrementAndGet());
        return true;
    }
}
