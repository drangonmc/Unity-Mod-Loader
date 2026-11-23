package net.fabricmc.fabric.api.event.player;
import mc.unity.adapter.fabric.SessionEvent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
public interface UseItemCallback {
    InteractionResult interact(Player player, Level level, InteractionHand hand);
    Event<UseItemCallback> EVENT =
        new SessionEvent<>(UseItemCallback.class, callbacks -> (player, level, hand) -> {
            for (var cb : callbacks) {
                var result = cb.interact(player, level, hand);
                if (result != InteractionResult.PASS)
                    return result;
            }
            return InteractionResult.PASS;
        });
}
