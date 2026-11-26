package qa.audit;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class HostAuditProbe extends JavaPlugin implements Listener {
    public static class Signal extends Event {
        private static final HandlerList handlers=new HandlerList();
        public Signal(){super(true);}
        public HandlerList getHandlers(){return handlers;}
        public static HandlerList getHandlerList(){return handlers;}
    }
    public static final class Other extends Signal {}
    final AtomicInteger calls=new AtomicInteger();
    @EventHandler public void receive(Signal signal){calls.incrementAndGet();}
    public void onEnable(){
        getServer().getPluginManager().registerEvents(this,this);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var type=getClassLoader().loadClass("mc.unity.plugins.Host");
            var current=type.getDeclaredMethod("active");current.setAccessible(true);
            var fire=type.getDeclaredMethod("fire",Event.class);fire.setAccessible(true);
            var target=current.invoke(null);
            var start=new CountDownLatch(1);
            var jobs=new ArrayList<Future<?>>();
            for(int i=0;i<32;i++)jobs.add(pool.submit(()->{
                start.await();
                for(int n=0;n<8;n++)fire.invoke(target,n%2==0?new Signal():new Other());
                return null;
            }));
            start.countDown();
            for(var job:jobs)job.get(5,TimeUnit.SECONDS);
            if(calls.get()!=256)throw new AssertionError(calls.get());
            getLogger().info("QA24 WORKER DISPATCH callbacks="+calls.get());
        }catch(Exception failure){throw new AssertionError(failure);}
    }
}
