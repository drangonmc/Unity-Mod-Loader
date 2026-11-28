package mc.unity.plugins;
import java.util.function.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import io.papermc.paper.threadedregions.scheduler.*;
final class PlayerScheduler implements EntityScheduler {
    private final BooleanSupplier alive;
    PlayerScheduler(BooleanSupplier alive){this.alive=alive;}
    public boolean execute(Plugin p,Runnable action,Runnable retired,long delay){return schedule(p,t->action.run(),retired,Math.max(1,delay),0)!=null;}
    public ScheduledTask run(Plugin p,Consumer<ScheduledTask> action,Runnable retired){return schedule(p,action,retired,1,0);}
    public ScheduledTask runDelayed(Plugin p,Consumer<ScheduledTask> action,Runnable retired,long delay){return schedule(p,action,retired,delay,0);}
    public ScheduledTask runAtFixedRate(Plugin p,Consumer<ScheduledTask> action,Runnable retired,long delay,long period){
        if(period<1)throw new IllegalArgumentException("period");return schedule(p,action,retired,delay,period);
    }
    private ScheduledTask schedule(Plugin p,Consumer<ScheduledTask> action,Runnable retired,long delay,long period){
        if(delay<1)throw new IllegalArgumentException("delay");if(!alive.getAsBoolean())return null;
        class Entry implements ScheduledTask,Runnable {
            BukkitTask task;volatile ExecutionState state=ExecutionState.IDLE;
            public Plugin getOwningPlugin(){return p;} public boolean isRepeatingTask(){return period!=0;}
            public ExecutionState getExecutionState(){return state;}
            public synchronized CancelledState cancel(){
                switch(state){
                case FINISHED:return CancelledState.ALREADY_EXECUTED;
                case CANCELLED:return CancelledState.CANCELLED_ALREADY;
                case CANCELLED_RUNNING:return CancelledState.NEXT_RUNS_CANCELLED_ALREADY;
                case RUNNING:if(period==0)return CancelledState.RUNNING;
                    state=ExecutionState.CANCELLED_RUNNING;task.cancel();return CancelledState.NEXT_RUNS_CANCELLED;
                default:state=ExecutionState.CANCELLED;task.cancel();return CancelledState.CANCELLED_BY_CALLER;
                }
            }
            public void run(){
                synchronized(this){if(state!=ExecutionState.IDLE)return;state=ExecutionState.RUNNING;}
                boolean live=alive.getAsBoolean();
                try{if(live)action.accept(this);else if(retired!=null)retired.run();}
                finally{synchronized(this){
                    if(!live || state==ExecutionState.CANCELLED_RUNNING){task.cancel();state=ExecutionState.CANCELLED;}
                    else state=period==0?ExecutionState.FINISHED:ExecutionState.IDLE;
                }}
            }
        }
        var entry=new Entry();
        synchronized(entry){var scheduler=org.bukkit.Bukkit.getScheduler();entry.task=period==0?scheduler.runTaskLater(p,entry,delay):scheduler.runTaskTimer(p,entry,delay,period);}
        return entry;
    }
}
