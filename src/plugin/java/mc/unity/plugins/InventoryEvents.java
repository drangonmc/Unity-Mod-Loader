package mc.unity.plugins;

import java.lang.invoke.*;
import java.util.*;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.bukkit.event.inventory.*;
import static org.bukkit.event.inventory.InventoryAction.*;

/** Runs after vanilla menu, life-state and slot checks, before accepting client predictions. */
final class InventoryEvents {
    private static final VarHandle STATUS, TYPE, SLOTS;
    private static final MethodHandle RESET;
    static {
        try {
            var lookup = MethodHandles.privateLookupIn(AbstractContainerMenu.class, MethodHandles.lookup());
            STATUS = lookup.findVarHandle(AbstractContainerMenu.class,"quickcraftStatus",int.class);
            TYPE = lookup.findVarHandle(AbstractContainerMenu.class,"quickcraftType",int.class);
            SLOTS = lookup.findVarHandle(AbstractContainerMenu.class,"quickcraftSlots",Set.class);
            RESET = lookup.findVirtual(AbstractContainerMenu.class,"resetQuickCraft",MethodType.methodType(void.class));
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    static boolean input(Host host, ServerPlayer player, ServerboundContainerClickPacket packet) {
        var menu = player.containerMenu;
        int raw = packet.slotNum(), button = packet.buttonNum();
        if (packet.containerInput() == ContainerInput.QUICK_CRAFT) {
            if (AbstractContainerMenu.getQuickcraftHeader(button) != 2 ||
                InventoryDragEvent.getHandlerList().getRegisteredListeners().length == 0 ||
                (int)STATUS.get(menu) != 1 || menu.getCarried().isEmpty()) return false;
            return drag(host,player,menu);
        }
        if (InventoryClickEvent.getHandlerList().getRegisteredListeners().length == 0) return false;
        var views = new MenuViews(host,player);
        var click = switch(packet.containerInput()) {
            case PICKUP -> button == 0 ? ClickType.LEFT : ClickType.RIGHT;
            case QUICK_MOVE -> button == 0 ? ClickType.SHIFT_LEFT : ClickType.SHIFT_RIGHT;
            case SWAP -> button == 40 ? ClickType.SWAP_OFFHAND : ClickType.NUMBER_KEY;
            case CLONE -> ClickType.MIDDLE;
            case THROW -> button == 0 ? ClickType.DROP : ClickType.CONTROL_DROP;
            case PICKUP_ALL -> ClickType.DOUBLE_CLICK;
            default -> ClickType.UNKNOWN;
        };
        var slot = views.valid(raw) ? menu.getSlot(raw) : null;
        var action = action(player, menu.getCarried(), slot, raw, click);
        var event = new InventoryClickEvent(views.view, views.slotType(raw), raw, click, action,
                                            click == ClickType.NUMBER_KEY ? button : -1);
        host.fire(event);
        return event.isCancelled() || player.containerMenu != menu;
    }
    private static InventoryAction action(ServerPlayer p,ItemStack cursor,Slot slot,int raw,ClickType click) {
        if (slot == null) {
            if (raw != -999 || cursor.isEmpty()) return NOTHING;
            return click == ClickType.LEFT ? DROP_ALL_CURSOR : click == ClickType.RIGHT ? DROP_ONE_CURSOR : NOTHING;
        }
        var stack = slot.getItem();
        return switch(click) {
            case LEFT, RIGHT -> {
                boolean one = click == ClickType.RIGHT;
                if (cursor.isEmpty()) yield stack.isEmpty() || !slot.mayPickup(p) ? NOTHING : one ? PICKUP_HALF : PICKUP_ALL;
                if (!slot.mayPlace(cursor)) yield !stack.isEmpty() && slot.mayPickup(p) && ItemStack.isSameItemSameComponents(stack,cursor) &&
                    cursor.getCount()+stack.getCount() <= cursor.getMaxStackSize() ? PICKUP_ALL : NOTHING;
                if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack,cursor))
                    yield slot.mayPickup(p) && cursor.getCount() <= slot.getMaxStackSize(cursor) ? SWAP_WITH_CURSOR : NOTHING;
                int room = slot.getMaxStackSize(cursor)-stack.getCount();
                yield room <= 0 ? NOTHING : one ? PLACE_ONE : room >= cursor.getCount() ? PLACE_ALL : PLACE_SOME;
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> !stack.isEmpty() && slot.mayPickup(p) ? MOVE_TO_OTHER_INVENTORY : NOTHING;
            case DROP, CONTROL_DROP -> stack.isEmpty() || !slot.mayPickup(p) ? NOTHING : click == ClickType.DROP ? DROP_ONE_SLOT : DROP_ALL_SLOT;
            case NUMBER_KEY, SWAP_OFFHAND -> slot.mayPickup(p) ? HOTBAR_SWAP : NOTHING;
            case MIDDLE -> p.getAbilities().instabuild && cursor.isEmpty() && !stack.isEmpty() ? CLONE_STACK : NOTHING;
            case DOUBLE_CLICK -> !cursor.isEmpty() ? COLLECT_TO_CURSOR : NOTHING;
            default -> UNKNOWN;
        };
    }
    @SuppressWarnings("unchecked")
    private static boolean drag(Host host, ServerPlayer player, AbstractContainerMenu menu) {
        Set<Slot> selected = (Set<Slot>)SLOTS.get(menu);
        if (selected.isEmpty()) return false;
        var old = menu.getCarried().copy();
        int type = (int)TYPE.get(menu);
        if (!AbstractContainerMenu.isValidQuickcraftType(type, player)) return false;
        int left = old.getCount(), count = selected.size();
        var changes = new LinkedHashMap<Slot,ItemStack>();
        var proposed = new LinkedHashMap<Integer,org.bukkit.inventory.ItemStack>();
        for (var slot : selected) {
            if (!AbstractContainerMenu.canItemQuickReplace(slot,old,true) || !slot.mayPlace(old) ||
                !menu.canDragTo(slot) || (type != 2 && old.getCount() < count)) continue;
            int before = slot.getItem().getCount();
            int after = Math.min(Math.min(old.getMaxStackSize(),slot.getMaxStackSize(old)),
                                 before + AbstractContainerMenu.getQuickCraftPlaceCount(count,type,old));
            if (after <= before) continue;
            var next = old.copyWithCount(after);
            changes.put(slot,next);
            proposed.put(slot.index,new BlockViews.Stack(next));
            left -= after-before;
        }
        try { RESET.invokeExact(menu); } catch (Throwable e) {throw new IllegalStateException(e);}
        if (changes.isEmpty()) return true;
        var event = new InventoryDragEvent(new MenuViews(host,player).view,
            MenuViews.item(old.copyWithCount(Math.max(0,left))),new BlockViews.Stack(old),type == 1, proposed);
        host.fire(event);
        if (!event.isCancelled() && player.containerMenu == menu) {
            changes.forEach(Slot::setByPlayer);
            menu.setCarried(MenuViews.nativeItem(event.getCursor()));
        }
        return true;
    }
}
