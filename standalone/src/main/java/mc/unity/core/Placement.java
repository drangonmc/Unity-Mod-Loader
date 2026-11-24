package mc.unity.core;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
/**
 * Stage authoritative item-use writes until all placement listeners agree. Vanilla commits the notifications.
 */
public final class Placement {
    public record Change(Level level, BlockPos pos, BlockState old, CompoundTag data, int flags, int depth) {
        public static Change capture(Level level, BlockPos pos, int flags, int depth) {
            var be = level.getBlockEntity(pos);
            return new Change(level, pos.immutable(), level.getBlockState(pos),
                              be == null ? null : be.saveWithFullMetadata(level.registryAccess()), flags,
                              depth);
        }
        public void restore(boolean notify) {
            level.setBlock(pos, old, (notify ? 3 : 2) | 16 | 32 | 256 | 512);
            if (data != null) {
                var be = level.getBlockEntity(pos);
                if (be != null) {
                    be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                                                               level.registryAccess(), data.copy()));
                    be.setChanged();
                }
            }
        }
    }
    private record Removal(Change source, Runnable action) {}
    private static final class Capture {
        final Level level;
        final List<Change> changes = new ArrayList<>();
        final Map<BlockPos, List<Removal>> effects = new LinkedHashMap<>();
        final Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> originalEntities =
            new HashMap<>();
        Change writing;
        void defer(Runnable effect) {
            effects.computeIfAbsent(writing.pos, k -> new ArrayList<>()).add(new Removal(writing, effect));
        }
        Capture(Level level) {
            this.level = level;
        }
    }
    private record Transition(Change start, BlockState end) {}
    private static List<Transition> netChanges(List<Change> journal) {
        var changes = new LinkedHashMap<BlockPos, Change>();
        for (var entry : journal)
            changes.merge(entry.pos, entry,
                          (first, last)
                              -> new Change(first.level, first.pos, first.old, first.data,
                                            last.flags | ((first.flags | last.flags) & 3),
                                            Math.min(first.depth, last.depth)));
        return changes.values().stream().map(c -> new Transition(c, c.level.getBlockState(c.pos))).toList();
    }
    public static void preRemove(net.minecraft.world.level.block.entity.BlockEntity entity, BlockPos pos,
                                 BlockState previous) {
        var capture = active.get();
        if (capture != null && capture.level == entity.getLevel()) {
            var p = pos.immutable();
            capture.defer(() -> entity.preRemoveSideEffects(p, previous));
        } else
            entity.preRemoveSideEffects(pos, previous);
    }
    public static void afterRemoval(BlockState previous, ServerLevel level, BlockPos pos, boolean moved) {
        var capture = active.get();
        if (capture != null && capture.level == level) {
            var p = pos.immutable();
            capture.defer(() -> previous.affectNeighborsAfterRemoval(level, p, moved));
        } else
            previous.affectNeighborsAfterRemoval(level, pos, moved);
    }
    private static final ThreadLocal<Capture> active = new ThreadLocal<>();
    private static final ThreadLocal<Change> replay = new ThreadLocal<>();
    public static boolean capturing(Level level) {
        var c = active.get();
        return c != null && c.level == level;
    }
    public static BlockState write(LevelChunk chunk, BlockPos pos, BlockState state, int flags, int depth) {
        var level = chunk.getLevel();
        var committing = replay.get();
        if (committing != null && committing.level == level && committing.pos.equals(pos)) {
            replay.remove();
            return committing.old;
        }
        var c = active.get();
        if (c == null || c.level != level)
            return chunk.setBlockState(pos, state, flags);
        var before = Change.capture(level, pos, flags, depth);
        if (!c.originalEntities.containsKey(before.pos))
            c.originalEntities.put(before.pos, level.getBlockEntity(pos));
        c.changes.add(before);
        var outer = c.writing;
        c.writing = before;
        try {
            var old = chunk.setBlockState(pos, state, flags);
            if (old == null) {
                discard(c.changes, before);
                var effects = c.effects.get(before.pos);
                if (effects != null)
                    effects.removeIf(e -> e.source == before);
            }
            return old;
        } finally {
            c.writing = outer;
        }
    }
    static void discard(List<Change> changes, Change rejected) {
        // Records can be equal at the same position. Remove only this invocation's snapshot.
        for (var it = changes.listIterator(changes.size()); it.hasPrevious();) {
            if (it.previous() == rejected) {
                it.remove();
                return;
            }
        }
    }
    public static void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!capturing(level))
            state.onPlace(level, pos, old, moved);
    }
    public static final class Use {
        final UseOnContext context;
        final ItemStack before;
        final Capture capture;
        Use(UseOnContext context) {
            this.context = context;
            before = context.getItemInHand().copy();
            capture = new Capture(context.getLevel());
        }
    }
    public static Use begin(Item item, UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel) || context.getPlayer() == null ||
            item instanceof BucketItem || active.get() != null)
            return null;
        var use = new Use(context);
        active.set(use.capture);
        return use;
    }
    public static void abortUse(Use use) {
        if (use == null)
            return;
        active.remove();
        try {
            rollback(use.capture.changes);
        } finally {
            use.context.getPlayer().setItemInHand(use.context.getHand(), use.before);
            sync(use.context.getPlayer(), use.capture.changes);
        }
    }
    public static InteractionResult finish(InteractionResult result, Use use) {
        if (use == null)
            return result;
        active.remove();
        var context = use.context;
        var level = context.getLevel();
        var player = context.getPlayer();
        var hand = context.getHand();
        var before = use.before;
        var capture = use.capture;
        var changes = capture.changes;
        List<Transition> commits;
        boolean accepted = false;
        try {
            if (changes.isEmpty()) {
                accepted = true;
                return result;
            }
            var after = player.getItemInHand(hand);
            player.setItemInHand(hand, before);
            if (!result.consumesAction())
                return result;
            var net = netChanges(changes);
            commits = net.stream().filter(t -> t.end != t.start.old).toList();
            if (!commits.isEmpty() && Actions.active(Actions.Kind.PLACE)) {
                var event = Actions.player(Actions.Kind.PLACE, player);
                event.changes = commits.stream().map(Transition::start).toList();
                event.pos = event.changes.getFirst().pos;
                event.face = context.getClickedFace();
                if (Actions.post(event).cancelled)
                    return InteractionResult.FAIL;
            }
            player.setItemInHand(hand, after);
            for (var transition : net)
                if (transition.start.old == transition.end && transition.start.data != null &&
                    capture.originalEntities.get(transition.start.pos) !=
                        level.getBlockEntity(transition.start.pos))
                    transition.start.restore(false);
            accepted = true;
        } finally {
            if (!accepted)
                try {
                    rollback(changes);
                } finally {
                    player.setItemInHand(hand, before);
                    sync(player, changes);
                }
        }
        for (var transition : commits) {
            var change = transition.start;
            if (level.getBlockState(change.pos) != transition.end)
                continue;
            // Only the first departure from the original block may remove the original contents.
            Change departure = null;
            for (var effect : capture.effects.getOrDefault(change.pos, List.of())) {
                var from = effect.source;
                if (from != null && from.pos.equals(change.pos) &&
                    from.old.getBlock() == change.old.getBlock() &&
                    (departure == null || from == departure)) {
                    departure = from;
                    effect.action.run();
                }
            }
            if ((change.flags & 512) == 0)
                transition.end.onPlace(level, change.pos, change.old, (change.flags & 64) != 0);
            if (level.getBlockState(change.pos) != transition.end)
                continue;
            replay.set(change);
            try {
                level.setBlock(change.pos, transition.end, change.flags, change.depth);
            } finally {
                replay.remove();
            }
        }
        return result;
    }
    private static void sync(net.minecraft.world.entity.player.Player player, List<Change> changes) {
        Actions.resync(player, changes.stream().map(Change::pos).distinct().toArray(BlockPos[] ::new));
    }
    private static void rollback(List<Change> changes) {
        var first = new LinkedHashMap<BlockPos, Change>();
        for (var c : changes)
            first.putIfAbsent(c.pos, c);
        for (var c : first.reversed().values())
            c.restore(false);
    }
}
