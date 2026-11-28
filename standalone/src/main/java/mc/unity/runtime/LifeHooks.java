package mc.unity.runtime;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.portal.TeleportTransition;

public final class LifeHooks {
    private static final ThreadLocal<String> closeReason=new ThreadLocal<>();
    public static boolean dying(ServerPlayer p,DamageSource source){
        var host=ServerHooks.pluginHost();return host!=null && host.playerDying(p,source);
    }
    public static boolean equipment(net.minecraft.world.entity.player.Player p){
        var host=ServerHooks.pluginHost();return host!=null && p instanceof ServerPlayer sp && host.deathEquipment(sp);
    }
    public static boolean experience(LivingEntity entity,ServerLevel world,Entity killer){
        var host=ServerHooks.pluginHost();
        if(host==null || !(entity instanceof ServerPlayer p))return false;
        int award=host.deathXp(p,-1);if(award<0)return false;
        if(award>0)ExperienceOrb.award(world,p.position(),award);
        return true;
    }
    public static Component message(Component original,ServerPlayer p){
        var host=ServerHooks.pluginHost();return host==null?original:host.deathMessage(p,original);
    }
    public static boolean show(boolean value,ServerPlayer p){
        var host=ServerHooks.pluginHost();return host==null?value:host.showDeath(p,value);
    }
    public static TeleportTransition respawn(TeleportTransition t,ServerPlayer old,boolean alive){
        var host=ServerHooks.pluginHost();return host==null?t:host.respawning(old,t,alive);
    }
    public static void restored(ServerPlayer p,ServerPlayer old,boolean alive){
        var host=ServerHooks.pluginHost();if(host!=null)host.restored(p,old,alive);
    }
    public static boolean open(AbstractContainerMenu menu,ServerPlayer p,MenuProvider provider){
        var host=ServerHooks.pluginHost();if(menu==null || host==null)return true;
        var current=p.containerMenu;
        if(host.menuOpen(p,menu,provider) && current==p.containerMenu)return true;
        menu.removed(p);
        return false;
    }
    private static final ThreadLocal<Set<AbstractContainerMenu>> notifying=ThreadLocal.withInitial(()->Collections.newSetFromMap(new IdentityHashMap<>()));
    public static boolean closing(ServerPlayer p){
        var host=ServerHooks.pluginHost();
        var original=p.containerMenu;
        if(host==null || original==p.inventoryMenu)return true;
        var active=notifying.get();
        if(!active.add(original))return true;
        try{host.menuClose(p,closeReason.get()==null?reason():closeReason.get());return p.containerMenu==original;}
        finally{active.remove(original);if(active.isEmpty())notifying.remove();}
    }
    private static String reason(){
        return StackWalker.getInstance().walk(frames->frames.limit(16).map(frame->switch(frame.getMethodName()){
            case "openMenu" -> "OPEN_NEW";case "handleContainerClose" -> "PLAYER";
            case "die" -> "DEATH";case "teleport" -> "TELEPORT";case "onDisconnect","remove" -> "DISCONNECT";
            default -> "";
        }).filter(s->!s.isEmpty()).findFirst().orElse("UNKNOWN"));
    }
    public static void close(ServerPlayer p,String reason,boolean notify){
        var previous=closeReason.get();closeReason.set(reason);
        try{if(notify)p.closeContainer();else p.doCloseContainer();}
        finally{if(previous==null)closeReason.remove();else closeReason.set(previous);}
    }
}
