package qa.bukkit;

import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModBlockGuard extends JavaPlugin implements Listener {
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void guard(BlockBreakEvent e) {
        var block = e.getBlock();
        if (block.getX() != 44 || block.getY() != 201 || block.getZ() != 18)
            return;
        String state = block.getBlockData().getAsString();
        if (state.equals("stackedblocks:stacked_cobblestone_blocks") ||
            state.startsWith("stackedblocks:stacked_cobblestone_blocks[")) {
            e.setCancelled(true);
            getLogger().info("QA24 MODDED BREAK CANCELLED " + state);
        }
    }
}
