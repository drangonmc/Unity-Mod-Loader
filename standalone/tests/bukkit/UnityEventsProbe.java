package qa.bukkit;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Compiled against unmodified Paper API. All observable gameplay comes from the server/client. */
public final class UnityEventsProbe extends JavaPlugin implements Listener {
    final List<String> calls = new ArrayList<>();
    public static final class Probe extends Event implements Cancellable {
        static final HandlerList handlers = new HandlerList();
        boolean cancelled;
        Probe(boolean async) {
            super(async);
        }
        public HandlerList getHandlers() {
            return handlers;
        }
        public static HandlerList getHandlerList() {
            return handlers;
        }
        public boolean isCancelled() {
            return cancelled;
        }
        public void setCancelled(boolean c) {
            cancelled = c;
        }
    }
    public void onEnable() {
        var manager = Bukkit.getPluginManager();
        var counter = new AtomicInteger();
        var all = new Listener() {};
        try (var in = new BufferedReader(
                 new InputStreamReader(Objects.requireNonNull(getResource("event-types.tsv"))))) {
            for (String row : in.lines().toList()) {
                var type = Class.forName(row.split("\t")[0], false, getClassLoader()).asSubclass(Event.class);
                manager.registerEvent(type, all, EventPriority.NORMAL, (listener, event) -> {}, this, true);
                counter.incrementAndGet();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (HandlerList.getRegisteredListeners(this).size() != counter.get())
            throw new AssertionError("Registration count");
        HandlerList.unregisterAll(all);
        if (!HandlerList.getRegisteredListeners(this).isEmpty())
            throw new AssertionError("Unregister");
        getLogger().info("QA24 ALL HANDLERLISTS " + counter.get());
        manager.registerEvents(this, this);
        PluginScopeChecks.verify(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (Bukkit.isPrimaryThread())
                throw new AssertionError("Async task on server thread");
            Bukkit.getScheduler().runTask(this, () -> {
                if (!Bukkit.isPrimaryThread())
                    throw new AssertionError("Sync task off server thread");
                getLogger().info("QA24 ASYNC TO SYNC TASK");
            });
        });
        manager.registerEvent(Probe.class, this, EventPriority.HIGHEST,
                              (l, e) -> calls.add("ignored"), this, true);
        if (new Probe(false).callEvent() || !calls.equals(List.of("low", "monitor")))
            throw new AssertionError(calls);
        try {
            new Probe(true).callEvent();
            throw new AssertionError("async on server");
        } catch (IllegalStateException expected) {}
        var thread = new Thread(() -> {
            try {
                new Probe(false).callEvent();
                throw new AssertionError("sync off server");
            } catch (IllegalStateException expected) {}
            new Probe(true).callEvent();
            getLogger().info("QA24 ASYNC OWNER PASS");
        }, "Unity event probe");
        thread.start();
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void low(Probe e) {
        if (!e.isAsynchronous()) {
            calls.add("low");
            e.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    private void monitor(Probe e) {
        if (!e.isAsynchronous())
            calls.add("monitor");
    }
    @EventHandler
    public void flight(PlayerToggleFlightEvent event) {
        if (event.isFlying()) {
            event.setCancelled(true);
            getLogger().info("QA24 FLIGHT DENY");
        }
    }
    @EventHandler
    public void mode(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() == org.bukkit.GameMode.ADVENTURE) {
            e.setCancelled(true);
            getLogger().info("QA24 MODE DENY");
        }
    }
    @EventHandler
    public void level(PlayerLevelChangeEvent e) {
        getLogger().info("QA24 LEVEL " + e.getPlayer().getName() + " " + e.getOldLevel() + " " +
                         e.getNewLevel());
    }
    private boolean sawChunk;
    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        if (!sawChunk) {
            sawChunk = true;
            getLogger().info("QA24 CHUNK LOAD " + e.isNewChunk());
        }
    }
    @EventHandler
    public void unloading(ChunkUnloadEvent e) {
        if (e.getChunk().getX() == 78 && e.getChunk().getZ() == 78) {
            e.setSaveChunk(false);
            getLogger().info("QA24 CHUNK NO SAVE");
        }
    }
    @EventHandler
    public void init(WorldInitEvent e) {
        getLogger().info("QA24 WORLD INIT " + e.getWorld().getName());
    }
    @EventHandler
    public void startup(ServerLoadEvent e) {
        getLogger().info("QA24 SERVER LOAD " + e.getType());
    }
    @EventHandler
    public void save(WorldSaveEvent e) {
        getLogger().info("QA24 WORLD SAVE " + e.getWorld().getName());
    }
    @EventHandler
    public void load(WorldLoadEvent e) {
        getLogger().info("QA24 WORLD LOAD " + e.getWorld().getName());
    }
    @EventHandler
    public void enable(PluginEnableEvent e) {
        getLogger().info("QA24 ENABLE " + e.getPlugin().getName());
    }
    @EventHandler
    public void disable(PluginDisableEvent e) {
        getLogger().info("QA24 DISABLE " + e.getPlugin().getName());
    }
    @EventHandler
    public void join(PlayerJoinEvent e) {
        e.joinMessage(net.kyori.adventure.text.Component.text("QA24 JOIN " + e.getPlayer().getName()));
    }
    @EventHandler
    public void quit(PlayerQuitEvent e) {
        e.quitMessage(net.kyori.adventure.text.Component.text("QA24 QUIT " + e.getPlayer().getName()));
    }
    @EventHandler
    public void held(PlayerItemHeldEvent e) {
        if (e.getNewSlot() == 6) {
            e.setCancelled(true);
            getLogger().info("QA24 HELD DENY");
        }
    }
    @EventHandler(priority = EventPriority.LOW)
    public void protect(BlockBreakEvent e) {
        if (e.getBlock().getX() == 44 && e.getBlock().getY() == 201) {
            e.setCancelled(true);
            getLogger().info("QA24 BREAK DENY " + e.getPlayer().getName());
        }
        if (e.getBlock().getX() == 45) {
            e.setDropItems(false);
            e.setExpToDrop(0);
            getLogger().info("QA24 BREAK ALLOW");
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void uncancelled(BlockBreakEvent e) {
        if (e.getBlock().getX() == 44)
            throw new AssertionError("Cancelled break leaked");
    }
    @EventHandler
    public void place(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getX() == 48 && e instanceof BlockMultiPlaceEvent multi) {
            e.setCancelled(true);
            getLogger().info("QA24 MULTIPLACE DENY " + multi.getReplacedBlockStates().size());
        }
        if (e.getBlockPlaced().getX() == 46) {
            getLogger().info("QA24 PLACE DENY " + e.getBlockReplacedState().getType() + " " +
                             e.getItemInHand().getType());
            e.setCancelled(true);
        }
    }
    @EventHandler
    public void entity(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof org.bukkit.entity.ArmorStand) {
            e.setCancelled(true);
            getLogger().info("QA24 ENTITY DENY " + e.getEventName());
        }
    }
    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && e.getClickedBlock().getX() == 47) {
            e.setCancelled(true);
            getLogger().info("QA24 INTERACT DENY " + e.getAction());
        }
    }
}
