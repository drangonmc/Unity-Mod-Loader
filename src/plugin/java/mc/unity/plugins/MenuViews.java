package mc.unity.plugins;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;

final class MenuViews {
    private final Host host;
    final ServerPlayer player;
    final AbstractContainerMenu menu;
    final InventoryView view;
    final PlayerInventory bottom;
    final Inventory top;
    final List<Slot> upperSlots;
    final org.bukkit.entity.Player owner;
    final InventoryType type;

    MenuViews(Host host, ServerPlayer player) {
        this(host,player,player.containerMenu,null);
    }
    MenuViews(Host host,ServerPlayer player,AbstractContainerMenu candidate,net.minecraft.network.chat.Component title) {
        this.host=host;this.player=player;
        menu=candidate;
        upperSlots = menu.slots.stream().filter(s -> s.container != player.getInventory()).toList();
        owner = host.views.player(player);
        type = type(menu);
        bottom = Views.of(PlayerInventory.class, "player inventory", player.getUUID(), (m,a) -> inventory(false,m,a));
        top = Views.of(Inventory.class, type.name(), menu, (m,a) -> inventory(true,m,a));
        view = Views.of(InventoryView.class, type.name(), menu, (m,a) -> switch (m) {
            case "getTitle","getOriginalTitle" -> title==null?type.name():StackMeta.legacy(title);
            case "title" -> title==null?net.kyori.adventure.text.Component.text(type.name()):host.adventure(title);
            case "getPlayer" -> owner;
            case "getTopInventory" -> top;
            case "getBottomInventory" -> bottom;
            case "getType" -> type;
            case "countSlots" -> menu.slots.size();
            case "getInventory" -> !valid((int)a[0]) ? null : playerSlot((int)a[0]) ? bottom : top;
            case "convertSlot" -> valid((int)a[0]) && playerSlot((int)a[0]) ? menu.getSlot((int)a[0]).getContainerSlot() : valid((int)a[0]) ? upperSlots.indexOf(menu.getSlot((int)a[0])) : (int)a[0];
            case "getSlotType" -> slotType((int)a[0]);
            case "getItem" -> valid((int)a[0]) ? slotItem(menu.getSlot((int)a[0])) : null;
            case "setItem" -> { menu.getSlot((int)a[0]).setByPlayer(nativeItem(a[1])); yield null; }
            case "getCursor" -> BlockViews.Stack.mirror(menu.getCarried(), menu::broadcastChanges);
            case "setCursor" -> { menu.setCarried(nativeItem(a[0])); yield null; }
            case "close" -> { player.closeContainer(); yield null; }
            default -> throw Views.unsupported("InventoryView", m);
        });
    }
    private static InventoryType type(AbstractContainerMenu m) {
        if (m instanceof InventoryMenu) return InventoryType.CRAFTING;
        if (m instanceof ChestMenu c) return c.getContainer() instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity ? InventoryType.BARREL : InventoryType.CHEST;
        var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(m.getType()).getPath();
        return switch(id) {
            case "generic_3x3" -> InventoryType.DISPENSER;
            case "generic_9x1","generic_9x2","generic_9x3","generic_9x4","generic_9x5","generic_9x6" -> InventoryType.CHEST;
            case "crafting" -> InventoryType.WORKBENCH;
            case "merchant" -> InventoryType.MERCHANT;
            default -> { try { yield InventoryType.valueOf(id.toUpperCase(Locale.ROOT)); }
                         catch (IllegalArgumentException unknown) { yield InventoryType.CHEST; } }
        };
    }
    boolean valid(int raw) { return raw >= 0 && raw < menu.slots.size(); }
    private boolean playerSlot(int raw) { return menu.getSlot(raw).container == player.getInventory(); }
    SlotType slotType(int raw) {
        if (!valid(raw)) return SlotType.OUTSIDE;
        var slot = menu.getSlot(raw);
        if (slot instanceof ResultSlot || slot instanceof FurnaceResultSlot || slot instanceof MerchantResultSlot)
            return SlotType.RESULT;
        if (playerSlot(raw)) {
            int index = slot.getContainerSlot();
            if (index >= 36 && index < 40) return SlotType.ARMOR;
            if (index < 9 || index == 40) return SlotType.QUICKBAR;
        }
        if (type == InventoryType.CRAFTING || type == InventoryType.WORKBENCH)
            if (!playerSlot(raw)) return SlotType.CRAFTING;
        if (menu instanceof AbstractFurnaceMenu && raw == 1) return SlotType.FUEL;
        return SlotType.CONTAINER;
    }
    private Object inventory(boolean upper,String m,Object[] a) {
        return switch(m) {
            case "getType" -> upper ? type : InventoryType.PLAYER;
            case "getSize" -> upper ? upperSlots.size() : player.getInventory().getContainerSize();
            case "getLocation" -> {
                if(!upper)yield owner.getLocation();
                var container=upperSlots.isEmpty()?null:upperSlots.getFirst().container;
                if(container instanceof net.minecraft.world.level.block.entity.BlockEntity be){
                    var pos=be.getBlockPos();yield new org.bukkit.Location(host.views.world(player.level()),pos.getX(),pos.getY(),pos.getZ());
                }yield null;
            }
            case "getHolder" -> {
                if(!upper)yield owner;
                if(!upperSlots.isEmpty() && upperSlots.getFirst().container instanceof net.minecraft.world.level.block.entity.BlockEntity be){
                    var block=host.views.blocks.block(player.level(),be.getBlockPos());
                    Class<? extends org.bukkit.block.Container> holder=be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity?org.bukkit.block.Chest.class:
                        be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity?org.bukkit.block.Barrel.class:org.bukkit.block.Container.class;
                    yield Views.of(holder,"container",be,(name,args)->switch(name){
                        case "getInventory","getBlockInventory","getSnapshotInventory"->top;
                        case "getBlock"->block;case "getLocation"->block.getLocation();case "getX"->block.getX();case "getY"->block.getY();case "getZ"->block.getZ();
                        case "getWorld"->block.getWorld();case "getType"->block.getType();case "isPlaced"->true;default->throw Views.unsupported("Container",name);
                    });
                }
                yield null;
            }
            case "getContents","getStorageContents" -> {
                int size=upper?upperSlots.size():m.equals("getStorageContents")?36:player.getInventory().getContainerSize();
                var array=new org.bukkit.inventory.ItemStack[size];
                for(int i=0;i<size;i++)array[i]=upper?slotItem(upperSlots.get(i)):playerItem(player.getInventory().getItem(i));yield array;
            }
            case "setContents","setStorageContents" -> {
                var array=(org.bukkit.inventory.ItemStack[])a[0];
                int size=upper?upperSlots.size():m.equals("setStorageContents")?36:player.getInventory().getContainerSize();
                if(array.length>size)throw new IllegalArgumentException("Too many inventory slots");
                var copies=new ItemStack[size];
                for(int i=0;i<size;i++)copies[i]=i<array.length?nativeItem(array[i]):ItemStack.EMPTY;
                for(int i=0;i<size;i++)if(upper)upperSlots.get(i).setByPlayer(copies[i]);else player.getInventory().setItem(i,copies[i]);
                menu.broadcastChanges();yield null;
            }
            case "getViewers" -> {
                if(!upper)yield List.of(owner);
                var containers=Collections.newSetFromMap(new IdentityHashMap<net.minecraft.world.Container,Boolean>());
                upperSlots.forEach(slot->containers.add(slot.container));
                yield player.level().getServer().getPlayerList().getPlayers().stream()
                    .filter(viewer->viewer.containerMenu!=viewer.inventoryMenu && viewer.containerMenu.slots.stream().anyMatch(s->containers.contains(s.container)))
                    .map(viewer->Host.active().views.player(viewer)).toList();
            }
            case "getItem" -> a[0] instanceof org.bukkit.inventory.EquipmentSlot slot ?
                playerItem(player.getItemBySlot(equipment(slot))) :
                upper ? ((int)a[0] >= 0 && (int)a[0] < upperSlots.size() ? slotItem(upperSlots.get((int)a[0])) : null) : playerItem(player.getInventory().getItem((int)a[0]));
            case "setItem" -> {
                if(a[0] instanceof org.bukkit.inventory.EquipmentSlot slot)
                    player.setItemSlot(equipment(slot),nativeItem(a[1]));
                else if (upper) upperSlots.get((int)a[0]).setByPlayer(nativeItem(a[1]));
                else player.getInventory().setItem((int)a[0],nativeItem(a[1]));
                yield null;
            }
            case "getHelmet" -> playerItem(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
            case "getChestplate" -> playerItem(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
            case "getLeggings" -> playerItem(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
            case "getBoots" -> playerItem(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            case "getArmorContents" -> new org.bukkit.inventory.ItemStack[]{
                playerItem(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)),
                playerItem(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)),
                playerItem(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)),
                playerItem(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD))};
            case "getItemInMainHand" -> BlockViews.Stack.mirror(player.getMainHandItem(), player.getInventory()::setChanged);
            case "getItemInOffHand" -> BlockViews.Stack.mirror(player.getOffhandItem(), player.getInventory()::setChanged);
            case "getHeldItemSlot" -> player.getInventory().getSelectedSlot();
            default -> throw Views.unsupported("Inventory",m);
        };
    }
    private static net.minecraft.world.entity.EquipmentSlot equipment(org.bukkit.inventory.EquipmentSlot slot) {
        return switch(slot){
            case HAND -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case OFF_HAND -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            default -> net.minecraft.world.entity.EquipmentSlot.valueOf(slot.name());
        };
    }
    private BlockViews.Stack playerItem(ItemStack stack) {
        return stack.isEmpty() ? null : BlockViews.Stack.mirror(stack, player.getInventory()::setChanged);
    }
    private static BlockViews.Stack slotItem(Slot slot) {
        return slot.hasItem() ? BlockViews.Stack.mirror(slot.getItem(), slot::setChanged) : null;
    }
    static BlockViews.Stack item(ItemStack s) { return s.isEmpty() ? null : new BlockViews.Stack(s); }
    static ItemStack nativeItem(Object value) { return BlockViews.Stack.nativeCopy((org.bukkit.inventory.ItemStack)value); }
}
