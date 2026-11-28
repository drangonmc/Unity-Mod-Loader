package mc.unity.adapter.neoforge;

import java.lang.invoke.*;
import java.util.*;
import mc.unity.core.Actions;
import mc.unity.core.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent.*;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

final class NeoActions implements Actions.Adapter {
    private final NeoBus bus;
    private final Actions.Kind kind;
    private static final MethodHandle SNAPSHOT=snapshotConstructor();
    private static MethodHandle snapshotConstructor(){
        try{return MethodHandles.privateLookupIn(BlockSnapshot.class,MethodHandles.lookup()).findConstructor(BlockSnapshot.class,
            MethodType.methodType(void.class,ResourceKey.class,LevelAccessor.class,BlockPos.class,BlockState.class,CompoundTag.class,int.class));}
        catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }
    private static BlockSnapshot snapshot(Placement.Change c){
        try{return (BlockSnapshot)SNAPSHOT.invoke(c.level().dimension(),c.level(),c.pos(),c.old(),c.data()==null?null:c.data().copy(),c.flags());}
        catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
    }
    NeoActions(NeoBus bus,Actions.Kind kind){this.bus=bus;this.kind=kind;}
    public boolean handles(Actions.Kind k){return k==kind;}
    private <E extends Event & ICancellableEvent> E dispatch(Actions.Action action,E event){
        event.setCanceled(action.cancelled);bus.post(event);action.cancelled=event.isCanceled();return event;
    }
    public void accept(Actions.Action a) {
        switch(kind){
            case BREAK -> {
                var event=new BreakBlockEvent(a.level,a.pos,a.state,a.player);
                event.setCanceled(a.cancelled || a.initiallyDenied);
                bus.post(event);a.cancelled=event.isCanceled();a.initiallyDenied=a.cancelled;
            }
            case PLACE -> {
                var list=a.changes.stream().map(NeoActions::snapshot).toList();
                var against=a.level.getBlockState(a.pos.relative(a.face.getOpposite()));
                dispatch(a,list.size()==1?new EntityPlaceEvent(list.getFirst(),against,a.player):new EntityMultiPlaceEvent(list,against,a.player));
            }
            case RIGHT_ITEM -> {var e=dispatch(a,new PlayerInteractEvent.RightClickItem(a.player,a.hand));if(a.cancelled)a.result=e.getCancellationResult();}
            case INTERACT_ENTITY -> {var e=dispatch(a,new PlayerInteractEvent.EntityInteract(a.player,a.hand,a.target,a.location));if(a.cancelled)a.result=e.getCancellationResult();}
            case ATTACK_ENTITY -> dispatch(a,new AttackEntityEvent(a.player,a.target));
            case LEFT_EMPTY -> bus.post(new PlayerInteractEvent.LeftClickEmpty(a.player));
            case RIGHT_EMPTY -> bus.post(new PlayerInteractEvent.RightClickEmpty(a.player,a.hand));
            default -> throw new AssertionError(kind);
        }
    }
}
