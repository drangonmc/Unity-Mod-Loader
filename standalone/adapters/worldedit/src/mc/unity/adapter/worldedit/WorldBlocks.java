package mc.unity.adapter.worldedit;

import java.io.*;
import java.util.Set;
import java.util.function.Supplier;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
import org.enginehub.linbus.stream.LinBinaryIO;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinRootEntry;

final class WorldBlocks implements WorldNativeAccess<LevelChunk, BlockState, BlockPos> {
    static final Set<SideEffect> EFFECTS = Set.of(SideEffect.LIGHTING, SideEffect.NEIGHBORS,
        SideEffect.NETWORK, SideEffect.POI_UPDATE, SideEffect.ENTITY_AI, SideEffect.VALIDATION);
    final ServerLevel level;

    WorldBlocks(ServerLevel level) { this.level = level; }

    <T> T onServer(Supplier<T> action) {
        var server = level.getServer();
        return server.isSameThread() ? action.get() : server.submit(action).join();
    }

    public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 pos, B value, SideEffectSet effects)
        throws WorldEditException {
        if (level.isOutsideBuildHeight(pos.y())) return false;
        if (level.getServer().isSameThread()) return WorldNativeAccess.super.setBlock(pos, value, effects);
        return level.getServer().submit(() -> {
            try { return WorldNativeAccess.super.setBlock(pos, value, effects); }
            catch (WorldEditException e) { throw new java.util.concurrent.CompletionException(e); }
        }).join();
    }

    public void applySideEffects(BlockVector3 pos, com.sk89q.worldedit.world.block.BlockState previous,
                                 SideEffectSet effects) {
        onServer(() -> { WorldNativeAccess.super.applySideEffects(pos, previous, effects); return null; });
    }

    static LinCompoundTag snapshot(CompoundTag value) {
        try {
            var buffer = new ByteArrayOutputStream();
            NbtIo.write(value, new DataOutputStream(buffer));
            var input = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
            return LinBinaryIO.readUsing(input, LinRootEntry::readFrom).value();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public boolean updateTileEntity(BlockPos pos, LinCompoundTag value) {
        var tile = level.getBlockEntity(pos);
        if (tile == null) return false;
        try {
            var buffer = new ByteArrayOutputStream();
            LinBinaryIO.write(new DataOutputStream(buffer), new LinRootEntry("", value));
            var tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));
            var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(tile.getType()).toString();
            if (!id.equals(tag.getStringOr("id", ""))) throw new IllegalArgumentException("Unexpected block entity at " + pos);
            var problems = new ProblemReporter.Collector();
            var previous = tile.saveWithFullMetadata(level.registryAccess());
            try {
                tile.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), tag));
                if (!problems.isEmpty()) throw new IllegalArgumentException(problems.getReport());
            } catch (RuntimeException e) {
                tile.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), previous));
                throw e;
            }
            tile.setChanged();
            level.getChunkSource().blockChanged(pos);
            return true;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public LevelChunk getChunk(int x, int z) { return level.getChunk(x, z); }
    public BlockPos getPosition(int x, int y, int z) { return new BlockPos(x, y, z); }
    public BlockState toNative(com.sk89q.worldedit.world.block.BlockState state) {
        try {
            return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                                                   state.getAsString(), false).blockState();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException(state.getAsString(), e);
        }
    }
    public BlockState getBlockState(LevelChunk chunk, BlockPos pos) { return chunk.getBlockState(pos); }
    public BlockState setBlockState(LevelChunk chunk, BlockPos pos, BlockState state) {
        return chunk.setBlockState(pos, state, Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
    }
    public BlockState getValidBlockForPosition(BlockState state, BlockPos pos) {
        return Block.updateFromNeighbourShapes(state, level, pos);
    }
    public void updateLightingForBlock(BlockPos pos) { level.getChunkSource().getLightEngine().checkBlock(pos); }
    public void notifyBlockUpdate(LevelChunk chunk, BlockPos pos, BlockState old, BlockState state) {
        level.sendBlockUpdated(pos, old, state, Block.UPDATE_CLIENTS);
    }
    public boolean isChunkTicking(LevelChunk chunk) {
        return chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.BLOCK_TICKING);
    }
    public void markBlockChanged(LevelChunk chunk, BlockPos pos) { level.getChunkSource().blockChanged(pos); }
    public void notifyNeighbors(BlockPos pos, BlockState old, BlockState state) {
        level.updateNeighborsAt(pos, old.getBlock(), null);
        if (state.hasAnalogOutputSignal()) level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }
    public void updateNeighbors(BlockPos pos, BlockState old, BlockState state, int depth) {
        old.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, depth);
        state.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, depth);
        state.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, depth);
    }
    public void onBlockStateChange(BlockPos pos, BlockState old, BlockState state) {
        level.updatePOIOnBlockStateChange(pos, old, state);
    }
}
