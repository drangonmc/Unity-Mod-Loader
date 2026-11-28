package mc.unity.plugins;

import java.lang.invoke.*;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import net.minecraft.world.entity.Display;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.Registries;
import com.mojang.math.Transformation;

final class DisplayView {
    private static final MethodHandle GET,SET,READ,WRITE,LIGHT,GET_LIGHT;
    static{
        try{
            var block=MethodHandles.privateLookupIn(Display.BlockDisplay.class,MethodHandles.lookup());
            GET=block.findVirtual(Display.BlockDisplay.class,"getBlockState",MethodType.methodType(BlockState.class));
            SET=block.findVirtual(Display.BlockDisplay.class,"setBlockState",MethodType.methodType(void.class,BlockState.class));
            var all=MethodHandles.privateLookupIn(Display.class,MethodHandles.lookup());
            READ=all.findStatic(Display.class,"createTransformation",MethodType.methodType(Transformation.class,SynchedEntityData.class));
            WRITE=all.findVirtual(Display.class,"setTransformation",MethodType.methodType(void.class,Transformation.class));
            LIGHT=all.findVirtual(Display.class,"setBrightnessOverride",MethodType.methodType(void.class,net.minecraft.util.Brightness.class));
            GET_LIGHT=all.findVirtual(Display.class,"getBrightnessOverride",MethodType.methodType(net.minecraft.util.Brightness.class));
        }catch(ReflectiveOperationException ex){throw new ExceptionInInitializerError(ex);}
    }
    static Object call(Display display,String method,Object[] args)throws Throwable{
        return switch(method){
            case "getBlock" -> BlockViews.data((BlockState)GET.invoke(display));
            case "setBlock" -> {
                var value=(org.bukkit.block.data.BlockData)args[0];
                SET.invoke(display,BlockStateParser.parseForBlock(display.level().registryAccess().lookupOrThrow(Registries.BLOCK),value.getAsString(),false).blockState());yield null;
            }
            case "getTransformation" -> {
                var t=(Transformation)READ.invoke(display.getEntityData());
                yield new org.bukkit.util.Transformation(new Vector3f(t.translation()),new Quaternionf(t.leftRotation()),new Vector3f(t.scale()),new Quaternionf(t.rightRotation()));
            }
            case "setTransformation" -> {
                var t=(org.bukkit.util.Transformation)args[0];
                WRITE.invoke(display,new Transformation(t.getTranslation(),t.getLeftRotation(),t.getScale(),t.getRightRotation()));yield null;
            }
            case "setBrightness" -> {
                var b=(org.bukkit.entity.Display.Brightness)args[0];
                LIGHT.invoke(display,b==null?null:new net.minecraft.util.Brightness(b.getBlockLight(),b.getSkyLight()));yield null;
            }
            case "getBrightness" -> {
                var b=(net.minecraft.util.Brightness)GET_LIGHT.invoke(display);
                yield b==null?null:new org.bukkit.entity.Display.Brightness(b.block(),b.sky());
            }
            default -> throw Views.unsupported("Display",method);
        };
    }
}
