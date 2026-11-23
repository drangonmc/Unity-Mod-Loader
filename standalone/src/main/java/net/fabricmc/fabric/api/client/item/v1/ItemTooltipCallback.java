package net.fabricmc.fabric.api.client.item.v1;
import java.util.ArrayList;
import java.util.List;
import mc.unity.core.Events;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
public interface ItemTooltipCallback {
    void getTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines);
    Event<ItemTooltipCallback> EVENT = new Event<>() {
        public void register(ItemTooltipCallback cb) {
            Events.TOOLTIP.listen(e -> cb.getTooltip(e.stack(), e.context(), e.flag(), e.lines()));
        }
    };
}
