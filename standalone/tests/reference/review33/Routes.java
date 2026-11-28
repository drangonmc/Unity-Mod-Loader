package qa.unity33;

import com.guilei12.dronemod.entity.DroneEntity;
import com.guilei12.dronemod.item.RemoteData;
import com.guilei12.dronemod.registry.ModDataComponents;
import com.guilei12.dronemod.registry.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Fixture uses the author's controller and power switches. The scheduled DroneWorkGoal performs all transfers. */
@Mod("unity33routes")
public final class Routes {
    private static final net.neoforged.neoforge.registries.DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> TYPES =
        net.neoforged.neoforge.registries.DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES,"unity33routes");
    private static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> VALUE =
        TYPES.register("value",()->net.neoforged.neoforge.attachment.AttachmentType.builder(()->0)
            .serialize(com.mojang.serialization.Codec.INT.fieldOf("v")).sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    public Routes(net.neoforged.bus.api.IEventBus bus){TYPES.register(bus);NeoForge.EVENT_BUS.register(this);}
    private static net.neoforged.neoforge.attachment.IAttachmentHolder holder(Object object){
        return (net.neoforged.neoforge.attachment.IAttachmentHolder)object;
    }
    public static String inspect(net.minecraft.world.entity.player.Player player){
        var tile=player.level().getBlockEntity(new BlockPos(69,80,36));
        return player.level().dimension().identifier()+" "+holder(player).getExistingDataOrNull(VALUE)+" "+holder(player.level()).getExistingDataOrNull(VALUE)+" "+(tile==null?"null":holder(tile).getExistingDataOrNull(VALUE));
    }
    private static DroneEntity drone(ServerLevel level,String name){
        var id=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
        var matches=level.getEntitiesOfClass(DroneEntity.class,new AABB(48,65,12,83,105,46),d->id.equals(d.getOwnerUUID()));
        if(matches.size()!=1)throw new IllegalStateException("Expected one drone for "+name+", got "+matches.size());
        return matches.getFirst();
    }
    private static BlockPos endpoint(int x){return switch(x){case 65->new BlockPos(65,80,32);case 66->new BlockPos(66,80,39);
        case 67->new BlockPos(67,80,35);case 68->new BlockPos(68,80,40);case 69->new BlockPos(69,80,36);default->throw new IllegalArgumentException("endpoint");};}
    @SubscribeEvent public void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("u33sync").executes(c->{
            var server=c.getSource().getServer();var a=server.getPlayerList().getPlayerByName("VolumeA");
            var level=server.overworld();
            holder(a).setData(VALUE,3302);holder(level).setData(VALUE,3303);
            holder(server.getLevel(net.minecraft.world.level.Level.NETHER)).setData(VALUE,3304);
            holder(level.getBlockEntity(new BlockPos(69,80,36))).setData(VALUE,3305);
            System.out.println("U33 SYNC WRITTEN");return 1;
        }));
        for(boolean charge:new boolean[]{false,true}){
            e.getDispatcher().register(Commands.literal(charge?"u33power":"u33quarry").executes(c->{
                var block=(net.mego.quietquarry.block.QuarryCoreBlockEntity)c.getSource().getLevel().getBlockEntity(new BlockPos(64,81,24));
                var energy=block.getEnergyHandler();
                if(charge)try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){
                    int remaining=800;
                    while(remaining>0){
                        int inserted=energy.insert(remaining,tx);if(inserted<=0)throw new IllegalStateException("Cannot charge quarry");remaining-=inserted;
                    }
                    tx.commit();
                }
                System.out.println("U33 QUARRY FE "+energy.getAmountAsLong());return 1;
            }));
        }
        var root=Commands.literal("u33drone");var owner=Commands.argument("owner",StringArgumentType.word());
        owner.then(Commands.literal("off").executes(c->{var d=drone(c.getSource().getLevel(),StringArgumentType.getString(c,"owner"));d.setPowered(false);System.out.println("U33 DRONE OFF");return 1;}));
        owner.then(Commands.literal("route").then(Commands.argument("input",IntegerArgumentType.integer(65,69)).then(Commands.argument("output",IntegerArgumentType.integer(65,69)).executes(c->{
            var d=drone(c.getSource().getLevel(),StringArgumentType.getString(c,"owner"));d.setPowered(false);
            var src=endpoint(IntegerArgumentType.getInteger(c,"input"));var dest=endpoint(IntegerArgumentType.getInteger(c,"output"));
            var control=new ItemStack(ModItems.REMOTE_CONTROL.get());
            control.set(ModDataComponents.REMOTE_DATA.get(),RemoteData.DEFAULT.withInput(src).withOutput(dest));
            d.getRemoteSlot().setItem(0,control);d.readRemoteControl();d.setPowered(true);
            System.out.println("U33 ROUTE "+d.getOwnerUUID()+" "+src.toShortString()+" -> "+dest.toShortString());return 1;
        }))));
        owner.then(Commands.literal("read").executes(c->{
            var d=drone(c.getSource().getLevel(),StringArgumentType.getString(c,"owner"));var inv=d.getInventory();int count=0;
            for(int i=0;i<inv.getContainerSize();i++){var stack=inv.getItem(i);if(!stack.isEmpty())count+=stack.getCount();}
            System.out.println("U33 CARGO "+d.getOwnerUUID()+" "+count+" "+d.getState()+" "+d.isPowered());return 1;
        }));
        e.getDispatcher().register(root.then(owner));
    }
}
