package net.neoforged.neoforge.event.level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
public abstract class BlockEvent extends Event {
 protected final Level level; protected final BlockPos pos; protected final BlockState state; protected final Entity entity;
 protected BlockEvent(Level level,BlockPos pos,BlockState state,Entity entity){this.level=level;this.pos=pos;this.state=state;this.entity=entity;}
 public Level getLevel(){return level;} public BlockPos getPos(){return pos;} public BlockState getState(){return state;} public Entity getEntity(){return entity;}
 public static class BreakEvent extends BlockEvent implements ICancellableEvent { public BreakEvent(Level l,BlockPos p,BlockState s,Player e){super(l,p,s,e);} public Player getPlayer(){return (Player)entity;} }
 public static class EntityPlaceEvent extends BlockEvent implements ICancellableEvent { private final BlockState placed,against; public EntityPlaceEvent(Level l,BlockPos p,BlockState s,BlockState a,Entity e){super(l,p,s,e);placed=s;against=a;} public BlockState getPlacedBlock(){return placed;} public BlockState getPlacedAgainst(){return against;} }
 public static class EntityMultiPlaceEvent extends EntityPlaceEvent { public EntityMultiPlaceEvent(Level l,BlockPos p,BlockState s,BlockState a,Entity e){super(l,p,s,a,e);} }
}
