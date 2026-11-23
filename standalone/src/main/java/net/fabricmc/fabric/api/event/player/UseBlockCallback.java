package net.fabricmc.fabric.api.event.player;
import mc.unity.adapter.fabric.SessionEvent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
public interface UseBlockCallback {
    InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hit);
    Event<UseBlockCallback> EVENT =
        new SessionEvent<>(UseBlockCallback.class, callbacks -> (player, level, hand, hit) -> {
            for (var cb : callbacks) {
                var result = cb.interact(player, level, hand, hit);
                if (result != InteractionResult.PASS)
                    return result;
            }
            return InteractionResult.PASS;
        });
}
