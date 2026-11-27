package net.neoforged.neoforge.event.entity.player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
public class AttackEntityEvent extends PlayerInteractEvent implements ICancellableEvent {
 private final Entity target;
 public AttackEntityEvent(Player p,Entity t){super(p,net.minecraft.world.InteractionHand.MAIN_HAND);target=t;}
 public Entity getTarget(){return target;}
}
