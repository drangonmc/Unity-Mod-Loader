package mc.unity.adapter.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import java.util.UUID;
import java.util.function.BiPredicate;
import com.sk89q.worldedit.bukkit.BukkitAdapter;

public final class RegionEdits implements AutoCloseable {
    private final BiPredicate<UUID,org.bukkit.Location> allowed;
    private RegionEdits(BiPredicate<UUID,org.bukkit.Location> allowed){this.allowed=allowed;}
    public static AutoCloseable install(BiPredicate<UUID,org.bukkit.Location> allowed){
        var listener=new RegionEdits(allowed);
        WorldEdit.getInstance().getEventBus().register(listener);
        return listener;
    }
    @Override public void close(){ WorldEdit.getInstance().getEventBus().unregister(this); }
    @Subscribe public void editing(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || event.getWorld() == null) return;
        var world = event.getWorld();
        var actor = event.getActor();
        UUID subject=actor instanceof com.sk89q.worldedit.entity.Player ? actor.getUniqueId() : null;
        var destination=BukkitAdapter.adapt(world);
        event.setExtent(new AbstractDelegateExtent(event.getExtent()) {
            @Override public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 pos, T value)
                throws WorldEditException {
                if (!allowed.test(subject,new org.bukkit.Location(destination,pos.x(),pos.y(),pos.z())))return false;
                return super.setBlock(pos, value);
            }
        });
    }
}
