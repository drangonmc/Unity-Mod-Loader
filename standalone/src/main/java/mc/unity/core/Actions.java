package mc.unity.core;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
/** Synchronous decisions on the game thread. Platform adapters translate these to their own event types. */
public final class Actions {
    public enum Kind {
        PLACE,
        BREAK,
        LEFT_BLOCK,
        LEFT_EMPTY,
        RIGHT_EMPTY,
        RIGHT_BLOCK,
        RIGHT_ITEM,
        INTERACT_ENTITY,
        ATTACK_ENTITY,
        ATTACK,
        HURT,
        DAMAGE
    }
    public static final class Action {
        public final Kind kind;
        public Player player;
        public Entity target;
        public Level level;
        public BlockPos pos;
        public BlockState state;
        public Direction face;
        public InteractionHand hand;
        public BlockHitResult hit;
        public Vec3 location;
        public String phase;
        public DamageSource source;
        public float amount;
        public List<Placement.Change> changes;
        public Integer experience;
        public boolean dropItems = true;
        public BlockPos against;
        public net.minecraft.world.item.ItemStack usedStack;
        public boolean initiallyDenied;
        public boolean cancelled;
        public InteractionResult result = InteractionResult.PASS;
        public int blockPolicy, itemPolicy;
        Action(Kind k) {
            kind = k;
        }
    }
    public interface Adapter {
        boolean handles(Kind kind);
        void accept(Action event);
    }
    private static final class Registration implements Runnable {
        final Adapter adapter;
        Registration(Adapter adapter) {
            this.adapter = Objects.requireNonNull(adapter);
        }
        public void run() {
            adapters.remove(this);
        }
    }
    private static final java.util.List<Registration> adapters =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    public static void attach(Adapter a) {
        attachScoped(a);
    }
    public static Runnable attachScoped(Adapter a) {
        var registration = new Registration(a);
        adapters.add(registration);
        return registration;
    }
    public static void clear() {
        adapters.clear();
    }
    public static boolean active(Kind k) {
        for (var a : adapters)
            if (a.adapter.handles(k))
                return true;
        return false;
    }
    static Action post(Action e) {
        for (var a : adapters) {
            if (a.adapter.handles(e.kind))
                a.adapter.accept(e);
        }
        return e;
    }
    static Action player(Kind k, Player p) {
        var e = new Action(k);
        e.player = p;
        e.level = p.level();
        return e;
    }
    private static final Action FORBIDDEN = new Action(Kind.BREAK);
    public static Action breaking(net.minecraft.world.item.ItemStack tool, BlockState state, Level level,
                                  BlockPos pos, Player player) {
        boolean vanilla = tool.canDestroyBlock(state, level, pos, player);
        if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel) || p.level() != level ||
            p.gameMode == null || !active(Kind.BREAK))
            return vanilla ? null : FORBIDDEN;
        var e = player(Kind.BREAK, player);
        e.pos = pos;
        e.state = state;
        e.initiallyDenied =
            !vanilla || p.blockActionRestricted(level, pos, p.gameMode.getGameModeForPlayer());
        post(e);
        if (e.cancelled)
            resync(player, pos);
        return e;
    }
    public static boolean canBreak(Action action) {
        return action == null || action != FORBIDDEN && !action.cancelled;
    }
    private static final ThreadLocal<Action> replacingXP = new ThreadLocal<>();
    public static boolean skipBlockDrop(Level level, BlockPos pos) {
        var e = replacingXP.get();
        return e != null && !e.dropItems && e.level == level && e.pos.equals(pos);
    }
    public static boolean replaceXP(Level level, BlockPos pos) {
        var e = replacingXP.get();
        return e != null && e.experience != null && e.level == level && e.pos.equals(pos);
    }
    public static void harvested(net.minecraft.world.level.block.Block block, Level level, Player player,
                                 BlockPos pos, BlockState state,
                                 net.minecraft.world.level.block.entity.BlockEntity be,
                                 net.minecraft.world.item.ItemStack tool, Action decision) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer) || decision == null ||
            (decision.experience == null && decision.dropItems)) {
            block.playerDestroy(level, player, pos, state, be, tool);
            return;
        }
        var previous = replacingXP.get();
        replacingXP.set(decision);
        try {
            block.playerDestroy(level, player, pos, state, be, tool);
        } finally {
            if (previous == null)
                replacingXP.remove();
            else
                replacingXP.set(previous);
        }
        if (decision.experience != null) net.minecraft.world.level.block.UnityBlockExperience.award(block, server, pos, decision.experience);
    }
    public static void resync(Player p, BlockPos... positions) {
        if (!(p instanceof net.minecraft.server.level.ServerPlayer sp) || sp.connection == null)
            return;
        for (var pos : positions) {
            if (!sp.level().hasChunkAt(pos))
                continue;
            sp.connection.send(
                new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(sp.level(), pos));
            var be = sp.level().getBlockEntity(pos);
            if (be != null) {
                var update = be.getUpdatePacket();
                if (update != null)
                    sp.connection.send(update);
            }
        }
        sp.containerMenu.sendAllDataToRemote();
    }
    public static Action useBlock(Player p, InteractionHand hand, BlockHitResult hit) {
        if (!active(Kind.RIGHT_BLOCK))
            return null;
        var e = player(Kind.RIGHT_BLOCK, p);
        e.pos = hit.getBlockPos();
        e.hand = hand;
        e.hit = hit;
        e.face = hit.getDirection();
        post(e);
        if (e.cancelled)
            resync(p, e.pos, e.pos.relative(e.face));
        return e;
    }
    public static boolean skipBlock(boolean vanilla, Action e) {
        return e == null || e.blockPolicy == 0 ? vanilla : e.blockPolicy < 0;
    }
    public static boolean skipItem(boolean vanilla, Action e) {
        return e == null || e.itemPolicy == 0 ? vanilla : e.itemPolicy < 0;
    }
    public static InteractionResult useItem(Player p, InteractionHand hand) {
        if (!active(Kind.RIGHT_ITEM))
            return null;
        var e = player(Kind.RIGHT_ITEM, p);
        e.hand = hand;
        post(e);
        if (!e.cancelled)
            return null;
        resync(p);
        return e.result;
    }
    public static Action left(Player p, BlockPos pos, Direction side, String phase) {
        if (!active(Kind.LEFT_BLOCK))
            return null;
        var e = player(Kind.LEFT_BLOCK, p);
        e.pos = pos;
        e.face = side;
        e.phase = phase;
        e.hand = InteractionHand.MAIN_HAND;
        post(e);
        if (e.cancelled || e.itemPolicy < 0)
            resync(p, pos);
        return e;
    }
    public static Action leftServer(Player p, BlockPos pos, Direction dir, String action) {
        if (!p.isWithinBlockInteractionRange(pos, 1) || !p.level().isInWorldBounds(pos))
            return null;
        return left(p, pos, dir,
                    action.startsWith("STOP")    ? "STOP"
                    : action.startsWith("ABORT") ? "ABORT"
                                                 : "START");
    }
    public static boolean stopMining(Action e) {
        return e != null && (e.cancelled || e.itemPolicy < 0);
    }
    public static void blockAttack(BlockState state, Level level, BlockPos pos, Player player, Action e) {
        if (e == null || e.blockPolicy >= 0)
            state.attack(level, pos, player);
    }
    public static InteractionResult cancelResult(Action e) {
        return e != null && e.cancelled ? e.result : null;
    }
    public static boolean blockDenied(Action action) {
        return action != null && action.blockPolicy < 0;
    }
    public static void clientBlockAttack(BlockState state, Level level, BlockPos pos, Player player,
                                         boolean denied) {
        if (!denied)
            state.attack(level, pos, player);
    }
    public static void emptyLeft(Player p) {
        if (active(Kind.LEFT_EMPTY)) {
            var e = player(Kind.LEFT_EMPTY, p);
            e.hand = InteractionHand.MAIN_HAND;
            post(e);
        }
    }
    public static void emptyRight(net.minecraft.world.item.ItemStack stack, Player p, InteractionHand hand,
                                  HitResult hit) {
        if (stack.isEmpty() && (hit == null || hit.getType() == HitResult.Type.MISS) &&
            active(Kind.RIGHT_EMPTY)) {
            var e = player(Kind.RIGHT_EMPTY, p);
            e.hand = hand;
            post(e);
        }
    }
    private static void resyncEntity(Player actor, Entity entity) {
        resync(actor);
        if (actor instanceof net.minecraft.server.level.ServerPlayer sp && entity instanceof
                                                                               LivingEntity living) {
            var list = new java.util.ArrayList<com.mojang.datafixers.util.Pair<
                net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack>>();
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
                list.add(com.mojang.datafixers.util.Pair.of(slot, living.getItemBySlot(slot).copy()));
            sp.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(entity.getId(), list));
        }
    }
    public static boolean attackEntity(Player p, Entity target) {
        if (!active(Kind.ATTACK_ENTITY))
            return false;
        var e = player(Kind.ATTACK_ENTITY, p);
        e.target = target;
        post(e);
        if (e.cancelled)
            resyncEntity(p, target);
        return e.cancelled;
    }
    public static InteractionResult interactEntity(Player p, Entity target, InteractionHand hand,
                                                   Vec3 location) {
        if (!active(Kind.INTERACT_ENTITY))
            return null;
        var e = player(Kind.INTERACT_ENTITY, p);
        e.target = target;
        e.hand = hand;
        e.location = location;
        post(e);
        if (!e.cancelled)
            return null;
        resyncEntity(p, target);
        return e.result;
    }
    private static float damage(Kind kind, LivingEntity target, DamageSource source, float amount) {
        if (!active(kind))
            return amount;
        var e = new Action(kind);
        e.target = target;
        e.level = target.level();
        e.source = source;
        e.amount = amount;
        post(e);
        if (e.cancelled && target instanceof net.minecraft.server.level.ServerPlayer sp) {
            var f = sp.getFoodData();
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                sp.getHealth(), f.getFoodLevel(), f.getSaturationLevel()));
        }
        return e.cancelled ? -1 : Math.max(0, e.amount);
    }
    public static float attackAmount(LivingEntity t, DamageSource s, float a) {
        return damage(Kind.ATTACK, t, s, a);
    }
    public static boolean attacked(LivingEntity t, DamageSource s, float a) {
        return damage(Kind.ATTACK, t, s, a) < 0;
    }
    public static float hurt(LivingEntity t, DamageSource s, float a) {
        return Math.max(0, damage(Kind.HURT, t, s, a));
    }
    public static float damaged(LivingEntity t, DamageSource s, float a) {
        return damage(Kind.DAMAGE, t, s, a);
    }
}
