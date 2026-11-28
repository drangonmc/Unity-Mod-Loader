package mc.unity.plugins;

import org.bukkit.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.concurrent.TimeUnit;

final class WorldQueries {
    static WorldBorder border(BukkitViews views,ServerLevel level) {
        var border=level.getWorldBorder();
        return Views.of(WorldBorder.class,"world border",border,(m,a)->switch(m){
            case "getWorld"->views.world(level);
            case "getSize"->border.getSize();
            case "getCenter"->new Location(views.world(level),border.getCenterX(),0,border.getCenterZ());
            case "getMaxSize"->net.minecraft.world.level.border.WorldBorder.MAX_SIZE;
            case "getMaxCenterCoordinate"->net.minecraft.world.level.border.WorldBorder.MAX_CENTER_COORDINATE;
            case "getDamageBuffer"->border.getSafeZone();
            case "getDamageAmount"->border.getDamagePerBlock();
            case "getWarningDistance"->border.getWarningBlocks();
            case "getWarningTimeTicks"->border.getWarningTime();
            case "isInside"->{var pos=(Location)a[0];yield pos.getWorld()==views.world(level) && border.isWithinBounds(pos.getX(),pos.getZ());}
            case "setCenter"->{
                double x,z;if(a[0] instanceof Location at){x=at.getX();z=at.getZ();}else{x=(double)a[0];z=(double)a[1];}
                if(!Double.isFinite(x)||!Double.isFinite(z)||Math.max(Math.abs(x),Math.abs(z))>net.minecraft.world.level.border.WorldBorder.MAX_CENTER_COORDINATE)throw new IllegalArgumentException("Invalid world border center");
                border.setCenter(x,z);yield null;
            }
            case "setSize"->{
                double size=(double)a[0];if(!Double.isFinite(size)||size<1||size>net.minecraft.world.level.border.WorldBorder.MAX_SIZE)throw new IllegalArgumentException("Invalid world border size");
                long ticks=a.length==1?0:a.length==2?Math.multiplyExact((long)a[1],20):((TimeUnit)a[1]).toMillis((long)a[2])/50;
                if(ticks<0)throw new IllegalArgumentException("Negative border duration");
                if(ticks==0)border.setSize(size);else border.lerpSizeBetween(border.getSize(),size,ticks,level.getGameTime());
                yield null;
            }
            default->throw Views.unsupported("WorldBorder",m);
        });
    }
    static int highest(ServerLevel world,Object[] args) {
        int x,z;
        if(args[0] instanceof Location l){x=l.getBlockX();z=l.getBlockZ();}
        else{x=(int)args[0];z=(int)args[1];}
        var map=args[args.length-1] instanceof HeightMap key?Heightmap.Types.valueOf(key.name()):Heightmap.Types.MOTION_BLOCKING;
        return world.getHeight(map,x,z)-1;
    }
}
