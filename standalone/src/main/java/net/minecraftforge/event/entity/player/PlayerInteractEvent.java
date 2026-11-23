package net.minecraftforge.event.entity.player;
import java.util.Objects;
import mc.unity.adapter.forge.CancelBus;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.eventbus.api.bus.*;
import net.minecraftforge.eventbus.api.event.InheritableEvent;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
import net.minecraftforge.fml.LogicalSide;
public abstract class PlayerInteractEvent implements PlayerEvent, InheritableEvent {
    private static final CancelBus<PlayerInteractEvent> family = new CancelBus<>(PlayerInteractEvent.class);
    public static final EventBus<PlayerInteractEvent> BUS = family;
    private final Player player;
    private final InteractionHand hand;
    private final BlockPos pos;
    private final Direction face;
    private InteractionResult cancellation = InteractionResult.PASS;
    protected PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos, Direction face) {
        this.player = player;
        this.hand = hand;
        this.pos = pos;
        this.face = face;
    }
    public Player getEntity() {
        return player;
    }
    public InteractionHand getHand() {
        return hand;
    }
    public ItemStack getItemStack() {
        return player.getItemInHand(hand);
    }
    public BlockPos getPos() {
        return pos;
    }
    public Direction getFace() {
        return face;
    }
    public Level getLevel() {
        return player.level();
    }
    public LogicalSide getSide() {
        return getLevel().isClientSide() ? LogicalSide.CLIENT : LogicalSide.SERVER;
    }
    public InteractionResult getCancellationResult() {
        return cancellation;
    }
    public void setCancellationResult(InteractionResult value) {
        cancellation = Objects.requireNonNull(value);
    }
    public static abstract class BlockInteraction extends PlayerInteractEvent {
        protected BlockInteraction(Player p, InteractionHand h, BlockPos pos, Direction f) {
            super(p, h, pos, f);
        }
        private Result block = Result.DEFAULT, item = Result.DEFAULT;
        public Result getUseBlock() {
            return block;
        }
        public void setUseBlock(Result v) {
            block = Objects.requireNonNull(v);
        }
        public Result getUseItem() {
            return item;
        }
        public void setUseItem(Result v) {
            item = Objects.requireNonNull(v);
        }
    }
    public static final class RightClickBlock extends BlockInteraction implements Cancellable {
        public static final CancellableEventBus<RightClickBlock> BUS = family.child(RightClickBlock.class);
        private final BlockHitResult hit;
        public RightClickBlock(Player p, InteractionHand h, BlockPos pos, BlockHitResult hit) {
            super(p, h, pos, hit.getDirection());
            this.hit = hit;
        }
        public BlockHitResult getHitVec() {
            return hit;
        }
    }
    public static final class LeftClickBlock extends BlockInteraction implements Cancellable {
        public enum Action { START, STOP, ABORT, CLIENT_HOLD }
        public static final CancellableEventBus<LeftClickBlock> BUS = family.child(LeftClickBlock.class);
        private final Action action;
        public LeftClickBlock(Player p, BlockPos pos, Direction face, Action act) {
            super(p, InteractionHand.MAIN_HAND, pos, face);
            action = act;
        }
        public Action getAction() {
            return action;
        }
    }
    public static final class RightClickItem extends PlayerInteractEvent implements Cancellable {
        public static final CancellableEventBus<RightClickItem> BUS = family.child(RightClickItem.class);
        public RightClickItem(Player p, InteractionHand h) {
            super(p, h, p.blockPosition(), null);
        }
    }
    public static final class EntityInteractSpecific extends PlayerInteractEvent implements Cancellable {
        public static final CancellableEventBus<EntityInteractSpecific> BUS =
            family.child(EntityInteractSpecific.class);
        private final Entity target;
        private final Vec3 local;
        public EntityInteractSpecific(Player p, InteractionHand h, Entity target, Vec3 local) {
            super(p, h, target.blockPosition(), null);
            this.target = target;
            this.local = local;
        }
        public Entity getTarget() {
            return target;
        }
        public Vec3 getLocalPos() {
            return local;
        }
    }
    public static final class LeftClickEmpty extends PlayerInteractEvent {
        public static final EventBus<LeftClickEmpty> BUS = family.child(LeftClickEmpty.class);
        public LeftClickEmpty(Player p) {
            super(p, InteractionHand.MAIN_HAND, p.blockPosition(), null);
        }
    }
    public static final class RightClickEmpty extends PlayerInteractEvent {
        public static final EventBus<RightClickEmpty> BUS = family.child(RightClickEmpty.class);
        public RightClickEmpty(Player p, InteractionHand h) {
            super(p, h, p.blockPosition(), null);
        }
    }
    public static void clear() {
        family.clearFamily();
    }
}
