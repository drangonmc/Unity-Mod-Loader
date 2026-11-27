package qa.review28;
import mc.unity.api.UnityMod;
import mc.unity.core.Actions;
import net.minecraft.server.MinecraftServer;

public final class DropProbe implements UnityMod {
    private Runnable detach;
    private Runnable tick;
    private Actions.Action pending;
    public void serverStarted(MinecraftServer server) {
        tick=mc.unity.core.Events.SERVER_TICK_POST.listenScoped((s,time)->{
            if(pending==null)return;
            var action=pending; pending=null;
            var box=new net.minecraft.world.phys.AABB(action.pos).inflate(4);
            var orbs=action.level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,box);
            System.out.println("UNITY28_HARVEST orbs="+orbs.size()+" tool="+action.player.getMainHandItem()
                +" correct="+action.player.hasCorrectToolForDrops(action.state)+" creative="+action.player.isCreative());
        });
        detach=Actions.attachScoped(new Actions.Adapter() {
            public boolean handles(Actions.Kind k){return k==Actions.Kind.BREAK;}
            public void accept(Actions.Action action){
                if(action.pos.getX()==41 && action.pos.getY()==80 && action.pos.getZ()==8) {
                    pending=action;
                    action.experience=null;
                    action.dropItems=false;
                    System.out.println("UNITY28_DROP_ONLY no XP override");
                }
            }
        });
    }
    public void serverStopping(MinecraftServer server){if(detach!=null)detach.run();if(tick!=null)tick.run();}
}
