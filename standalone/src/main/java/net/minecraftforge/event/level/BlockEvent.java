package net.minecraftforge.event.level;
import java.util.*;
import mc.unity.adapter.forge.CancelBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.*;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
public interface BlockEvent {
    LevelAccessor getLevel();
    BlockPos getPos();
    BlockState getState();
    final class BreakEvent extends MutableEvent implements BlockEvent, Cancellable, HasResult {
        public static final CancellableEventBus<BreakEvent> BUS = new CancelBus<>(BreakEvent.class);
        private final Level level;
        private final BlockPos pos;
        private final BlockState state;
        private final Player player;
        private Result result;
        private Integer experience;
        public BreakEvent(Level level, BlockPos pos, BlockState state, Player player, Result result) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.player = player;
            this.result = result;
        }
        public LevelAccessor getLevel() {
            return level;
        }
        public BlockPos getPos() {
            return pos;
        }
        public BlockState getState() {
            return state;
        }
        public Player getPlayer() {
            return player;
        }
        public int getExpToDrop() {
            if (result.isDenied())
                return 0;
            if (experience == null)
                experience = net.minecraft.world.level.block.UnityBlockExperience.sample(player, state);
            return experience;
        }
        public void setExpToDrop(int amount) {
            experience = Math.max(0, amount);
        }
        public Integer experienceOverride() {
            return experience;
        }
        public Result getResult() {
            return result;
        }
        public void setResult(Result value) {
            result = Objects.requireNonNull(value);
        }
    }
    class EntityPlaceEvent extends MutableEvent implements BlockEvent, Cancellable {
        public static final CancellableEventBus<EntityPlaceEvent> BUS = new CancelBus<>(EntityPlaceEvent.class);
        private final BlockSnapshot block;
        private final BlockState placed, against;
        private final Entity entity;
        public EntityPlaceEvent(BlockSnapshot block, BlockState against, Entity entity) {
            this.block = block;
            this.against = against;
            this.entity = entity;
            placed = entity instanceof Player ? block.getCurrentBlock() : block.getReplacedBlock();
        }
        public LevelAccessor getLevel() {
            return block.getLevel();
        }
        public BlockPos getPos() {
            return block.getPos();
        }
        public BlockState getState() {
            return placed;
        }
        public BlockState getPlacedBlock() {
            return placed;
        }
        public BlockState getPlacedAgainst() {
            return against;
        }
        public BlockSnapshot getBlockSnapshot() {
            return block;
        }
        public Entity getEntity() {
            return entity;
        }
    }
    final class EntityMultiPlaceEvent extends EntityPlaceEvent {
        public static final CancellableEventBus<EntityMultiPlaceEvent> BUS =
            new CancelBus<>(EntityMultiPlaceEvent.class);
        private final List<BlockSnapshot> changes;
        public EntityMultiPlaceEvent(List<BlockSnapshot> changes, BlockState against, Entity entity) {
            super(changes.getFirst(), against, entity);
            this.changes = List.copyOf(changes);
        }
        public List<BlockSnapshot> getReplacedBlockSnapshots() {
            return changes;
        }
    }
}
