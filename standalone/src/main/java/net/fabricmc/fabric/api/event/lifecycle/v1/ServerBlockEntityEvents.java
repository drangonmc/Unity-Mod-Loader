package net.fabricmc.fabric.api.event.lifecycle.v1;
import mc.unity.adapter.fabric.SessionEvent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
public final class ServerBlockEntityEvents {
    public interface Load {
        void onLoad(BlockEntity entity, ServerLevel level);
    }
    public interface Unload {
        void onUnload(BlockEntity entity, ServerLevel level);
    }
    public static final Event<Load> BLOCK_ENTITY_LOAD = new SessionEvent<>(Load.class, list -> (e, s) -> {
        for (var cb : list)
            cb.onLoad(e, s);
    });
    public static final Event<Unload> BLOCK_ENTITY_UNLOAD =
        new SessionEvent<>(Unload.class, list -> (e, s) -> {
            for (var cb : list)
                cb.onUnload(e, s);
        });
}
