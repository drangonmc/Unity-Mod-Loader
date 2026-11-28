package qa.unity32;
import java.util.function.Supplier;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import com.mojang.serialization.Codec;
@Mod("unity32sync")
public final class SyncProbe {
    static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,"unity32sync");
    static final Supplier<AttachmentType<Integer>> SCORE=TYPES.register("score",()->AttachmentType.builder(()->0).serialize(Codec.INT.fieldOf("v")).sync(new AttachmentSyncHandler<Integer>(){
        public void write(RegistryFriendlyByteBuf b,Integer v,boolean initial){b.writeBoolean(initial);b.writeInt(v);}
        public Integer read(IAttachmentHolder h,RegistryFriendlyByteBuf b,Integer previous){
            boolean initial=b.readBoolean();int value=b.readInt();System.out.println("U32SYNC "+(initial?"initial":"update")+" "+value+" previous="+previous);return value;
        }
    }).build());
    static final Supplier<AttachmentType<Integer>> SECRET=TYPES.register("owner",()->AttachmentType.builder(()->0)
        .serialize(Codec.INT.fieldOf("v")).sync((h,p)->h==(Object)p,ByteBufCodecs.VAR_INT).build());
    public SyncProbe(IEventBus bus){TYPES.register(bus);NeoForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void commands(RegisterCommandsEvent e){
        var cmd=Commands.literal("u32data");for(String mode:new String[]{"write","remove","check"})cmd.then(Commands.literal(mode).executes(ctx->{
            var p=ctx.getSource().getServer().getPlayerList().getPlayerByName("VolumeA");if(p==null)return 0;
            var owner=(IAttachmentHolder)(Object)p;var chunk=(IAttachmentHolder)(Object)p.level().getChunkAt(new BlockPos(65,80,23));
            if(mode.equals("write")){owner.setData(SCORE,3201);owner.setData(SECRET,3211);chunk.setData(SCORE,3202);chunk.syncData(SCORE);}
            if(mode.equals("remove"))owner.removeData(SECRET);
            System.out.println("U32SYNC server "+mode+" "+owner.getExistingDataOrNull(SCORE)+" "+chunk.getExistingDataOrNull(SCORE)+" "+owner.getExistingDataOrNull(SECRET));
            return 1;
        }));e.getDispatcher().register(cmd);e.getDispatcher().register(Commands.literal("u32mine").executes(c->mine(c.getSource())));
    }
    @net.neoforged.bus.api.SubscribeEvent public void absorption(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){
        if(e.getEntity().entityTags().contains("u32abs"))System.out.println("U32ABS "+e.getInflictedDamage()+" "+e.getHealthDamage()+" "+e.getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION));
    }
    @net.neoforged.bus.api.SubscribeEvent public void parry(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent e){
        if(e.getEntity().entityTags().contains("u32parry")){
            if(e.getOriginalBlock() || e.getOriginalBlockedDamage()!=0)throw new AssertionError("Original unarmed shield state");
            e.setBlocked(true);e.setBlockedDamage(3);System.out.println("U32PARRY unarmed 3");
        }
    }
    public static int mine(net.minecraft.commands.CommandSourceStack source){
        var level=source.getLevel();var machine=level.getBlockEntity(new BlockPos(69,80,24));if(machine==null)throw new AssertionError("Missing machine");
        var owner=mc.unity.api.MachineActors.forMachine(machine).orElseThrow();
        var other=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,source.getServer().getPlayerList().getPlayerByName("VolumeB").getGameProfile());
        if(!owner.getGameProfile().name().equals("VolumeA") || level.players().contains(owner))throw new AssertionError("Offline actor identity");
        var pos=new BlockPos(72,80,29);
        level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        for(var actor:new net.minecraft.server.level.ServerPlayer[]{other,owner}){
            actor.snapTo(72.5,80,27.5,0,0);
            actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
            boolean mined=actor.gameMode.destroyBlock(pos);
            if(mined!=(actor==owner) || level.getBlockState(pos).isAir()!=(actor==owner))throw new AssertionError("Incorrect region check for "+actor.getGameProfile().name());
        }
        try {
            var fabric=Class.forName("net.fabricmc.fabric.api.entity.FakePlayer").getMethod("get",net.minecraft.server.level.ServerLevel.class,com.mojang.authlib.GameProfile.class);
            level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for(String name:new String[]{"VolumeB","VolumeA"}){
                var actor=(net.minecraft.server.level.ServerPlayer)fabric.invoke(null,level,source.getServer().getPlayerList().getPlayerByName(name).getGameProfile());
                if(!mc.unity.api.MachineActors.isMachine(actor))throw new AssertionError("Fabric machine player");
                actor.snapTo(72.5,80,27.5,0,0);actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
                if(actor.gameMode.destroyBlock(pos)!=name.equals("VolumeA") || level.getBlockState(pos).isAir()!=name.equals("VolumeA"))throw new AssertionError("Fabric region check "+name);
            }
        }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        System.out.println("U32MACHINE Fabric and NeoForge share WorldGuard ownership checks");
        System.out.println("U32MACHINE owner VolumeA allowed; VolumeB denied");return 1;
    }
    public static String inspect(Player local){
        var entity=local.level().players().stream().filter(p->p.getGameProfile().name().equals("VolumeA")).findFirst().orElse(null);
        var chunk=local.level().getChunkSource().getChunkNow(4,1);
        if(entity==null || chunk==null)return "untracked";
        var holder=(IAttachmentHolder)(Object)entity;
        return holder.getExistingDataOrNull(SCORE)+" "+((IAttachmentHolder)(Object)chunk).getExistingDataOrNull(SCORE)+" "+holder.getExistingDataOrNull(SECRET);
    }
}
