package mc.unity.plugins;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.LevelResource;
import org.bukkit.OfflinePlayer;

final class OfflineViews {
    static OfflinePlayer[] all(Host host){
        var players=new LinkedHashMap<UUID,OfflinePlayer>();
        for(var online:host.game.getPlayerList().getPlayers())players.put(online.getUUID(),host.views.player(online));
        Path path=host.game.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if(Files.isDirectory(path))try(var files=Files.newDirectoryStream(path,"*.dat")){
            for(var file:files){
                String name=file.getFileName().toString();UUID id;
                try{id=UUID.fromString(name.substring(0,name.length()-4));}catch(IllegalArgumentException other){continue;}
                players.computeIfAbsent(id,k->find(host,k));
            }
        }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
        return players.values().toArray(OfflinePlayer[]::new);
    }
    static OfflinePlayer find(Host host,Object lookup) {
        var players=host.game.getPlayerList();
        var online=lookup instanceof UUID id?players.getPlayer(id):players.getPlayerByName((String)lookup);
        if(online!=null)return host.views.player(online);
        var profiles=host.game.services().nameToIdCache();
        NameAndId entry;
        if(lookup instanceof UUID id)entry=profiles.get(id).orElse(new NameAndId(id,null));
        else {
            String name=(String)lookup;
            entry=profiles.get(name).orElseGet(()->new NameAndId(UUIDUtil.createOfflinePlayerUUID(name),name));
        }
        var uuid=entry.id();var save=host.game.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid+".dat");
        return Views.of(OfflinePlayer.class,"offline "+uuid,uuid,(m,a)->switch(m){
            case "getUniqueId"->uuid;
            case "getName"->entry.name();
            case "isOnline","isConnected"->players.getPlayer(uuid)!=null;
            case "getPlayer"->{var current=players.getPlayer(uuid);yield current==null?null:host.views.player(current);}
            case "hasPlayedBefore"->Files.isRegularFile(save);
            case "getLastPlayed","getLastSeen"->Files.exists(save)?Files.getLastModifiedTime(save).toMillis():0L;
            case "getFirstPlayed"->Files.exists(save)?Files.readAttributes(save,java.nio.file.attribute.BasicFileAttributes.class).creationTime().toMillis():0L;
            case "isOp"->players.isOp(entry);case "isWhitelisted"->players.getWhiteList().isWhiteListed(entry);
            case "serialize"->Map.of("UUID",uuid.toString());
            default->throw Views.unsupported("OfflinePlayer",m);
        });
    }
}
