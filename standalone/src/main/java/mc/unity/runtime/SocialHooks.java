package mc.unity.runtime;

import com.mojang.brigadier.ParseResults;
import java.util.Set;
import net.minecraft.commands.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.portal.TeleportTransition;

public final class SocialHooks {
    private static final ThreadLocal<String> cause=new ThreadLocal<>();
    public static void chat(PlayerList list,PlayerChatMessage message,ServerPlayer sender,ChatType.Bound bound) {
        var host=ServerHooks.pluginHost();
        if(host==null || !host.chat(sender,message,bound)) list.broadcastChatMessage(message,sender,bound);
    }
    public static void command(Commands commands,ParseResults<CommandSourceStack> parsed,String original,ServerPlayer player) {
        var host=ServerHooks.pluginHost();
        String result=host==null?original:host.command(player,original);
        if(result==null || result.isBlank())return;
        if(result.equals(original))commands.performCommand(parsed,original);
        else commands.performPrefixedCommand(player.createCommandSourceStack(),result);
    }
    public static boolean move(ServerPlayer p,double x,double y,double z,float yaw,float pitch,double oldX,double oldY,double oldZ,float oldYaw,float oldPitch) {
        var host=ServerHooks.pluginHost();
        if(host!=null && !host.move(p,x,y,z,yaw,pitch,oldX,oldY,oldZ,oldYaw,oldPitch))return false;
        p.absSnapTo(x,y,z,yaw,pitch);return true;
    }
    private static final ThreadLocal<TeleportTransition> redirected=new ThreadLocal<>();
    public static void directTeleport(net.minecraft.server.network.ServerGamePacketListenerImpl connection,PositionMoveRotation pose,Set<Relative> flags,ServerPlayer p) {
        var input=new TeleportTransition(p.level(),pose.position(),pose.deltaMovement(),pose.yRot(),pose.xRot(),false,false,flags,TeleportTransition.DO_NOTHING);
        var approved=teleport(p,input);
        if(approved==null)return;
        if(approved.newLevel()==p.level()){connection.teleport(PositionMoveRotation.of(approved),approved.relatives());return;}
        var prev=redirected.get();redirected.set(approved);
        try {p.teleport(approved);} finally {if(prev==null)redirected.remove();else redirected.set(prev);}
    }
    public static TeleportTransition teleport(ServerPlayer p,TeleportTransition transition) {
        if(redirected.get()==transition)return transition;
        var host=ServerHooks.pluginHost();
        return host==null?transition:host.teleport(p,transition,cause.get()==null?"UNKNOWN":cause.get());
    }
    public static ServerPlayer withCause(ServerPlayer p,TeleportTransition t,String reason){
        var previous=cause.get();cause.set(reason);
        try{return p.teleport(t);}finally{if(previous==null)cause.remove();else cause.set(previous);}
    }
    public static Entity portal(Entity e,TeleportTransition t){
        if(!(e instanceof ServerPlayer p) || e.portalProcess==null)return e.teleport(t);
        var portal=e.portalProcess;
        String reason=portal.isSamePortal((net.minecraft.world.level.block.Portal)net.minecraft.world.level.block.Blocks.NETHER_PORTAL)?"NETHER_PORTAL":
            portal.isSamePortal((net.minecraft.world.level.block.Portal)net.minecraft.world.level.block.Blocks.END_PORTAL)?"END_PORTAL":
            portal.isSamePortal((net.minecraft.world.level.block.Portal)net.minecraft.world.level.block.Blocks.END_GATEWAY)?"END_GATEWAY":"UNKNOWN";
        return withCause(p,t,reason);
    }
    public static int random(net.minecraft.world.entity.LivingEntity e,double x,double y,double z,boolean effects){
        if(!(e instanceof ServerPlayer p))return e.randomTeleport(x,y,z,effects)?1:0;
        var level=p.level();var pos=net.minecraft.core.BlockPos.containing(x,y,z);
        if(!level.hasChunkAt(pos))return 0;
        while(pos.getY()>level.getMinY() && !level.getBlockState(pos.below()).blocksMotion()){pos=pos.below();y--;}
        if(pos.getY()<=level.getMinY())return 0;
        var box=p.getBoundingBox().move(x-p.getX(),y-p.getY(),z-p.getZ());
        if(!level.noCollision(p,box)||level.containsAnyLiquid(box))return 0;
        boolean ok=teleportTo(p,level,x,y,z,java.util.Set.of(),p.getYRot(),p.getXRot(),true,"CONSUMABLE_EFFECT");
        if(ok && effects)level.broadcastEntityEvent(p,(byte)46);
        return ok?1:-1;
    }
    public static boolean teleportTo(Entity entity,ServerLevel level,double x,double y,double z,Set<Relative> flags,float yaw,float pitch,boolean camera,String reason) {
        var previous=cause.get();cause.set(reason);
        try{return entity.teleportTo(level,x,y,z,flags,yaw,pitch,camera);}
        finally{if(previous==null)cause.remove();else cause.set(previous);}
    }
}
