package mc.unity.adapter.neoforge;

import mc.unity.core.Actions;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.*;

final class MachineInteractions implements Actions.Adapter {
    private final NeoBus bus;
    private final Actions.Kind kind;
    MachineInteractions(NeoBus bus,Actions.Kind kind){this.bus=bus;this.kind=kind;}
    public boolean handles(Actions.Kind k){return kind==k;}
    private static TriState state(int policy){return policy<0?TriState.FALSE:policy>0?TriState.TRUE:TriState.DEFAULT;}
    private static int value(TriState state){return state==TriState.TRUE?1:state==TriState.FALSE?-1:0;}
    public void accept(Actions.Action a){
        if(kind==Actions.Kind.RIGHT_BLOCK){
            var e=new RightClickBlock(a.player,a.hand,a.pos,a.hit);
            e.setCanceled(a.cancelled);e.setUseBlock(state(a.blockPolicy));e.setUseItem(state(a.itemPolicy));
            bus.post(e);a.cancelled=e.isCanceled();a.blockPolicy=value(e.getUseBlock());a.itemPolicy=value(e.getUseItem());
            if(a.cancelled)a.result=e.getCancellationResult();
        }else{
            var phase=switch(a.phase){case "STOP"->LeftClickBlock.Action.STOP;case "ABORT"->LeftClickBlock.Action.ABORT;default->LeftClickBlock.Action.START;};
            var e=new LeftClickBlock(a.player,a.pos,a.face,phase);
            e.setCanceled(a.cancelled);e.setUseBlock(state(a.blockPolicy));e.setUseItem(state(a.itemPolicy));
            bus.post(e);a.cancelled=e.isCanceled();a.blockPolicy=value(e.getUseBlock());a.itemPolicy=value(e.getUseItem());
        }
    }
}
