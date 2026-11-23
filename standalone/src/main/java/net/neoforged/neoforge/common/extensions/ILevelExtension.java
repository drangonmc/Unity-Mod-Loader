package net.neoforged.neoforge.common.extensions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
public interface ILevelExtension {
    default <T,C> T getCapability(BlockCapability<T,C> capability,BlockPos pos,C context){
        return capability.getCapability((Level)this,pos,null,null,context);
    }
    default <T,C> T getCapability(BlockCapability<T,C> capability,BlockPos pos,BlockState state,BlockEntity entity,C context){
        return capability.getCapability((Level)this,pos,state,entity,context);
    }
    default <T> T getCapability(BlockCapability<T,Void> capability,BlockPos pos){return getCapability(capability,pos,null);}
    default <T> T getCapability(BlockCapability<T,Void> capability,BlockPos pos,BlockState state,BlockEntity entity){return getCapability(capability,pos,state,entity,null);}
}
