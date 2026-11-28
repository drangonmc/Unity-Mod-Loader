package net.neoforged.neoforge.transfer.fluid;

import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Native bucket exchange against an opted-in NeoForge block capability. One bucket is 1,000 mB. */
public final class FluidUtil {
    private FluidUtil(){}
    public static boolean interactWithFluidHandler(Player player,InteractionHand hand,Level level,BlockPos pos,Direction side) {
        var handler=Capabilities.Fluid.BLOCK.getCapability(level,pos,null,null,side);
        if(handler==null)return false;
        var held=player.getItemInHand(hand);
        try(var tx=Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            Item result=null;
            if(held.is(Items.BUCKET)) {
                for(int i=0;i<handler.size();i++) {
                    var fluid=handler.getResource(i);
                    if(fluid.isEmpty() || !fluid.getComponentsPatch().isEmpty() || handler.getAmountAsLong(i)<1000)continue;
                    var filled=fluid.getFluid().getBucket();
                    if(filled==Items.AIR)continue;
                    try(var attempt=Transaction.open(tx)){
                        if(handler.extract(i,fluid,1000,attempt)!=1000)continue;
                        attempt.commit();result=filled;break;
                    }
                }
            }else{
                Fluid liquid=held.is(Items.WATER_BUCKET)?Fluids.WATER:held.is(Items.LAVA_BUCKET)?Fluids.LAVA:null;
                if(liquid!=null && handler.insert(FluidResource.of(liquid),1000,tx)==1000)result=Items.BUCKET;
            }
            if(result==null)return false;
            if(!level.isClientSide()) {
                tx.commit();
                player.setItemInHand(hand,ItemUtils.createFilledResult(held,player,result.getDefaultInstance()));
            }
            return true;
        }
    }
}
