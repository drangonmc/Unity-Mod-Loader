package mc.unity.runtime;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import mc.unity.api.MachineActors;
import mc.unity.core.Attachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class MachineAccess {
    public enum Operation { MINE, EXTRACT, INSERT }
    public static boolean mine(ServerLevel world,BlockPos pos,BlockEntity machine,BlockState expected) {
        if(machine.isRemoved() || machine.getLevel()!=world || !world.getServer().isSameThread() || !world.isLoaded(pos))return false;
        var owner=MachineActors.owner(machine).orElse(null);
        return valid(world,pos,owner,Operation.MINE) && world.getBlockState(pos)==expected;
    }
    public static void deployed(Entity drone,UseOnContext context){
        if(context.getPlayer() instanceof ServerPlayer placer && drone.level()==placer.level())
            Attachments.set((Attachments.Owner)drone,MachineActors.OWNER,new NameAndId(placer.getGameProfile()));
    }
    public static boolean transfer(Level level,BlockPos pos,Entity drone,UUID recordedOwner,BlockPos destination,boolean withdrawing){
        if(!(level instanceof ServerLevel world) || drone.level()!=level || drone.isRemoved() || !world.getServer().isSameThread())return false;
        NameAndId owner=Attachments.find((Attachments.Owner)drone,MachineActors.OWNER);
        if(owner==null && recordedOwner!=null){
            var online=world.getServer().getPlayerList().getPlayer(recordedOwner);
            owner=online!=null?new NameAndId(online.getGameProfile()):world.getServer().services().nameToIdCache().get(recordedOwner).orElse(new NameAndId(recordedOwner,null));
        }
        if(!container(world,pos,owner,withdrawing?Operation.EXTRACT:Operation.INSERT))return false;
        return !withdrawing || container(world,destination,owner,Operation.INSERT);
    }
    private static boolean container(ServerLevel world,BlockPos pos,NameAndId owner,Operation op){
        if(pos==null || !world.isLoaded(pos) || !valid(world,pos,owner,op))return false;
        var state=world.getBlockState(pos);
        if(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
            var other=pos.relative(ChestBlock.getConnectedDirection(state));
            if(!world.isLoaded(other) || !valid(world,other,owner,op))return false;
        }
        return true;
    }
    private static boolean valid(ServerLevel world,BlockPos pos,NameAndId owner,Operation op){
        var host=ServerHooks.pluginHost();
        return host==null || host.machineAccess(world,pos,owner,op.name());
    }
}
