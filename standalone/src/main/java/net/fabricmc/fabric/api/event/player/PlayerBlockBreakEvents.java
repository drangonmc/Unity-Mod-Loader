package net.fabricmc.fabric.api.event.player;
import mc.unity.core.Actions;
import mc.unity.core.Events;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class PlayerBlockBreakEvents {
    public interface Before {
        boolean beforeBlockBreak(Level world, Player player, BlockPos pos, BlockState state,
                                 BlockEntity entity);
    }
    public static final Event<Before> BEFORE = new Event<>() {
        public void register(Before cb) {
            Actions.attach(new Actions.Adapter() {
                public boolean handles(Actions.Kind kind) { return kind == Actions.Kind.BREAK; }
                public void accept(Actions.Action a) {
                    a.cancelled =
                        !cb.beforeBlockBreak(a.level, a.player, a.pos, a.state, a.level.getBlockEntity(a.pos));
                }
            });
        }
    };
}
