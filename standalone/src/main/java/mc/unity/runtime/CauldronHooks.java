package mc.unity.runtime;

import mc.unity.core.Actions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

public final class CauldronHooks {
    private static final class Interaction {
        final BlockHitResult hit;
        boolean applied;
        Interaction(BlockHitResult hit){this.hit=hit;}
    }
    private static final ThreadLocal<Interaction> clicked=new ThreadLocal<>();
    public static InteractionResult interact(CauldronInteraction action,BlockState state,Level level,BlockPos pos,
                    Player player,InteractionHand hand,ItemStack stack,BlockHitResult hit) {
        if(ServerHooks.pluginHost()==null)return action.interact(state,level,pos,player,hand,stack);
        var previous=clicked.get();clicked.set(new Interaction(hit));
        try{return action.interact(state,level,pos,player,hand,stack);}
        finally{if(previous==null)clicked.remove();else clicked.set(previous);}
    }
    public static InteractionResult change(Level level,BlockPos pos,Player player,InteractionHand hand,ItemStack held,
                                           ItemStack result,BlockState proposed,SoundEvent sound,boolean fill) {
        var host=ServerHooks.pluginHost();
        var interaction=clicked.get();
        var hit=interaction==null?null:interaction.hit;
        if(host==null || !(player instanceof ServerPlayer p) || hit==null || (!host.observesBucket(fill) && !host.observesCauldron()))return null;
        var item=held.getItem();
        var chosen=host.bucket(p,pos,hit,hand,fill,result);
        var approved=chosen==null ? null : host.cauldron(p,pos,proposed,fill?"BUCKET_FILL":"BUCKET_EMPTY");
        if(approved==null){
            Actions.resync(p,pos);
            return InteractionResult.SUCCESS;
        }
        level.setBlockAndUpdate(pos,approved);
        player.setItemInHand(hand,ItemUtils.createFilledResult(held,player,chosen));
        player.awardStat(fill?Stats.USE_CAULDRON:Stats.FILL_CAULDRON);
        player.awardStat(Stats.ITEM_USED.get(item));
        level.playSound(null,pos,sound,SoundSource.BLOCKS,1,1);
        level.gameEvent(null,fill?GameEvent.FLUID_PICKUP:GameEvent.FLUID_PLACE,pos);
        return InteractionResult.SUCCESS;
    }
    /** Called only after the original recipe and level checks have accepted this player action. */
    public static boolean skipItemWork(Level level,BlockState old,BlockPos pos,Player player,String reason) {
        if(level.isClientSide())return true;
        var host=ServerHooks.pluginHost();var context=clicked.get();
        if(host==null || context==null || !(player instanceof ServerPlayer p) || !host.observesCauldron())return false;
        var property=net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL;
        int before=old.hasProperty(property)?old.getValue(property):0;
        int after=before+(reason.equals("BOTTLE_EMPTY")?1:-1);
        var next=after<=0?Blocks.CAULDRON.defaultBlockState():
            (old.hasProperty(property)?old:Blocks.WATER_CAULDRON.defaultBlockState()).setValue(property,after);
        var approved=host.cauldron(p,pos,next,reason);
        if(approved==null){Actions.resync(p,pos);return true;}
        level.setBlockAndUpdate(pos,approved);
        context.applied=true;
        return false;
    }
    public static boolean setOnce(Level level,BlockPos pos,BlockState state) {
        var ctx=clicked.get();return ctx!=null && ctx.applied || level.setBlockAndUpdate(pos,state);
    }
    public static void lowerOnce(BlockState old,Level level,BlockPos pos) {
        var ctx=clicked.get();
        if(ctx==null || !ctx.applied)net.minecraft.world.level.block.LayeredCauldronBlock.lowerFillLevel(old,level,pos);
    }

}
