package mc.unity.runtime;
import java.util.Optional;
import net.minecraft.util.BlockUtil;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.*;
import net.minecraft.world.phys.Vec3;

public final class PortalHooks {
    public record Plan(ServerLevel world,Vec3 target,float yaw,float pitch,int search,boolean create,int creation){}
    private static final ThreadLocal<Integer> radius=new ThreadLocal<>();
    public static int radius(int vanilla){Integer custom=radius.get();return custom==null?vanilla:custom;}
    public static Plan nether(ServerLevel world,Entity entity,BlockPos to,boolean nether){
        var initial=new Plan(world,Vec3.atLowerCornerOf(to),entity.getYRot(),entity.getXRot(),nether?16:128,true,16);
        var host=ServerHooks.pluginHost();return entity instanceof ServerPlayer p && host!=null?host.portal(p,initial,"NETHER_PORTAL"):initial;
    }
    public static BlockPos destination(Plan plan){var p=plan.target;return plan.world.getWorldBorder().clampToBounds(p.x,p.y,p.z);}
    public static boolean netherDimension(Plan p){return p.world.dimension().equals(Level.NETHER);}
    public static Optional<BlockPos> find(PortalForcer forcer,BlockPos pos,boolean nether,WorldBorder border,Plan p){
        var old=radius.get();radius.set(p.search);
        try{return forcer.findClosestPortalPosition(pos,nether,border);}finally{if(old==null)radius.remove();else radius.set(old);}
    }
    public static Optional<BlockUtil.FoundRectangle> create(PortalForcer forcer,BlockPos pos,Direction.Axis axis,Plan p){
        if(!p.create)return Optional.empty();var old=radius.get();radius.set(p.creation);
        try{return forcer.createPortal(pos,axis);}finally{if(old==null)radius.remove();else radius.set(old);}
    }
    public static TeleportTransition end(TeleportTransition t,Entity e,BlockPos platform){
        if(t==null)return null;
        var host=ServerHooks.pluginHost();
        var a=PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(e),PositionMoveRotation.of(t),t.relatives());
        var original=new Plan(t.newLevel(),a.position(),a.yRot(),a.xRot(),0,true,0);
        var result=e instanceof ServerPlayer player && host!=null?host.portal(player,original,"END_PORTAL"):original;
        if(result==null)return null;
        if(platform!=null && result.create && result.world.dimension()==Level.END){
            var offset=BlockPos.containing(result.target).subtract(BlockPos.containing(original.target));
            net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(result.world,platform.offset(offset),true);
        }
        if(original.equals(result))return t;
        return new TeleportTransition(result.world,result.target,a.deltaMovement(),result.yaw,result.pitch,t.missingRespawnBlock(),t.asPassenger(),java.util.Set.of(),t.postTeleportTransition());
    }
}
