package mc.unity.client;
import java.lang.invoke.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.fabricmc.fabric.api.event.player.*;

public final class Interactions {
    private static final MethodHandle predict;
    static {
        try {
            predict = MethodHandles.privateLookupIn(MultiPlayerGameMode.class, MethodHandles.lookup())
                          .findVirtual(
                              MultiPlayerGameMode.class, "startPrediction",
                              MethodType.methodType(void.class, ClientLevel.class, PredictiveAction.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    public static InteractionResult use(MultiPlayerGameMode mode, Player player, InteractionHand hand,
                                        BlockHitResult hit) {
        if (mc.unity.adapter.fabric.LoaderView.upstream())
            return null;
        if (!((mc.unity.adapter.fabric.SessionEvent<?>)(hit == null ? UseItemCallback.EVENT
                                                                    : UseBlockCallback.EVENT))
                 .subscribed())
            return null;
        if (hit != null && player.isSpectator())
            return null;
        var level = (ClientLevel)player.level();
        var result = hit == null ? UseItemCallback.EVENT.invoker().interact(player, level, hand)
                                 : UseBlockCallback.EVENT.invoker().interact(player, level, hand, hit);
        if (result == InteractionResult.PASS)
            return null;
        if (result.consumesAction()) {
            PredictiveAction send = seq
                -> hit == null ? new ServerboundUseItemPacket(hand, seq, player.getYRot(), player.getXRot())
                               : new ServerboundUseItemOnPacket(hand, hit, seq);
            try {
                predict.invokeExact(mode, level, send);
            } catch (RuntimeException | Error ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new IllegalStateException(ex);
            }
        }
        return result;
    }
}
