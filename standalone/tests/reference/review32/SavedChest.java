import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
class SavedChest {
    public static void main(String[] args)throws Exception{
        var world=Path.of(args[0]);var root=SavedWorld.chunk(world,65,23);var result=new JsonObject();
        var blocks=root.getListOrEmpty("block_entities");
        var contents=new JsonObject();boolean chest=false;
        for(var tag:blocks){var tile=(CompoundTag)tag;
            if(tile.getIntOr("x",-1)!=65 || tile.getIntOr("y",-1)!=80 || tile.getIntOr("z",-1)!=23)continue;
            if(!tile.getStringOr("id","").equals("minecraft:chest"))throw new AssertionError(tile);
            chest=true;
            for(var v:tile.getListOrEmpty("Items")){var item=(CompoundTag)v;contents.addProperty(""+item.getByteOr("Slot",(byte)-1),item.getStringOr("id","")+"*"+item.getIntOr("count",0));}
        }
        if(!chest)throw new AssertionError("Missing persisted chest");result.add("chest",contents);
        result.addProperty("state",SavedWorld.block(world,65,80,23));
        var path=world.resolve("players/data/"+java.util.UUID.nameUUIDFromBytes("OfflinePlayer:VolumeA".getBytes(java.nio.charset.StandardCharsets.UTF_8))+".dat");
        var player=NbtIo.readCompressed(path,NbtAccounter.defaultQuota());
        result.addProperty("playerAttachment",player.getCompoundOrEmpty("unity:attachments").getCompoundOrEmpty("unity32sync:score").getIntOr("v",-1));
        result.addProperty("chunkAttachment",root.getCompoundOrEmpty("unity:attachments").getCompoundOrEmpty("unity32sync:score").getIntOr("v",-1));
        Files.writeString(Path.of(args[1]),result.toString());
    }
}
