package qa.unity29;
import com.mojang.brigadier.arguments.StringArgumentType;
import mc.unity.api.UnityMod;
import net.minecraft.commands.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import com.dannyandson.tinypipes.blocks.*;
import com.guilei12.dronemod.block.entity.ChargeStationBlockEntity;

/** Configures the authors' endpoints and pipe faces. Subsequent transfers run on ordinary game ticks. */
public final class TransferSetup implements UnityMod {
    public void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> commands) {
        commands.register(Commands.literal("unity29setup").requires(s->s.getEntity()==null).then(Commands.argument("kind",StringArgumentType.word()).executes(ctx->{
            String kind=StringArgumentType.getString(ctx,"kind");
            var level=ctx.getSource().getLevel();
            int z=switch(kind){case "fluid"->8;case "item"->10;case "energy"->12;default->throw new IllegalArgumentException(kind);};
            BlockPos source=new BlockPos(24,80,z), center=source.east(), dest=center.east();
            if(kind.equals("fluid")) {
                var tank=Capabilities.Fluid.BLOCK.getCapability(level,source,null,null,Direction.EAST);
                var water=FluidResource.of(Fluids.WATER);
                try(var tx=Transaction.openRoot()){
                    if(tank.insert(water,7250,tx)!=7250)throw new AssertionError("initial fluid fill");
                }
                if(tank.getAmountAsLong(0)!=0)throw new AssertionError("aborted transfer changed source");
                try(var tx=Transaction.openRoot()){
                    if(tank.insert(water,7250,tx)!=7250)throw new AssertionError();tx.commit();
                }
            }else if(kind.equals("energy")) {
                var block=(ChargeStationBlockEntity)level.getBlockEntity(source);
                block.getEnergyHandler().set(12345);block.setChanged();
                var receiver=(ChargeStationBlockEntity)level.getBlockEntity(dest);
                var witness=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER,7);
                var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("machine_serial",29012);
                witness.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
                receiver.getInventory().setItem(0,witness);receiver.setChanged();
            }
            var be=(PipeBlockEntity)level.getBlockEntity(center);
            var key=Identifier.fromNamespaceAndPath("tinypipes","full_"+kind+"_pipe");
            var pipe=be.addPipe(BuiltInRegistries.ITEM.getOptional(key).orElseThrow().getDefaultInstance());
            if(pipe==null)throw new IllegalStateException("No pipe for "+key);
            pipe.setConnectionState(be,Direction.EAST,PipeConnectionState.ENABLED);
            pipe.setConnectionState(be,Direction.WEST,PipeConnectionState.PULLING);
            be.setChanged();
            System.out.println("UNITY29_PIPE_READY "+kind);
            return 1;
        })));
    }
}
