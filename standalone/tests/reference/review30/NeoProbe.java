package qa.unity30;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.*;

@Mod("unity30witness")
public final class NeoProbe {
    public NeoProbe() { NeoForge.EVENT_BUS.register(this); }

    private static void mark(String key, Object value) {
        System.out.println("UNITY30 " + key + " " + value);
    }
    private static boolean area(BlockPos pos) {
        return pos.getY() == 80 && pos.getZ() == 20 && pos.getX() >= 46 && pos.getX() <= 52;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void breakHigh(BreakBlockEvent event) {
        if (!area(event.getPos())) return;
        mark("break-high-" + event.getPos().getX(), event.isCanceled());
        if (event.getPos().getX() == 46) event.setCanceled(true);
    }
    @SubscribeEvent
    public void breakNormal(BreakBlockEvent event) {
        if (area(event.getPos())) mark("break-normal-" + event.getPos().getX(), event.isCanceled());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
    public void breakLow(BreakBlockEvent event) {
        if (area(event.getPos())) mark("break-low-" + event.getPos().getX(), event.isCanceled());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void place(EntityPlaceEvent event) {
        if (!area(event.getPos())) return;
        mark("place-old-" + event.getPos().getX(), event.getBlockSnapshot().getState().isAir());
        if (event.getPos().getX() == 49) event.setCanceled(true);
    }
    @SubscribeEvent(receiveCanceled=true,priority=EventPriority.LOWEST)
    public void placeLow(EntityPlaceEvent event) {
        if (area(event.getPos())) mark("place-low-" + event.getPos().getX(), event.isCanceled());
    }
    @SubscribeEvent
    public void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getPos().equals(new BlockPos(52,80,20)) && !event.getLevel().isClientSide()) {
            event.setCanceled(true);
            mark("interact-deny", true);
        }
    }
    @SubscribeEvent
    public void attack(AttackEntityEvent event) {
        if (event.getTarget().entityTags().contains("u30attack")) {
            event.setCanceled(true); mark("attack-deny", true);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().entityTags().contains("u30health"))
            mark("incoming-" + event.getSource().getMsgId(), event.isCanceled());
    }
    @SubscribeEvent
    public void pre(LivingDamageEvent.Pre event) {
        if (event.getEntity().entityTags().contains("u30health")) {
            mark("pre", event.getNewDamage()); event.setNewDamage(2);
        }
    }
    @SubscribeEvent
    public void post(LivingDamageEvent.Post event) {
        if (event.getEntity().entityTags().contains("u30health")) mark("post",event.getHealthDamage());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void death(LivingDeathEvent event) {
        if (event.getEntity().entityTags().contains("u30save")) {
            event.getEntity().setHealth(4); event.setCanceled(true); mark("death-deny",true);
        }
    }
    @SubscribeEvent
    public void drops(LivingDropsEvent event) {
        var mob=event.getEntity();
        if (mob.entityTags().contains("u30nodrops")) {
            for (var drop:event.getDrops()) drop.addTag("u30captured");
            mark("drops-cancel",event.getDrops().size()); event.setCanceled(true);
        }
        if (mob.entityTags().contains("u30loot")) {
            mark("drops-replace",event.getDrops().size());
            event.getDrops().clear();
            event.getDrops().add(new ItemEntity(mob.level(),mob.getX(),mob.getY(),mob.getZ(),Items.EMERALD.getDefaultInstance().copyWithCount(5)));
        }
    }
}
