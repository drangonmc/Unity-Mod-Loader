package mc.unity.adapter.forge;
import static net.minecraftforge.event.entity.player.PlayerInteractEvent.*;

import mc.unity.core.Actions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent.*;
public final class ActionAdapter implements Actions.Adapter {
    public boolean handles(Actions.Kind k) {
        return switch (k) {
            case PLACE -> EntityPlaceEvent.BUS.hasListeners() || EntityMultiPlaceEvent.BUS.hasListeners();
            case BREAK -> BreakEvent.BUS.hasListeners();
            case LEFT_EMPTY -> LeftClickEmpty.BUS.hasListeners();
            case RIGHT_EMPTY -> RightClickEmpty.BUS.hasListeners();
            case LEFT_BLOCK -> LeftClickBlock.BUS.hasListeners();
            case RIGHT_BLOCK -> RightClickBlock.BUS.hasListeners();
            case RIGHT_ITEM -> RightClickItem.BUS.hasListeners();
            case INTERACT_ENTITY -> EntityInteractSpecific.BUS.hasListeners();
            case ATTACK_ENTITY -> AttackEntityEvent.BUS.hasListeners();
            case ATTACK -> LivingAttackEvent.BUS.hasListeners();
            case HURT -> LivingHurtEvent.BUS.hasListeners();
            case DAMAGE -> LivingDamageEvent.BUS.hasListeners();
        };
    }
    private static int policy(Result r) {
        return r == Result.DEFAULT ? 0 : r == Result.ALLOW ? 1 : -1;
    }
    private static void use(Actions.Action action, BlockInteraction event) {
        action.blockPolicy = policy(event.getUseBlock());
        action.itemPolicy = policy(event.getUseItem());
        action.result = event.getCancellationResult();
    }
    private static boolean post(Actions.Action action, net.minecraftforge.eventbus.api.bus.CancellableEventBus<?> bus,
                                net.minecraftforge.eventbus.internal.Event event) {
        return ((CancelBus<?>)bus).post(event,action.cancelled);
    }
    public void accept(Actions.Action a) {
        switch (a.kind) {
            case PLACE -> {
                var snaps = a.changes.stream().map(BlockSnapshot::new).toList();
                var against = a.level.getBlockState(a.pos.relative(a.face.getOpposite()));
                a.cancelled = snaps.size() == 1
                    ? post(a,EntityPlaceEvent.BUS,new EntityPlaceEvent(snaps.getFirst(), against, a.player))
                    : post(a,EntityMultiPlaceEvent.BUS,new EntityMultiPlaceEvent(snaps, against, a.player));
            }
            case BREAK -> {
                var e = new BreakEvent(
                    a.level, a.pos, a.state, a.player, a.initiallyDenied ? Result.DENY : Result.DEFAULT);
                a.cancelled = post(a,BreakEvent.BUS,e) || e.getResult().isDenied();
                if (e.experienceOverride() != null) a.experience = e.experienceOverride();
            }
            case LEFT_BLOCK -> {
                var e = new LeftClickBlock(a.player, a.pos, a.face, LeftClickBlock.Action.valueOf(a.phase));
                a.cancelled = post(a,LeftClickBlock.BUS,e);
                use(a, e);
            }
            case RIGHT_BLOCK -> {
                var e = new RightClickBlock(a.player, a.hand, a.pos, a.hit);
                a.cancelled = post(a,RightClickBlock.BUS,e);
                use(a, e);
            }
            case RIGHT_ITEM -> {
                var e = new RightClickItem(a.player, a.hand);
                a.cancelled = post(a,RightClickItem.BUS,e);
                a.result = e.getCancellationResult();
            }
            case INTERACT_ENTITY -> {
                var e = new EntityInteractSpecific(a.player, a.hand, a.target, a.location);
                a.cancelled = post(a,EntityInteractSpecific.BUS,e);
                a.result = e.getCancellationResult();
            }
            case LEFT_EMPTY -> LeftClickEmpty.BUS.post(new LeftClickEmpty(a.player));
            case RIGHT_EMPTY -> RightClickEmpty.BUS.post(new RightClickEmpty(a.player, a.hand));
            case ATTACK_ENTITY ->
                a.cancelled = post(a,AttackEntityEvent.BUS,new AttackEntityEvent(a.player, a.target));
            case ATTACK ->
                a.cancelled = post(a,LivingAttackEvent.BUS,
                    new LivingAttackEvent((LivingEntity) a.target, a.source, a.amount));
            case HURT -> {
                var e = new LivingHurtEvent((LivingEntity) a.target, a.source, a.amount);
                a.cancelled = post(a,LivingHurtEvent.BUS,e);
                a.amount = e.getAmount();
            }
            case DAMAGE -> {
                var e = new LivingDamageEvent((LivingEntity) a.target, a.source, a.amount);
                a.cancelled = post(a,LivingDamageEvent.BUS,e);
                a.amount = e.getAmount();
            }
        }
    }
    public static void clear() {
        PlayerInteractEvent.clear();
        for (var b : java.util.List.of(EntityPlaceEvent.BUS, EntityMultiPlaceEvent.BUS, BreakEvent.BUS,
                 AttackEntityEvent.BUS, LivingAttackEvent.BUS, LivingHurtEvent.BUS, LivingDamageEvent.BUS))
            ((CancelBus<?>) b).clearFamily();
    }
}
