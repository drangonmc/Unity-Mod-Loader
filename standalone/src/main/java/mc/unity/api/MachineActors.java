package mc.unity.api;

import com.mojang.authlib.GameProfile;
import mc.unity.core.Attachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.*;
import java.util.Optional;

/** Persist the placing player's identity on a machine. Its actions use the ordinary player gameplay hooks. */
public final class MachineActors {
    public static final Attachments.Key<NameAndId> OWNER=new Attachments.Key<>(){
        public Identifier id(){return Identifier.parse("unity:machine_owner");}
        public com.mojang.serialization.Codec<NameAndId> codec(){return NameAndId.CODEC;}
    };
    public static boolean isMachine(Player p){
        if(p instanceof FakePlayer)return true;
        for(Class<?> c=p.getClass();c!=null && c!=ServerPlayer.class;c=c.getSuperclass())
            if(c.getName().equals("net.fabricmc.fabric.api.entity.FakePlayer"))return true;
        return false;
    }
    public static void placed(BlockEntity machine,ServerPlayer placer){
        if(machine!=null && machine.getLevel()==placer.level())Attachments.set((Attachments.Owner)machine,OWNER,new NameAndId(placer.getGameProfile()));
    }
    public static Optional<NameAndId> owner(BlockEntity machine){return Optional.ofNullable(Attachments.find((Attachments.Owner)machine,OWNER));}
    public static Optional<ServerPlayer> forMachine(BlockEntity machine){
        if(!(machine.getLevel() instanceof ServerLevel level) || machine.isRemoved() || !level.getServer().isSameThread())return Optional.empty();
        return owner(machine).map(profile->FakePlayerFactory.get(level,new GameProfile(profile.id(),profile.name())));
    }
    public static net.minecraft.server.PlayerAdvancements advancements(net.minecraft.server.players.PlayerList list,ServerPlayer player){
        var server=player.level().getServer();
        return new FakePlayer.FakePlayerAdvancements(server.getFixerUpper(),list,server.getAdvancements(),
            server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve("_fake.json"),player);
    }
}
