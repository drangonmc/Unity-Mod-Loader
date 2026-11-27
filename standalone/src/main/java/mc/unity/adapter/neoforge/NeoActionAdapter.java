package mc.unity.adapter.neoforge;

import mc.unity.core.Actions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class NeoActionAdapter implements Actions.Adapter {
    private final NeoBus bus=(NeoBus)NeoForge.EVENT_BUS;
    public boolean handles(Actions.Kind kind) {
        return bus.hasListeners();
    }
    public void accept(Actions.Action a) {
        switch(a.kind) {
            case BREAK -> {
                if (!(a.player instanceof ServerPlayer p)) return;
                var e=new BlockEvent.BreakEvent(a.level,a.pos,a.state,p);
                e.setCanceled(a.cancelled || a.initiallyDenied);
                bus.post(e); a.cancelled=e.isCanceled();
            }
            case PLACE -> {
                var e=a.changes.size()==1
                    ? new BlockEvent.EntityPlaceEvent(a.level,a.pos,a.state,a.level.getBlockState(a.against),a.player)
                    : new BlockEvent.EntityMultiPlaceEvent(a.level,a.pos,a.state,a.level.getBlockState(a.against),a.player);
                e.setCanceled(a.cancelled);bus.post(e);a.cancelled=e.isCanceled();
            }
            case RIGHT_BLOCK -> {
                var e=new PlayerInteractEvent.RightClickBlock(a.player,a.hand,a.pos,a.hit);
                e.setCanceled(a.cancelled);bus.post(e);a.cancelled=e.isCanceled();a.result=e.getCancellationResult();
            }
            case LEFT_BLOCK -> {
                var e=new PlayerInteractEvent.LeftClickBlock(a.player,a.pos,a.face);
                e.setCanceled(a.cancelled);bus.post(e);a.cancelled=e.isCanceled();a.result=e.getCancellationResult();
            }
            case RIGHT_ITEM -> {
                var e=new PlayerInteractEvent.RightClickItem(a.player,a.hand);
                e.setCanceled(a.cancelled);bus.post(e);a.cancelled=e.isCanceled();a.result=e.getCancellationResult();
            }
            case RIGHT_EMPTY -> { var e=new PlayerInteractEvent.RightClickEmpty(a.player,a.hand);e.setCanceled(a.cancelled);bus.post(e);a.cancelled=e.isCanceled(); }
            case LEFT_EMPTY -> { var e=new PlayerInteractEvent.LeftClickEmpty(a.player);e.setCanceled(a.cancelled);bus.post(e);a.cancelled=e.isCanceled(); }
            case INTERACT_ENTITY -> { var e=new PlayerInteractEvent.EntityInteractSpecific(a.player,a.hand,a.target,a.location);e.setCanceled(a.cancelled);bus.post(e);a.cancelled=e.isCanceled();a.result=e.getCancellationResult(); }
            case ATTACK_ENTITY -> {
                var e=new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(a.player,a.target);
                e.setCanceled(a.cancelled);bus.post(e);a.cancelled=e.isCanceled();
            }
            default -> {}
        }
    }
}
