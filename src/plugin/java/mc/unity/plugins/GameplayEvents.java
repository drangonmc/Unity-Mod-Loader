package mc.unity.plugins;

import mc.unity.core.Actions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.bukkit.block.BlockFace;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class GameplayEvents implements Actions.Adapter {
    private final Host host;
    GameplayEvents(Host host) {
        this.host = host;
    }
    private static boolean subscribed(Class<? extends Event> type) {
        return PluginEvents.handlers(type).getRegisteredListeners().length != 0;
    }
    public boolean handles(Actions.Kind kind) {
        if (host.enabled.isEmpty())
            return false;
        return switch (kind) {
            case BREAK -> subscribed(BlockBreakEvent.class);
            case PLACE -> subscribed(BlockPlaceEvent.class) || subscribed(BlockMultiPlaceEvent.class);
            case INTERACT_ENTITY -> subscribed(org.bukkit.event.player.PlayerInteractEntityEvent.class);
            case LEFT_BLOCK, RIGHT_BLOCK, RIGHT_ITEM -> subscribed(PlayerInteractEvent.class);
            default -> false;
        };
    }
    public void accept(Actions.Action a) {
        if (!(a.player instanceof ServerPlayer player))
            return;
        var level = player.level();
        var p = host.views.player(player);
        var blocks = host.views.blocks;
        var hand = a.hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        switch (a.kind) {
        case BREAK -> {
            var event = new BlockBreakEvent(blocks.block(level, a.pos), p);
            event.setCancelled(a.cancelled || a.initiallyDenied);
            event.setDropItems(a.dropItems);
            int exp = a.experience == null
                          ? net.minecraft.world.level.block.UnityBlockExperience.sample(player, a.state)
                          : a.experience;
            event.setExpToDrop(exp);
            host.fire(event);
            a.cancelled = event.isCancelled();
            a.dropItems = event.isDropItems();
            a.experience = event.getExpToDrop();
        }
        case PLACE -> {
            var snapshots = a.changes.stream().map(c -> blocks.snapshot(level, c.pos(), c.old())).toList();
            var against = blocks.block(level, a.against);
            var item = new BlockViews.Stack(a.usedStack);
            var event = snapshots.size() == 1
                            ? new BlockPlaceEvent(blocks.block(level, a.pos), snapshots.getFirst(), against,
                                                  item, p, true, hand)
                            : new BlockMultiPlaceEvent(snapshots, against, item, p, true, hand);
            event.setCancelled(a.cancelled);
            host.fire(event);
            a.cancelled = event.isCancelled() || !event.canBuild();
        }
        case INTERACT_ENTITY -> {
            var entity = host.views.entity(a.target);
            var e = a.location == null
                        ? new org.bukkit.event.player.PlayerInteractEntityEvent(p, entity, hand)
                        : new org.bukkit.event.player.PlayerInteractAtEntityEvent(
                              p, entity, new org.bukkit.util.Vector(a.location.x, a.location.y, a.location.z),
                              hand);
            e.setCancelled(a.cancelled);
            host.fire(e);
            a.cancelled = e.isCancelled();
            if (e.isCancelled()) {
                a.cancelled = true;
                a.result = InteractionResult.FAIL;
            }
        }
        case LEFT_BLOCK, RIGHT_BLOCK, RIGHT_ITEM -> {
            if (a.kind == Actions.Kind.LEFT_BLOCK && !"START".equals(a.phase))
                return;
            var action = a.kind == Actions.Kind.LEFT_BLOCK    ? Action.LEFT_CLICK_BLOCK
                         : a.kind == Actions.Kind.RIGHT_BLOCK ? Action.RIGHT_CLICK_BLOCK
                                                              : Action.RIGHT_CLICK_AIR;
            var held = player.getItemInHand(a.hand == null ? InteractionHand.MAIN_HAND : a.hand);
            var event = new PlayerInteractEvent(
                p, action, held.isEmpty() ? null : BlockViews.Stack.mirror(held, player.getInventory()::setChanged),
                a.pos == null ? null : blocks.block(level, a.pos),
                a.face == null ? BlockFace.SELF : BlockFace.valueOf(a.face.name()), hand,
                a.hit == null ? null
                              : new org.bukkit.util.Vector(a.hit.getLocation().x - a.pos.getX(),
                                                           a.hit.getLocation().y - a.pos.getY(),
                                                           a.hit.getLocation().z - a.pos.getZ()));
            if (a.blockPolicy != 0)
                event.setUseInteractedBlock(a.blockPolicy < 0 ? Event.Result.DENY : Event.Result.ALLOW);
            if (a.itemPolicy != 0)
                event.setUseItemInHand(a.itemPolicy < 0 ? Event.Result.DENY : Event.Result.ALLOW);
            if (a.cancelled) event.setCancelled(true);
            host.fire(event);
            // The SDK starts block clicks at ALLOW. Preserve vanilla secondary use unless a listener denies it.
            a.blockPolicy = event.useInteractedBlock() == Event.Result.DENY ? -1 : Math.max(0, a.blockPolicy);
            a.itemPolicy = policy(event.useItemInHand());
            a.cancelled = a.itemPolicy < 0 && (a.blockPolicy < 0 || a.kind == Actions.Kind.RIGHT_ITEM);
            if (a.cancelled) {
                a.cancelled = true;
                a.result = InteractionResult.FAIL;
            }
        }
        default -> {
        }
        }
    }
    private static int policy(Event.Result r) {
        return r == Event.Result.DENY ? -1 : r == Event.Result.ALLOW ? 1 : 0;
    }
}
