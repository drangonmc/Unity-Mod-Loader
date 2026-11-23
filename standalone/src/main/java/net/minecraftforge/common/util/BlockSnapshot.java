package net.minecraftforge.common.util;
import mc.unity.core.Placement.Change;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class BlockSnapshot {
    private final Change change;
    public BlockSnapshot(Change change) {
        this.change = change;
    }
    public static BlockSnapshot create(ResourceKey<Level> dim, LevelAccessor w, BlockPos pos, int flag) {
        return new BlockSnapshot(Change.capture((Level) w, pos, flag, 512));
    }
    public static BlockSnapshot create(ResourceKey<Level> dim, LevelAccessor w, BlockPos pos) {
        return create(dim, w, pos, 3);
    }
    public LevelAccessor getLevel() {
        return change.level();
    }
    public BlockPos getPos() {
        return change.pos();
    }
    public int getFlag() {
        return change.flags();
    }
    public BlockState getReplacedBlock() {
        return change.old();
    }
    public BlockState getCurrentBlock() {
        return change.level().getBlockState(getPos());
    }
    public BlockEntity getBlockEntity() {
        return change.level().getBlockEntity(getPos());
    }
    public CompoundTag getTag() {
        return change.data() == null ? null : change.data().copy();
    }
}
