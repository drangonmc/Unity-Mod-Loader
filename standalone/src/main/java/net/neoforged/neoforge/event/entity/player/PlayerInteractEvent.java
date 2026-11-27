package net.neoforged.neoforge.event.entity.player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
public abstract class PlayerInteractEvent extends Event implements ICancellableEvent {
 protected final Player player; protected final InteractionHand hand; protected InteractionResult result=InteractionResult.FAIL;
 protected PlayerInteractEvent(Player p,InteractionHand h){player=p;hand=h;}
 public net.minecraft.world.level.Level getLevel(){return player.level();} public Player getEntity(){return player;} public Player getPlayer(){return player;} public InteractionHand getHand(){return hand;}
 public InteractionResult getCancellationResult(){return result;} public void setCancellationResult(InteractionResult r){result=r;}
 public static class RightClickBlock extends PlayerInteractEvent { private final BlockPos pos; private final BlockHitResult hit; public RightClickBlock(Player p,InteractionHand h,BlockPos x,BlockHitResult v){super(p,h);pos=x;hit=v;} public BlockPos getPos(){return pos;} public net.minecraft.core.Direction getFace(){return hit.getDirection();} public BlockHitResult getHitVec(){return hit;} public net.minecraft.util.TriState getUseBlock(){return net.minecraft.util.TriState.DEFAULT;} public net.minecraft.util.TriState getUseItem(){return net.minecraft.util.TriState.DEFAULT;} }
 public static class LeftClickBlock extends PlayerInteractEvent { private final BlockPos pos; public LeftClickBlock(Player p,BlockPos x,net.minecraft.core.Direction d){super(p,InteractionHand.MAIN_HAND);pos=x;} public BlockPos getPos(){return pos;} public net.minecraft.core.Direction getFace(){return net.minecraft.core.Direction.UP;} public net.minecraft.util.TriState getUseBlock(){return net.minecraft.util.TriState.DEFAULT;} public net.minecraft.util.TriState getUseItem(){return net.minecraft.util.TriState.DEFAULT;} }
 public static class RightClickItem extends PlayerInteractEvent { public RightClickItem(Player p,InteractionHand h){super(p,h);} }
 public static class RightClickEmpty extends PlayerInteractEvent { public RightClickEmpty(Player p,InteractionHand h){super(p,h);} }
 public static class LeftClickEmpty extends PlayerInteractEvent { public LeftClickEmpty(Player p){super(p,InteractionHand.MAIN_HAND);} }
 public static class EntityInteractSpecific extends PlayerInteractEvent { private final Entity target; private final Vec3 location; public EntityInteractSpecific(Player p,InteractionHand h,Entity t,Vec3 l){super(p,h);target=t;location=l;} public Entity getTarget(){return target;} public Vec3 getLocation(){return location;} }
}
