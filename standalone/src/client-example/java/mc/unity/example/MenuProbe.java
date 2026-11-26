package mc.unity.example;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Exercises the active vanilla/mod screen, the normal multiplayer click path, and the mod's own widgets. */
final class MenuProbe {
    static boolean apply(Minecraft mc, String action, JsonObject request, JsonObject out, boolean first) {
        var player = mc.player;
        if (action.equals("menuOpen") && first) {
            if (player.containerMenu != player.inventoryMenu)
                throw new IllegalStateException("Another menu is open");
            player.getInventory().setSelectedSlot(request.get("handSlot").getAsInt());
            BlockPos pos = new BlockPos(
                request.get("x").getAsInt(), request.get("y").getAsInt(), request.get("z").getAsInt());
            var aim = Vec3.atCenterOf(pos);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
            var hit = mc.level.clip(new ClipContext(
                player.getEyePosition(), aim, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)
                || !player.isWithinBlockInteractionRange(pos, 0))
                throw new IllegalStateException("Container is not reachable: " + pos);
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);
        }
        if (action.equals("menuClose")) {
            if (first && mc.gui.screen() instanceof AbstractContainerScreen<?> screen)
                screen.onClose();
            return player.containerMenu == player.inventoryMenu && mc.gui.screen() == null;
        }
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)
            || player.containerMenu == player.inventoryMenu) {
            if (action.equals("menuOpen"))
                return false;
            throw new IllegalStateException("Expected an open container screen");
        }
        var menu = screen.getMenu();
        if (menu != player.containerMenu)
            throw new IllegalStateException("Menu identity changed");
        out.addProperty("menu", BuiltInRegistries.MENU.getKey(menu.getType()).toString());
        out.addProperty("screen", screen.getClass().getName());
        out.addProperty("containerId", menu.containerId);
        out.addProperty("stateId", menu.getStateId());
        if (menu.getClass().getName().equals(
                "com.nottouchedgrass.inventorystorages.menus.PlayerSlotStorageMenu")) {
            try {
                var data = (ContainerData) menu.getClass().getField("data").get(menu);
                var values = new JsonArray();
                for (int i = 0; i < data.getCount(); i++) values.add(data.get(i));
                out.add("syncedData", values);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        if (action.equals("menuClick")) {
            int index = request.get("index").getAsInt();
            if (request.has("inventory")) {
                int wanted = request.get("inventory").getAsInt();
                index = menu.slots.stream()
                            .filter(s -> s.container == player.getInventory() && s.getContainerSlot() == wanted)
                            .findFirst()
                            .orElseThrow()
                            .index;
            }
            if (index < 0 || index >= menu.slots.size())
                throw new IllegalArgumentException("Invalid slot " + index);
            mc.gameMode.handleContainerInput(
                menu.containerId, index, request.get("button").getAsInt(), ContainerInput.PICKUP, player);
            out.addProperty("clicked", index);
        } else if (action.equals("menuDrag")) {
            int type = request.get("button").getAsInt();
            for (int phase = 0; phase < 3; phase++) {
                int mask = net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(phase, type);
                if (phase == 1) {
                    for (var index : request.getAsJsonArray("indices"))
                        mc.gameMode.handleContainerInput(menu.containerId, index.getAsInt(), mask, ContainerInput.QUICK_CRAFT, player);
                } else {
                    mc.gameMode.handleContainerInput(menu.containerId, -999, mask, ContainerInput.QUICK_CRAFT, player);
                }
            }
        } else if (action.equals("menuEdit") || action.equals("menuButton")) {
            int nth = request.has("nth") ? request.get("nth").getAsInt() : 0;
            var widget = screen.children()
                             .stream()
                             .filter(w -> w instanceof AbstractWidget)
                             .map(w -> (AbstractWidget) w)
                             .filter(w -> w.visible && w.active)
                             .filter(w
                                 -> action.equals("menuEdit") ? w instanceof EditBox
                                     : request.has("label")
                                     ? w.getMessage().getString().equals(request.get("label").getAsString())
                                     : w instanceof CycleButton)
                             .skip(nth)
                             .findFirst()
                             .orElseThrow(() -> new IllegalStateException("No matching active widget"));
            var mouse = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(0, 0));
            if (!screen.mouseClicked(mouse, false))
                throw new IllegalStateException("Widget refused the click");
            screen.mouseReleased(mouse);
            if (widget instanceof EditBox box) {
                box.setValue(request.get("value").getAsString());
                out.addProperty("value", box.getValue());
            }
            out.addProperty("widget", widget.getClass().getName());
            out.addProperty("label", widget.getMessage().getString());
        } else if (!action.equals("menuOpen") && !action.equals("menuInfo"))
            throw new IllegalArgumentException(action);
        JsonArray slots = new JsonArray();
        for (var slot : menu.slots) {
            var value = stack(slot.getItem());
            value.addProperty("index", slot.index);
            value.addProperty(
                "inventory", slot.container == player.getInventory() ? slot.getContainerSlot() : -1);
            slots.add(value);
        }
        out.add("slots", slots);
        out.add("carried", stack(menu.getCarried()));
        return true;
    }
    private static JsonObject stack(ItemStack stack) {
        JsonObject r = new JsonObject();
        r.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        r.addProperty("count", stack.getCount());
        return r;
    }
}
