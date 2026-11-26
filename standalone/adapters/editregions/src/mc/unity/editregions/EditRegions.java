package mc.unity.editregions;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Applies the region build rule to the actual destination of each WorldEdit block write. */
public final class EditRegions extends JavaPlugin {
    @Override public void onEnable() { WorldEdit.getInstance().getEventBus().register(this); }
    @Override public void onDisable() { WorldEdit.getInstance().getEventBus().unregister(this); }

    @Subscribe public void editing(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || event.getWorld() == null) return;
        var world = event.getWorld();
        var actor = event.getActor();
        LocalPlayer player = null;
        if (actor instanceof com.sk89q.worldedit.entity.Player) {
            var online = Bukkit.getPlayer(actor.getUniqueId());
            if (online != null) player = WorldGuardPlugin.inst().wrapPlayer(online);
        }
        var subject = player;
        var platform = WorldGuard.getInstance().getPlatform();
        var query = platform.getRegionContainer().createQuery();
        event.setExtent(new AbstractDelegateExtent(event.getExtent()) {
            @Override public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 pos, T value)
                throws WorldEditException {
                var location = new Location(world, pos.x(), pos.y(), pos.z());
                if ((subject == null || !platform.getSessionManager().hasBypass(subject, world))
                        && !query.testBuild(location, subject)) return false;
                return super.setBlock(pos, value);
            }
        });
    }
}
